package com.backend.farmon.dto.home;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PopularExpertPostRow {
    private Long postId;
    private String title;
    private String content;
    private String writer;
    private String profileImageUrl;
    private String firstImageStoredFileName;
}
