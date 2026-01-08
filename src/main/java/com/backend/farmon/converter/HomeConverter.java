package com.backend.farmon.converter;

import com.backend.farmon.domain.Expert;
import com.backend.farmon.domain.Post;
import com.backend.farmon.domain.PostImg;
import com.backend.farmon.domain.User;
import com.backend.farmon.dto.home.HomePostRow;
import com.backend.farmon.dto.home.HomeResponse;
import com.backend.farmon.dto.home.PopularExpertPostRow;

import java.util.List;
import java.util.Optional;

public class HomeConverter {
    public static HomeResponse.PostListDTO toPostListDTO(List<HomePostRow> rows) {
        var list = rows.stream()
                .map(r -> HomeResponse.PostDetailDTO.builder()
                        .postId(r.postId())
                        .postTitle(r.postTitle())
                        .postContent(r.postContent())
                        .postType(r.postType())
                        .likeCount(r.likeCount().intValue())
                        .commentCount(r.commentCount().intValue())
                        .build()
                )
                .toList();

        return new HomeResponse.PostListDTO(list);
    }

    public static HomeResponse.PopularPostListDTO toPopularPostListDTO(List<PopularExpertPostRow> rows) {
        List<HomeResponse.PopularPostDetailDTO> popularPostDetailDTOList = rows.stream()
                .map(HomeConverter::toPopularPostDetailDTOF)
                .toList();

        return HomeResponse.PopularPostListDTO.builder()
                .popularPostList(popularPostDetailDTOList)
                .build();
    }

    public static HomeResponse.PopularPostDetailDTO toPopularPostDetailDTOF(PopularExpertPostRow row) {
        return HomeResponse.PopularPostDetailDTO.builder()
                .popularPostId(row.getPostId())
                .popularPostTitle(row.getTitle())
                .popularPostContent(row.getContent())
                .writer(row.getWriter())
                .profileImage(row.getProfileImageUrl())
                .popularPostImage(row.getFirstImageStoredFileName())
                .build();
    }

    public static HomeResponse.AutoCompleteSearchPostDTO toAutoCompleteSearchPostDTO(){
        return HomeResponse.AutoCompleteSearchPostDTO.builder()
                .isSearchSave(true)
                .build();
    }

    public static HomeResponse.SearchDeleteDTO toSearchDeleteDTO(){
        return HomeResponse.SearchDeleteDTO.builder()
                .isSearchDelete(true)
                .build();
    }

    public static HomeResponse.AutoCompleteSearchDTO toAutoCompleteSearchDTO(List<String> searchList){
        return HomeResponse.AutoCompleteSearchDTO.builder()
                .searchList(searchList)
                .build();
    }

    public static HomeResponse.RecommendSearchListDTO toRecommendSearchListDTO(List<String> recommendSearchList){
        return HomeResponse.RecommendSearchListDTO.builder()
                .recommendSearchList(recommendSearchList)
                .build();
    }

    public static HomeResponse.RecentSearchListDTO toRecentSearchListDTO(List<String> recentSearchList){
        return HomeResponse.RecentSearchListDTO.builder()
                .recentSearchList(recentSearchList)
                .build();
    }
}