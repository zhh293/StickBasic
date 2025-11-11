package com.tmd.mapper;

import com.tmd.entity.dto.Attachment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AttachmentMapper {

        /**
         * 插入附件记录（支持手动指定ID）
         */
        @Insert("INSERT INTO attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, " +
                        "business_type, business_id, uploader_id, upload_time, created_at, updated_at) " +
                        "VALUES (#{id}, #{fileId}, #{fileUrl}, #{fileName}, #{fileSize}, #{fileType}, #{mimeType}, " +
                        "#{businessType}, #{businessId}, #{uploaderId}, #{uploadTime}, #{createdAt}, #{updatedAt})")
        void insert(Attachment attachment);

        /**
         * 根据ID查询附件
         */
        @Select("SELECT * FROM attachment WHERE id = #{id}")
        Attachment selectById(Long id);

        /**
         * 根据业务类型和业务ID查询附件列表
         */
        @Select("SELECT * FROM attachment WHERE business_type = #{businessType} AND business_id = #{businessId}")
        List<Attachment> selectByBusiness(@Param("businessType") String businessType,
                        @Param("businessId") Long businessId);

        /**
         * 批量根据业务类型和业务ID列表查询附件
         */
        @Select({
            "<script>",
            "SELECT * FROM attachment",
            "WHERE business_type = #{businessType}",
            "AND business_id IN",
            "<foreach collection='businessIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
        })
        List<Attachment> selectByBusinessIds(@Param("businessType") String businessType,
                        @Param("businessIds") List<Long> businessIds);

        /**
         * 根据文件ID（OSS objectKey）查询附件
         */
        @Select("SELECT * FROM attachment WHERE file_id = #{fileId}")
        Attachment selectByFileId(String fileId);

        /**
         * 根据上传者ID查询附件列表
         */
        @Select("SELECT * FROM attachment WHERE uploader_id = #{uploaderId} ORDER BY upload_time DESC")
        List<Attachment> selectByUploaderId(Long uploaderId);

        /**
         * 根据ID删除附件
         */
        @Delete("DELETE FROM attachment WHERE id = #{id}")
        void deleteById(Long id);

        /**
         * 根据业务类型和业务ID删除附件
         */
        @Delete("DELETE FROM attachment WHERE business_type = #{businessType} AND business_id = #{businessId}")
        void deleteByBusiness(@Param("businessType") String businessType,
                        @Param("businessId") Long businessId);

        /**
         * 根据文件ID删除附件
         */
        @Delete("DELETE FROM attachment WHERE file_id = #{fileId}")
        void deleteByFileId(String fileId);

        /**
         * 批量插入附件
         */
        void batchInsert(List<Attachment> attachments);
}
