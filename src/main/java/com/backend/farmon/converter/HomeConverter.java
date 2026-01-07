package com.backend.farmon.converter;

import com.backend.farmon.domain.Expert;
import com.backend.farmon.domain.Post;
import com.backend.farmon.domain.PostImg;
import com.backend.farmon.domain.User;
import com.backend.farmon.dto.home.HomePostRow;
import com.backend.farmon.dto.home.HomeResponse;

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

    public static HomeResponse.PopularPostListDTO toPopularPostListDTO(List<Post> postList) {
        List<HomeResponse.PopularPostDetailDTO> popularPostDetailDTOList = postList.stream()
                .map(HomeConverter::toPopularPostDetailListDTO)
                .toList();

        return HomeResponse.PopularPostListDTO.builder()
                .popularPostList(popularPostDetailDTOList)
                .build();
    }

    public static HomeResponse.PopularPostDetailDTO toPopularPostDetailListDTO(Post post) {
        return HomeResponse.PopularPostDetailDTO.builder()
                .popularPostId(post.getId())
                .popularPostTitle(post.getPostTitle())
                .popularPostContent(post.getPostContent())
                .writer(post.getUser().getUserName())
                .profileImage(Optional.ofNullable(post.getUser())
                        .map(User::getExpert)
                        .map(Expert::getProfileImageUrl)
                        .orElse(null))
                .popularPostImage(
                        Optional.ofNullable(post.getPostImgs())
                                .filter(list -> !list.isEmpty()) // 리스트가 비어 있지 않은 경우만 처리
                                .map(list -> list.get(0)) // 첫 번째 이미지 가져오기
                                .map(PostImg::getStoredFileName) // 파일명 가져오기
                                .orElse(null) // 없으면 null 반환
                )
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