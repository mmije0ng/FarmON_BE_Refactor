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

    // 캐시 수동 evict
    private final CacheManager cacheManager;

    /**
     * 캐시 무효화 (트랜잭션 커밋 이후 실행)
     */
    private void evictHomeCommunityCacheAfterCommit(PostType postType) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictHomeCommunityCachesNow(postType);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictHomeCommunityCachesNow(postType);
            }
        });
    }

    /**
     * 캐시 무효화 (즉시 실행)
     * - home:community:popular (POPULAR 전용 캐시) 추가 무효화
     * - home:community (기존 캐시)도 함께 무효화
     */
    private void evictHomeCommunityCachesNow(PostType postType) {
        // 1) 기존 캐시(home:community) 무효화
        Cache communityCache = cacheManager.getCache("home:community");
        if (communityCache != null) {
            // POPULAR은 항상 무효화
            communityCache.evict("category:POPULAR");

            // 특정 타입 캐시만 무효화 (ALL/POPULAR 제외)
            if (postType != null && postType != PostType.ALL && postType != PostType.POPULAR) {
                communityCache.evict("category:" + postType.name());
            }
        }

        // 2) POPULAR 전용 캐시(home:community:popular)도 같이 무효화 (TTL 길게 가져갈 경우 필수)
        Cache popularCache = cacheManager.getCache("home:community:popular");
        if (popularCache != null) {
            popularCache.evict("category:POPULAR");
        }

        // 3) (선택) 인기 전문가 칼럼 별도 캐시를 쓰는 경우 함께 무효화
        // PostType.EXPERT_COLUMN의 인기 칼럼 리스트가 likeCount 기반 정렬에 영향받는 구조라면 추천
        if (postType == PostType.EXPERT_COLUMN) {
            Cache popularExpertCache = cacheManager.getCache("home:popularExpertColumn");
            if (popularExpertCache != null) {
                popularExpertCache.evict("list:v1");
            }
        }
    }

    /**
     * 권한 검증: checked exception 사용하지 않음 (GeneralException으로 통일)
     */
    private void validateRole() {
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
    public void postLikeUp(Long userId, Long postId) {
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

        // 중복 좋아요 방지 (원본 id 기준으로 한 번만 체크하는 게 더 안전/효율적)
        // 기존 로직 유지가 필요하면 relatedPosts 전체를 검사해도 되지만, like는 originalPost에 저장하므로 originalPost만 확인해도 충분함
        if (likeCountRepository.findByUserIdAndPostId(userId, originalPost.getId()) != null) {
            throw new GeneralException(ErrorStatus.LIKE_TYPE_NOT_SAVED); // 필요 시 별도 에러 코드 권장
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

        // 커밋 이후 캐시 무효화 (POPULAR + 해당 타입 + popular 캐시)
        PostType postType = originalPost.getBoard().getPostType();
        evictHomeCommunityCacheAfterCommit(postType);
    }

    // 좋아요 감소
    @Transactional
    public void postLikeDown(Long userId, Long postId) {
        validateRole();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_NOT_FOUND));

        Post originalPost = getOriginalPost(post);

        // 좋아요 찾기 (원본 게시물 기준)
        LikeCount like = likeCountRepository.findByUserIdAndPostId(userId, originalPost.getId());
        if (like == null) {
            throw new GeneralException(ErrorStatus.LIKE_TYPE_NOT_SAVED); // 필요 시 별도 에러 코드 권장
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

        // 커밋 이후 캐시 무효화 (POPULAR + 해당 타입 + popular 캐시)
        PostType postType = originalPost.getBoard().getPostType();
        evictHomeCommunityCacheAfterCommit(postType);
    }

    // 좋아요 개수 조회
    @Transactional(readOnly = true)
    public int getLikeCount(Long postId) {
        return postRepository.getLikeCount(postId);
    }
}
