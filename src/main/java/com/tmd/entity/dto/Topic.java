package com.tmd.entity.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Topic {
    private Long id;
    private String name;
    private String description;
    private String coverImage;
    private Integer postCount;
    private Integer followerCount;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
