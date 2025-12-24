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
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String dailyBookmark;
    private String homepageBackground;
    private String personalSignature;
    private UserStatus status;
    private Integer accountDays;
    private Boolean isFirst;
}
