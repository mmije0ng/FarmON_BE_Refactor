package com.backend.farmon.repository.LikeCountRepository;

import com.backend.farmon.domain.LikeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

public interface LikeCountRepository extends JpaRepository<LikeCount, Long> {

    // 게시글과 연관된 좋아요 개수 조회
    @Query("SELECT COUNT(lc) FROM LikeCount lc WHERE lc.post.id = :postId")
    Integer countLikeCountsByPostId(@Param("postId") Long postId);


    List<LikeCount> findAllByPostId(Long postId);

    LikeCount findByUserId(Long userid);

    //특정 게시물에 대해 찾기
    LikeCount findByUserIdAndPostId(Long userid, Long postId);



}