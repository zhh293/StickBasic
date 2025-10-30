package com.tmd.mapper;

import com.tmd.entity.dto.StickVO;
import com.tmd.entity.po.StickQueryParam;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface StickMapper {
    List<StickVO> stickList( StickQueryParam stickQueryParam);

    @Insert("insert into sticks(user_id,content) values(#{uid},#{stickVO.content})")
    @Options(useGeneratedKeys = true, keyProperty = "stickVO.id", keyColumn = "id")
    long addTile(StickVO stickVO, Long uid);

    @Select("select * from sticks where id=#{id}")
    StickVO getTile(Long id);

    void updateTile(Long tileId, String content);
    
    @Delete("delete from sticks where id = #{id}")
    int deleteTile(Long id);

    @Select("select * from sticks where user_id = #{uid}")
    List<StickVO> getAllTiles(long uid);
}