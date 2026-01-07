package com.tmd.mapper;

import com.tmd.entity.dto.PostComment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostCommentMapper {
    @Insert("INSERT INTO post_comments (id, post_id, commenter_id, content, parent_id, root_id, likes, dislikes, reply_count, created_at) "
            +
            "VALUES (#{id}, #{postId}, #{commenterId}, #{content}, #{parentId}, #{rootId}, #{likes}, #{dislikes}, #{replyCount}, #{createdAt})")
    int insert(PostComment comment);
}
