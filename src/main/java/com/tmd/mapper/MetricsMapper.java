package com.tmd.mapper;


import com.tmd.metrics.MetricEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MetricsMapper {
    @Update("CREATE TABLE IF NOT EXISTS metrics_event (" +
            "id BIGINT PRIMARY KEY," +
            "name VARCHAR(128) NOT NULL," +
            "tags JSON," + // 修改点：JSONB -> JSON
            "value BIGINT," +
            "duration_ms BIGINT," +
            "created_at TIMESTAMP NOT NULL" +
            ")")
    void createTableIfNotExists();

    @Insert("INSERT INTO metrics_event (id,name,tags,value,duration_ms,created_at) " +
            "VALUES (#{e.id},#{e.name},#{e.tags},#{e.value},#{e.durationMs},#{e.createdAt})") // 修改点：移除CAST
    void insert(@Param("e") MetricEvent e);

}