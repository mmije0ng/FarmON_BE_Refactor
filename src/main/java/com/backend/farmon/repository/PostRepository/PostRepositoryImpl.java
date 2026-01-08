package com.backend.farmon.repository.PostRepository;

import com.backend.farmon.apiPayload.code.status.ErrorStatus;
import com.backend.farmon.apiPayload.exception.GeneralException;
import com.backend.farmon.domain.*;
import com.backend.farmon.dto.home.HomePostRow;
import com.backend.farmon.dto.home.PopularExpertPostRow;
import com.backend.farmon.dto.post.PostType;
import com.backend.farmon.repository.BoardRepository.BoardRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {
    private final BoardRepository boardRepository;
    private final JPAQueryFactory queryFactory;
    private final QLikeCount likeCount = QLikeCount.likeCount;
    private final QPost post = QPost.post;
    private final QBoard board = QBoard.board;
    private final QPost originalPost = new QPost("originalPost");
    private final QComment comment = QComment.comment;
    private final QCrop crop = QCrop.crop;
    private final QUser user = QUser.user;
    private final QExpert expert = QExpert.expert;
    private final QPostImg postImg = QPostImg.postImg;

    /**
     * 공통 select (원본 게시글 기준) + 좋아요/댓글 count를 한 번에 가져오기 위한 프로젝션
     */
    private com.querydsl.core.types.ConstructorExpression<HomePostRow> homePostRowProjection(QPost targetPost) {
        return Projections.constructor(
                HomePostRow.class,
                targetPost.id,
                targetPost.postTitle,
                targetPost.postContent,
                targetPost.board.postType.stringValue(), // enum name
                targetPost.createdAt,
                likeCount.id.countDistinct(),
                comment.id.countDistinct()
        );
    }

    // ALL: 전체 게시글 N개
    @Override
    public List<HomePostRow> findTopPostsWithCounts(int limit) {
        return queryFactory
                .select(homePostRowProjection(originalPost))
                .from(originalPost)
                .join(originalPost.board, board)
                .leftJoin(likeCount).on(likeCount.post.id.eq(originalPost.id))
                .leftJoin(comment).on(comment.post.id.eq(originalPost.id))
                .where(originalPost.id.in(
                        JPAExpressions.select(post.originalPostId)
                                .from(post)
                                .where(
                                        post.board.postType.eq(PostType.ALL),
                                        post.originalPostId.isNotNull()
                                )
                ))
                .groupBy(
                        originalPost.id,
                        originalPost.postTitle,
                        originalPost.postContent,
                        originalPost.createdAt,
                        originalPost.board.postType
                )
                .orderBy(originalPost.createdAt.desc()) // 기존과 동일: 최신순
                .limit(limit)
                .fetch();
    }

    // POPULAR: 인기 게시글 N개
    @Override
    public List<HomePostRow> findTopPostsByLikesWithCounts(int limit) {
        QPost currentPost = new QPost("currentPost");

        return queryFactory
                .select(homePostRowProjection(originalPost))
                .from(originalPost)
                .join(originalPost.board, board)
                .leftJoin(likeCount).on(likeCount.post.id.eq(originalPost.id))
                .leftJoin(comment).on(comment.post.id.eq(originalPost.id))
                .where(originalPost.id.in(
                        JPAExpressions.select(currentPost.originalPostId)
                                .from(currentPost)
                                .where(
                                        currentPost.board.postType.eq(PostType.POPULAR),
                                        currentPost.originalPostId.isNotNull()
                                )
                ))
                .groupBy(
                        originalPost.id,
                        originalPost.postTitle,
                        originalPost.postContent,
                        originalPost.createdAt,
                        originalPost.board.postType
                )
                .orderBy(
                        likeCount.id.countDistinct().desc(), // 기존과 동일: 좋아요 수 desc
                        originalPost.createdAt.desc()
                )
                .limit(limit)
                .fetch();
    }

    // 특정 타입(EXPERT_COLUMN, Q&A 등)
    @Override
    public List<HomePostRow> findTopPostsByPostTypeWithCounts(PostType postType, int limit) {
        return queryFactory
                .select(homePostRowProjection(post))
                .from(post)
                .join(post.board, board)
                .leftJoin(likeCount).on(likeCount.post.id.eq(post.id))
                .leftJoin(comment).on(comment.post.id.eq(post.id))
                .where(board.postType.eq(postType))
                .groupBy(
                        post.id,
                        post.postTitle,
                        post.postContent,
                        post.createdAt,
                        post.board.postType
                )
                .orderBy(
                        likeCount.id.countDistinct().desc(), // 요청하신 정렬 그대로
                        post.createdAt.desc()
                )
                .limit(limit)
                .fetch();
    }

    // 인기 전문가 칼럼 6개 조회
    @Override
    public List<PopularExpertPostRow> findTopExpertColumnRowsByPopularIds(List<Long> popularPostsIdList, int limit) {
        QPostImg pi2 = new QPostImg("pi2");

        boolean hasPinned = popularPostsIdList != null && !popularPostsIdList.isEmpty();

        var query = queryFactory
                .select(Projections.constructor(
                        PopularExpertPostRow.class,
                        post.id,
                        post.postTitle,
                        post.postContent,
                        user.userName,
                        expert.profileImageUrl,
                        postImg.storedFileName
                ))
                .from(post)
                .join(post.board, board)
                .join(post.user, user)
                .leftJoin(user.expert, expert)
                .leftJoin(likeCount).on(likeCount.post.id.eq(post.id))
                // 첫 이미지 1개만 LEFT JOIN
                .leftJoin(postImg).on(postImg.id.eq(
                        JPAExpressions.select(pi2.id.min())
                                .from(pi2)
                                .where(pi2.post.id.eq(post.id))
                ))
                .where(board.postType.eq(PostType.EXPERT_COLUMN))
                .groupBy(
                        post.id,
                        post.postTitle,
                        post.postContent,
                        user.userName,
                        expert.profileImageUrl,
                        postImg.storedFileName
                );

        // QueryDSL orderBy에 null 전달 방지 + pinned 조건부 생성
        if (hasPinned) {
            // pinned 우선 정렬(1) / 나머지(2)
            var pinnedFirstOrderExpr = new CaseBuilder()
                    .when(post.id.in(popularPostsIdList)).then(1)
                    .otherwise(2);

            // pinned 내부 순서 유지 (MySQL: FIELD)
            var pinnedInnerOrderExpr = Expressions.numberTemplate(
                    Integer.class,
                    "FIELD({0}, {1})",
                    post.id,
                    Expressions.constant(popularPostsIdList)
            );

            query.orderBy(
                    pinnedFirstOrderExpr.asc(),
                    pinnedInnerOrderExpr.asc(),
                    likeCount.id.countDistinct().desc(),
                    post.createdAt.desc()
            );
        } else {
            query.orderBy(
                    likeCount.id.countDistinct().desc(),
                    post.createdAt.desc()
            );
        }

        return query
                .limit(limit)
                .fetch();
    }

    // 필터링 없이 조회
    @Override
    public Page<Post> findAllByBoardId(Long boardId, Pageable pageable) {
        QPost post = QPost.post;
        QPostImg postImg = QPostImg.postImg;
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_TYPE_NOT_FOUND));

        // 게시판 ID로 게시글 및 관련 이미지 조회
        List<Post> posts = queryFactory
                .selectFrom(post)
                .leftJoin(post.postImgs, postImg).fetchJoin() // Post와 PostImg를 Join
                .where(post.board.id.eq(boardId)) // 게시판 ID로 필터링
                .orderBy(post.createdAt.desc()) // 최신순 정렬
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 게시글 수 조회
        long total = queryFactory
                .select(post.id) // countQuery 최적화 (ID만 선택)
                .from(post)
                .where(post.board.id.eq(boardId))
                .fetchCount();

        return new PageImpl<>(posts, pageable, total);
    }

    // 필터링을 이용한 조회
    @Override
    public Page<Post> findPostsByBoardIdAndCrops(Long boardId, List<String> cropNames, Pageable pageable) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_TYPE_NOT_FOUND));

        BooleanBuilder whereClause = new BooleanBuilder();
        whereClause.and(post.board.id.eq(boardId));

        if (cropNames != null && !cropNames.isEmpty()) {
            // 1️⃣ cropNames를 기준으로 Crop ID 목록 가져오기
            List<Long> cropIds = queryFactory
                    .select(crop.id)
                    .from(crop)
                    .where(crop.name.in(cropNames)) // cropNames 리스트로 검색
                    .fetch();

            log.info("필터링할 cropIds: " + cropIds);

            // 2️⃣ 가져온 Crop ID 목록으로 Post 필터링
            if (!cropIds.isEmpty()) {
                whereClause.and(post.crop.id.in(cropIds));
            } else {
                // 일치하는 Crop이 없으면 결과가 없음
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        }

    // 3️⃣ 필터링된 게시글 조회
    List<Post> posts = queryFactory.selectFrom(post)
            .leftJoin(post.postImgs, postImg).fetchJoin()  // 게시글 이미지
            .leftJoin(post.crop, crop)  // Post와 Crop 관계 조인
            .where(whereClause)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    log.info("필터링된 게시글 개수: " + posts.size());

    // 4️⃣ 전체 게시글 개수 조회
    long totalCount = queryFactory.selectFrom(post)
            .leftJoin(post.crop, crop)
            .where(whereClause)
            .fetchCount();

    return new PageImpl<>(posts, pageable, totalCount);
}




    // 인기게시판 조회(상세조회X)
    @Override
    public Page<Post> findPopularPosts(Long boardId, Pageable pageable) {

        QPost post = QPost.post;
        QPostImg postImg = QPostImg.postImg;
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_TYPE_NOT_FOUND));
        // 게시판별 인기 게시글 및 관련 이미지 조회 (좋아요 수 기준 정렬)
        List<Post> posts = queryFactory
                .selectFrom(post)
                .leftJoin(post.postImgs, postImg).fetchJoin() // Post와 PostImg를 Join
                .where(post.board.id.eq(boardId)) // 게시판 ID로 필터링
                .orderBy(post.postLikes.desc()) // 좋아요 수 기준 내림차순 정렬
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();


        // 전체 게시글 수 조회 (countQuery로 분리하여 성능 최적화)
        long total = queryFactory
                .select(post.id) // countQuery를 최적화하기 위해 ID만 선택
                .from(post)
                .where(post.board.id.eq(boardId))
                .fetchCount();

        return new PageImpl<>(posts, pageable, total);
    }

    // 검색어(제목,소제목)에따라 검색
    @Override
    public Page<Post> findPostsBySearchQuery(String searchQuery, Long boardId, Pageable pageable) {
        QPost post = QPost.post;
        QPostImg postImg = QPostImg.postImg;
        QBoard board = QBoard.board;

        // 게시판 ID로 Board 객체 조회
        Board boardExists = boardRepository.findById(boardId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.POST_TYPE_NOT_FOUND));

        BooleanBuilder whereClause = new BooleanBuilder();
        whereClause.and(post.board.id.eq(boardId));  // 게시판 ID로 필터링 1,4,5,6

        // 검색어가 있을 경우
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            whereClause.and(post.postTitle.containsIgnoreCase(searchQuery)  // 제목 검색
                    .or(post.subTitle.containsIgnoreCase(searchQuery)));  // 부제목 검색
        }

        // 검색된 게시글 조회
        List<Post> posts = queryFactory.selectFrom(post)
                .leftJoin(post.postImgs, postImg).fetchJoin()  // 게시글 이미지와 조인
                .leftJoin(post.board, board).fetchJoin()  // 게시글과 게시판 조인
                .where(whereClause)
                .offset(pageable.getOffset())  // 페이징 처리
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc())  // 최신순 정렬
                .fetch();

        // 전체 게시글 수 조회 (countQuery로 최적화)
        long totalCount = queryFactory.selectFrom(post)
                .leftJoin(post.board, board)
                .where(whereClause)
                .fetchCount();

        // 페이징 처리된 결과 반환
        return new PageImpl<>(posts, pageable, totalCount);
    }


}
