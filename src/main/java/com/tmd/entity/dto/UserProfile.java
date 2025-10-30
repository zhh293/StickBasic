package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String nickname;
    private String avatar;
}
