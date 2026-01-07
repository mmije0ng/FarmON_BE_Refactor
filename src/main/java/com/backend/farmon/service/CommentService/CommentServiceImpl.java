package com.backend.farmon.service.CommentService;

import com.backend.farmon.apiPayload.code.status.ErrorStatus;
import com.backend.farmon.apiPayload.exception.GeneralException;
import com.backend.farmon.config.security.UserAuthorizationUtil;
import com.backend.farmon.controller.UserController;
import com.backend.farmon.domain.Comment;
import com.backend.farmon.domain.Post;
import com.backend.farmon.domain.User;
import com.backend.farmon.dto.Comment.CommentRequestDTO;
import com.backend.farmon.dto.Comment.CommentResponseDTO;
import com.backend.farmon.repository.CommentRepository.CommentRepository;
import com.backend.farmon.repository.PostRepository.PostRepository;
import com.backend.farmon.repository.UserRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Slf4j
@RequiredArgsConstructor
@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserAuthorizationUtil userAuthorizationUtil;

    @Transactional
    @Override
    public CommentResponseDTO saveComment(Long postId, Long parentId, CommentRequestDTO.CommentSaveRequestDto dto) {

        String currentUserRole = userAuthorizationUtil.getCurrentUserRole();

        if (!"FARMER".equals(currentUserRole) && !"EXPERT".equals(currentUserRole)) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED_ACCESS);
        }

        // 1. 원본 게시글 조회
        Post originalPost = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 존재하지 않습니다."));

        // 2. 같은 originalPostId를 가진 모든 게시글 가져오기
        List<Post> linkedPosts = getAllLinkedPosts(originalPost);

        // 3. 사용자 정보 조회
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 존재하지 않습니다."));

        // 4. 부모 댓글 확인 및 groupId 설정
        Comment parent = null;
        Long groupId;
        int depth = 0;
        int groupOrder = 0; // 기본값으로 설정
        Long originalCommentId = null;

        if (parentId != null) { // 대댓글인 경우
            parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("부모 댓글이 존재하지 않습니다."));

            groupId = parent.getGroupId(); // 부모 댓글의 groupId 상속
            depth = parent.getDepth() + 1; // 부모 댓글의 depth + 1
            groupOrder = 1; // 대댓글은 항상 groupOrder=1로 설정

            if(depth>2){
                throw new GeneralException(ErrorStatus.COMMENT_NOT_SAVED);
            }

            // 삭제된 부모 댓글에는 대댓글 작성 불가
            if (parent.getIsDeleted()) {
                throw new IllegalArgumentException("삭제된 댓글에는 대댓글을 작성할 수 없습니다.");
            }

            // 부모 댓글에 이미 대댓글이 존재하는지 확인
            boolean hasChildComment = commentRepository.existsByParentId(parent.getId());
            if (hasChildComment) {
                throw new IllegalArgumentException("부모 댓글에는 대댓글을 하나만 작성할 수 있습니다.");
            }

            // 모든 연결된 게시판에 대해 대댓글 저장
            List<Comment> savedComments = new ArrayList<>();
            Comment firstSavedChildComment = null;

            for (Post post : linkedPosts) {
                Comment childComment = Comment.builder()
                        .content(dto.getCommentContent())
                        .authorName(user.getUserName())
                        .user(user)
                        .post(post) // 현재 게시판(Post)에 저장
                        .parent(parent) // 부모 댓글 설정
                        .groupId(groupId) // 부모와 동일한 group ID 설정
                        .depth(depth) // 깊이 설정 (대댓글: 1)
                        .groupOrder(groupOrder) // 대댓글은 항상 groupOrder=1로 설정
                        .originalCommentId(null) // 임시로 null로 설정, 저장 후 업데이트 예정
                        .isDeleted(false) // 삭제되지 않은 상태로 저장
                        .build();

                log.info("대댓글 생성 시작: {}", childComment);
                Comment savedChildComment = commentRepository.save(childComment);

                if (firstSavedChildComment == null) {
                    firstSavedChildComment = savedChildComment; // 첫 번째 저장된 대댓글 참조 저장
                    firstSavedChildComment.setOriginalCommentId(firstSavedChildComment.getId()); // 자신의 ID를 originalCommentID로 설정
                    commentRepository.save(firstSavedChildComment); // 업데이트 반영을 위해 다시 저장
                    log.info("대댓글 originalCommentID 업데이트 완료: {}", firstSavedChildComment.getOriginalCommentId());
                } else {
                    savedChildComment.setOriginalCommentId(firstSavedChildComment.getOriginalCommentId()); // 동일한 original_comment_id 사용
                    commentRepository.save(savedChildComment); // 업데이트 반영을 위해 다시 저장
                    log.info("다른 게시판 대댓글 original_comment_id 업데이트 완료: {}", savedChildComment.getOriginalCommentId());
                }

                savedComments.add(savedChildComment);
            }

            return new CommentResponseDTO(savedComments.isEmpty() ? null : savedComments.get(0)); // 첫 번째 저장된 대댓글 반환
        } else { // 최상위 댓글인 경우
            groupId = commentRepository.findMaxGroupId().orElse(0L) + 1; // 새로운 그룹 ID 생성
            depth = 0; // 최상위 댓글의 깊이는 0
            groupOrder = 0; // 최상위 댓글은 항상 groupOrder=0으로 설정

            // 최상위 댓글은 모든 연결된 게시판에 저장
            List<Comment> savedComments = new ArrayList<>();
            Comment firstSavedParentComment = null;

            for (Post post : linkedPosts) {
                Comment comment = Comment.builder()
                        .content(dto.getCommentContent())
                        .authorName(user.getUserName())
                        .user(user)
                        .post(post)
                        .parent(null) // 최상위 댓글이므로 부모 없음
                        .groupId(groupId) // 새로운 그룹 ID 설정
                        .depth(depth) // 깊이 설정 (최상위 댓글: 0)
                        .groupOrder(groupOrder) // 최상위 댓글은 항상 groupOrder=0으로 설정
                        .originalCommentId(null) // 임시로 null로 설정, 첫 번째 저장 후 업데이트 예정
                        .isDeleted(false) // 삭제되지 않은 상태로 저장
                        .build();

                log.info("최상위 댓글 생성 시작: {}", comment);
                Comment savedParentComment = commentRepository.save(comment);

                if (firstSavedParentComment == null) {
                    firstSavedParentComment = savedParentComment; // 첫 번째 저장된 최상위 댓글 참조 저장
                    firstSavedParentComment.setOriginalCommentId(firstSavedParentComment.getId()); // 자신의 ID를 original_comment_id로 설정
                    commentRepository.save(firstSavedParentComment); // 업데이트 반영을 위해 다시 저장
                    log.info("최상위 댓글 original_comment_id 업데이트 완료: {}", firstSavedParentComment.getOriginalCommentId());
                } else {
                    savedParentComment.setOriginalCommentId(firstSavedParentComment.getOriginalCommentId()); // 동일한 original_comment_id 사용
                    commentRepository.save(savedParentComment); // 업데이트 반영을 위해 다시 저장
                    log.info("다른 게시판 최상위 댓글 original_comment_id 업데이트 완료: {}", savedParentComment.getOriginalCommentId());
                }

                savedComments.add(savedParentComment);
            }

            return new CommentResponseDTO(savedComments.get(0)); // 첫 번째 저장된 최상위 댓글 반환
        }
    }

    // 관련된 모든 게시글 가져오기 (Free, ALL, POPULAR)
    private List<Post> getAllLinkedPosts(Post originalPost) {
        Long baseId = originalPost.getOriginalPostId(); // originalPostId가 현재 게시글에 설정된 값 사용

        return postRepository.findAllByOriginalPostId(baseId); // 같은 originalPostId를 가진 게시글 조회
    }



    @Transactional
    @Override
    public void deleteComment(Long commentId) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.COMMENT_NOT_DELETED));

        Long currentUserId = userAuthorizationUtil.getCurrentUserId();
        if (!comment.getUser().getId().equals(currentUserId)) {
            throw new GeneralException(ErrorStatus.DELETE_ONLY_ACCESS);
        }

        List<Comment> relatedComments = commentRepository.findAllByOriginalCommentId(comment.getOriginalCommentId());

        if (comment.getParent() == null) {
            // 부모 댓글이면 논리 삭제
            for (Comment relatedComment : relatedComments) {
                relatedComment.setIsDeleted(true);
            }
            commentRepository.saveAll(relatedComments);
        } else {
            // 자식 댓글이면 실제 삭제
            commentRepository.deleteAll(relatedComments);
        }
    }





}
