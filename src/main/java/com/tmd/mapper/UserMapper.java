package com.tmd.mapper;

import com.tmd.entity.dto.UserProfile;
import com.tmd.entity.dto.UserUpdateDTO;
import com.tmd.entity.po.UserData;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Insert("insert into users(username,password,nickname,avatar) values(#{username},#{password},#{nickname},#{avatar})")
    @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
    void register(UserData userData);

    @Select("select * from users where username=#{username}")
    UserData findByUsername(UserData userData);

    @Select("select * from users where username=#{username};")
    UserData check(String name);

    @Select("select username, nickname, avatar, email,homepage_background as homepageBackground, personal_signature as personalSignature, status, account_days as accountDays from users where id=#{userId};")
    UserProfile getProfile(Long userId);

    UserData findByUserIdAndPassword(@Param("uid") long uid, @Param("oldPassword") String oldPassword);

    void updatePassword(@Param("uid") long uid, @Param("oldPassword") String oldPassword,
            @Param("newPassword") String newPassword);

    void update(@Param("id") Long id, @Param("userUpdateDTO") UserUpdateDTO userUpdateDTO);

    void softDelete(@Param("userId") Long userId);
}