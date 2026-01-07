package com.backend.farmon.service.LikeService;

import com.backend.farmon.apiPayload.code.status.ErrorStatus;
import com.backend.farmon.apiPayload.exception.GeneralException;
import com.backend.farmon.config.security.UserAuthorizationUtil;
import com.backend.farmon.domain.LikeCount;
import com.backend.farmon.domain.Post;
import com.backend.farmon.domain.User;
import com.backend.farmon.dto.post.PostType;
import com.backend.farmon.repository.LikeCountRepository.LikeCountRepository;
import com.backend.farmon.repository.PostRepository.PostRepository;
import com.backend.farmon.repository.UserRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LikeServiceImpl {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LikeCountRepository likeCountRepository;
    private final UserAuthorizationUtil userAuthorizationUtil;

    // 추가: 캐시 수동 evict를 위한 주입
    private final CacheManager cacheManager;

    // 캐시 무효화 (커밋 이후 실행)
    private void evictHomeCommunityCacheAfterCommit(PostType postType) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictHomeCommunityCacheNow(postType);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictHomeCommunityCacheNow(postType);
            }
        });
    }

    // 캐시 무효화 (즉시 실행)
    private void evictHomeCommunityCacheNow(PostType postType) {
        Cache cache = cacheManager.getCache("home:community");
        if (cache == null) return;

        // POPULAR은 항상 무효화
        cache.evict("category:POPULAR");

        // default 케이스(특정 타입)만 무효화하고 싶다면 ALL/POPULAR은 제외
        if (postType != null && postType != PostType.ALL && postType != PostType.POPULAR) {
            cache.evict("category:" + postType.name());
        }
    }

    private void validateRole() throws IllegalAccessException {
        String currentUserRole = userAuthorizationUtil.getCurrentUserRole();
        if (!"FARMER".equals(currentUserRole) && !"EXPERT".equals(currentUserRole)) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED_ACCESS);
        }
    }

    private Post getOriginalPost(Post post) {
        if (post.getOriginalPostId() == null) return post;

        return postRepository.findById(post.getOriginalPostId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_NOT_FOUND));
    }

    // 좋아요 추가
    @Transactional
    public void postLikeUp(Long userId, Long postId) throws IllegalAccessException {
        validateRole();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_NOT_FOUND));

        Post originalPost = getOriginalPost(post);

        // originalPostId가 같은 모든 게시물 가져오기
        List<Post> relatedPosts = postRepository.findAllByOriginalPostId(originalPost.getId());

        if (user.equals(post.getUser())) {
            throw new GeneralException(ErrorStatus.LIKE_TYPE_NOT_SAVED);
        }

        // 중복 좋아요 방지 (originalPostId가 같은 모든 게시물 체크)
        for (Post relatedPost : relatedPosts) {
            if (likeCountRepository.findByUserIdAndPostId(userId, relatedPost.getId()) != null) {
                throw new IllegalAccessException("이미 좋아요를 눌렀습니다!");
            }
        }

        // 원본 게시물 기준으로 좋아요 저장
        LikeCount likeCount = LikeCount.builder()
                .user(user)
                .post(originalPost)
                .build();
        likeCountRepository.save(likeCount);

        // originalPostId가 같은 모든 게시물의 좋아요 증가
        for (Post relatedPost : relatedPosts) {
            relatedPost.increaseLikes();
        }

        postRepository.saveAll(relatedPosts);
        postRepository.flush();

        // 커밋 이후 캐시 무효화 (POPULAR + 해당 타입)
        PostType postType = originalPost.getBoard().getPostType();
        evictHomeCommunityCacheAfterCommit(postType);
    }

    // 좋아요 감소
    @Transactional
    public void postLikeDown(Long userId, Long postId) throws IllegalAccessException {
        validateRole();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_NOT_FOUND));

        Post originalPost = getOriginalPost(post);

        // 좋아요 찾기 (원본 게시물 기준)
        LikeCount like = likeCountRepository.findByUserIdAndPostId(userId, originalPost.getId());
        if (like == null) {
            throw new IllegalAccessException("좋아요를 누른 적이 없습니다!");
        }

        // 좋아요 삭제
        likeCountRepository.delete(like);

        // originalPostId가 같은 모든 게시물 가져오기
        List<Post> relatedPosts = postRepository.findAllByOriginalPostId(originalPost.getId());

        // originalPostId가 같은 모든 게시물의 좋아요 감소
        for (Post relatedPost : relatedPosts) {
            relatedPost.decreaseLikes();
        }

        postRepository.saveAll(relatedPosts);
        postRepository.flush();

        // 커밋 이후 캐시 무효화 (POPULAR + 해당 타입)
        PostType postType = originalPost.getBoard().getPostType();
        evictHomeCommunityCacheAfterCommit(postType);
    }

    // 좋아요 개수 조회
    @Transactional(readOnly = true)
    public int getLikeCount(Long postId) {
        return postRepository.getLikeCount(postId);
    }
}
