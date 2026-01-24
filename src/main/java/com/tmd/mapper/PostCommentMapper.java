package com.tmd.mapper;

import com.tmd.entity.dto.PostComment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostCommentMapper {
        @Insert("INSERT INTO post_comment (id, post_id, commenter_id, content, parent_id, root_id, likes, dislikes, reply_count, created_at) "
                        +
                        "VALUES (#{id}, #{postId}, #{commenterId}, #{content}, #{parentId}, #{rootId}, #{likes}, #{dislikes}, #{replyCount}, #{createdAt})")
        int insert(PostComment comment);

        @Select("SELECT * FROM post_comment WHERE id = #{id}")
        PostComment selectById(@Param("id") Long id);

        @Select("<script>" +
                        "SELECT * FROM post_comment WHERE id IN " +
                        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                        "#{id}" +
                        "</foreach>" +
                        "</script>")
        java.util.List<PostComment> selectBatchIds(@Param("ids") java.util.List<Long> ids);

        @Update("UPDATE post_comment SET reply_count = reply_count + #{delta} WHERE id = #{id}")
        int incrReplyCount(@Param("id") Long id, @Param("delta") int delta);

        @Select("SELECT id FROM post_comment WHERE root_id = #{rootId} ORDER BY created_at ASC")
        java.util.List<Long> selectIdsByRootId(@Param("rootId") Long rootId);

        @Select("SELECT id FROM post_comment WHERE post_id = #{postId} AND root_id = id ORDER BY created_at DESC LIMIT #{offset}, #{size}")
        java.util.List<Long> selectRootIdsByPostId(@Param("postId") Long postId, @Param("offset") int offset,
                        @Param("size") int size);

        @Select("SELECT COUNT(*) FROM post_comment WHERE post_id = #{postId} AND root_id = id")
        long countRootsByPostId(@Param("postId") Long postId);

        @Select("<script>" +
                        "SELECT * FROM post_comment " +
                        "WHERE post_id = #{postId} AND root_id = id " +
                        "<choose>" +
                        "<when test=\"sort == 'hottest'\">ORDER BY likes DESC, created_at DESC</when>" +
                        "<when test=\"sort == 'oldest'\">ORDER BY created_at ASC</when>" +
                        "<otherwise>ORDER BY created_at DESC</otherwise>" +
                        "</choose>" +
                        "LIMIT #{offset}, #{size}" +
                        "</script>")
        java.util.List<PostComment> selectRoots(@Param("postId") Long postId, @Param("offset") int offset,
                        @Param("size") int size, @Param("sort") String sort);

        @Select("<script>" +
                        "SELECT * FROM post_comment WHERE root_id IN " +
                        "<foreach collection='rootIds' item='id' open='(' separator=',' close=')'>" +
                        "#{id}" +
                        "</foreach>" +
                        " AND id != root_id " +
                        "ORDER BY created_at ASC" +
                        "</script>")
        java.util.List<PostComment> selectRepliesByRootIds(@Param("rootIds") java.util.List<Long> rootIds);

        @org.apache.ibatis.annotations.Delete("<script>" +
                        "DELETE FROM post_comment WHERE id IN " +
                        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                        "#{id}" +
                        "</foreach>" +
                        "</script>")
        int deleteBatchIds(@Param("ids") java.util.List<Long> ids);
}
