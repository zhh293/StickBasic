package com.tmd.mapper;


import com.tmd.entity.dto.ReceivedMail;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReceivedMailMapper {
    void insert(ReceivedMail receivedMail);
}
