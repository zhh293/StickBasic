package com.tmd.entity.dto;

import com.tmd.entity.po.UserData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description 用户数据传输对象
 * @Author Bluegod
 * @Date 2025/9/6
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String nickname;//昵称
    private String avatar;//头像url
    private String token;//令牌

    public UserVO(UserData userData) {
        this.id = userData.getId();
        this.username = userData.getUsername();
        this.nickname = userData.getNickname();
        this.avatar = userData.getAvatar();
        this.token = userData.getToken();
    }
}
