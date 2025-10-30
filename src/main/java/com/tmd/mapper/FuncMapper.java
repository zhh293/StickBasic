package com.tmd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FuncMapper {

    @Insert("insert into sayings(saying) values(#{saying})")
    void insertsaying(String saying);

    @Select("select saying from sayings order by RANDOM() limit 1")
    String saying();
}
