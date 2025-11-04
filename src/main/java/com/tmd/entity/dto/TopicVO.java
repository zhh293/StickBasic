package com.tmd.entity.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicVO {
    private Long id;
    private String name;
    private String description;
    private String coverImage;
    private Integer postCount;
    private Integer followerCount;
    private String username;
}
