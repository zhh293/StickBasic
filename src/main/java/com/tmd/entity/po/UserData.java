package com.tmd.entity.po;

import com.tmd.entity.dto.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserData extends UserVO {
    private Long id;
    private String username;
    private String nickname;//昵称
    private String avatar;//头像url
    private String token;//令牌
    private String password;
}
