package com.tmd.mapper;

import com.tmd.entity.po.PStick;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMapper {

    @Insert("insert into psticks(user_id,stick_id,name,content,spirits,created_at,updated_at) values(#{userId},#{stickId},#{name},#{content},#{spirits},#{createdAt},#{updatedAt})")
    void insert(PStick pStick);
}
