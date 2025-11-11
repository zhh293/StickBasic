package com.tmd.publisher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TopicModerationMessage {
    private Long topicId;
    private String name;
    private String description;
    private String coverImageUrl;
    private Long uploaderId;
}