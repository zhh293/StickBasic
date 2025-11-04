package com.tmd.mapper;


import com.github.pagehelper.Page;
import com.tmd.entity.dto.mail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MailMapper {
    @Select("select * from mail where id = #{mailId}")
    mail selectById(Integer mailId);

    List<mail> selectByIds(List<Long> ids);

    @Select("select id from mail")
    List<Long> selectAllIds();

    @Select("select * from mail where sender_id = #{userId}")
    Page<mail> selectByUserId(Long l);

    void insert(mail mail);
}
