package com.tmd.mapper;

import com.tmd.entity.dto.PStickVO;
import com.tmd.entity.po.PStickQueryParam;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PStickMapper {
    List<PStickVO> pStickList(PStickQueryParam pStickQueryParam);

    @Insert("insert into psticks(user_id, name, content) values(#{userId}, #{pStickVO.name}, #{pStickVO.content})")
    @Options(useGeneratedKeys = true, keyProperty = "pStickVO.id", keyColumn = "id")
    long addPStick(PStickVO pStickVO, Long userId);

    @Select("select * from psticks where id=#{id}")
    PStickVO getPStick(Long id);

    int updatePStick(Long pStickId, String content, String name);
    
    @Delete("delete from psticks where id = #{id}")
    int deletePStick(Long id);
}