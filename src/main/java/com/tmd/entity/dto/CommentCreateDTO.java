package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentCreateDTO {
    private String content;
    private Long parentId;//父评论ID，用于回复评论
    private Long rootId;//根评论ID，用于归类同层回复
}
