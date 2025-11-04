package com.tmd.mapper;


import com.tmd.entity.dto.MailComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailCommentMapper {
    void insert(MailComment mailComment);
}
