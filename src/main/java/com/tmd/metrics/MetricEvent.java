package com.tmd.metrics;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricEvent {
    private Long id;
    private String name;
    private String tags;
    private Long value;
    private Long durationMs;
    private LocalDateTime createdAt;
}