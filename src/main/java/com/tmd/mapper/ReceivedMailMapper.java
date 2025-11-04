package com.tmd.mapper;


import com.github.pagehelper.Page;
import com.tmd.entity.dto.ReceivedMail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceivedMailMapper {
    void insert(ReceivedMail receivedMail);

    @Select("select * from received_mail where sticknew.received_mail.recipient_id = #{userId}")
    Page<ReceivedMail> selectByUserId(Long userId);

    @Select("select * from received_mail where sticknew.received_mail.id = #{mailId}")
    ReceivedMail selectByMailId(Long mailId);
}
