package com.tmd.mapper;

import com.tmd.entity.dto.PostComment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostCommentMapper {
    @Insert("INSERT INTO post_comments (id, post_id, commenter_id, content, parent_id, root_id, likes, dislikes, reply_count, created_at) "
            +
            "VALUES (#{id}, #{postId}, #{commenterId}, #{content}, #{parentId}, #{rootId}, #{likes}, #{dislikes}, #{replyCount}, #{createdAt})")
    int insert(PostComment comment);

    @Select("SELECT * FROM post_comments WHERE id = #{id}")
    PostComment selectById(@Param("id") Long id);

    @Update("UPDATE post_comments SET reply_count = reply_count + #{delta} WHERE id = #{id}")
    int incrReplyCount(@Param("id") Long id, @Param("delta") int delta);

    @Select("SELECT id FROM post_comments WHERE root_id = #{rootId}")
    java.util.List<Long> selectIdsByRootId(@Param("rootId") Long rootId);

    @org.apache.ibatis.annotations.Delete("<script>" +
            "DELETE FROM post_comments WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteBatchIds(@Param("ids") java.util.List<Long> ids);
}
