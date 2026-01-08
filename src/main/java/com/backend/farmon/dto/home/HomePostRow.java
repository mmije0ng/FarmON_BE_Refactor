package com.backend.farmon.dto.home;

import java.time.LocalDateTime;

public record HomePostRow(
        Long postId,
        String postTitle,
        String postContent,
        String postType,
        LocalDateTime createdAt,
        Long likeCount,
        Long commentCount
) {}