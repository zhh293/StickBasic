package com.tmd.entity.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostUpdateDTO {
    private Integer postId;
    private String title;
    private String content;
    private Integer topicId;
    private String status;
}
