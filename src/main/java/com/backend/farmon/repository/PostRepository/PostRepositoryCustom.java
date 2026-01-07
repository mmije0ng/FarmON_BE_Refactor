package com.backend.farmon.repository.PostRepository;

import com.backend.farmon.domain.Post;
import com.backend.farmon.dto.home.HomePostRow;
import com.backend.farmon.dto.post.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepositoryCustom {
    // ALL: 전체 게시글 N개 (원본 기준 최신순)
    List<HomePostRow> findTopPostsWithCounts(int limit);

    // POPULAR: 인기 게시글 N개 (좋아요 수 desc, 최신순)
    List<HomePostRow> findTopPostsByLikesWithCounts(int limit);

    // 특정 타입(EXPERT_COLUMN, Q&A 등): 좋아요 수 desc, 최신순
    List<HomePostRow> findTopPostsByPostTypeWithCounts(PostType postType, int limit);

    // 인기 전문가 칼럼 6개 조회
    List<Post> findTop6ExpertColumnPostsByPostId(List<Long> popularPostsIdList);

    // 필터링없이 조회 
    Page<Post> findAllByBoardId(Long boardId, Pageable pageable);

    //작물로 필터링
    Page<Post> findPostsByBoardIdAndCrops(Long boardId, List<String> cropNames, Pageable pageable);

    //인기게시판용 좋아요 순으로 정렬 
    Page<Post> findPopularPosts(@Param("boardId") Long boardId, Pageable pageable);

    // 검색기능 
    Page<Post> findPostsBySearchQuery(String searchQuery, Long boardId, Pageable pageable);
}
