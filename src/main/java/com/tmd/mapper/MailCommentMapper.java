package com.tmd.mapper;


import com.tmd.entity.dto.MailComment;
import org.apache.ibatis.annotations.Mapper;
 import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MailCommentMapper {
    void insert(MailComment mailComment);

    @Select("select id, mail_id as mailId, commenter_id as commenterId, content, create_at as createAt " +
            "from mail_comment where commenter_id = #{userId} order by create_at desc limit #{size} offset #{offset}")
    java.util.List<MailComment> selectByCommenterId(@Param("userId") Long userId,
                                                    @Param("offset") Integer offset,
                                                    @Param("size") Integer size);

    @Select("select count(*) from mail_comment where commenter_id = #{userId}")
    long countByCommenterId(@Param("userId") Long userId);
}
