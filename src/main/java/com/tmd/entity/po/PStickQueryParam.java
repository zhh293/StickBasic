package com.tmd.entity.po;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/14
 */
@Data
public class PStickQueryParam {
    private Integer page = 1; //页码
    private Integer pageSize = 10; //每页展示记录数

    private long userId;
    private String name; //模糊查找的名字
    private String content; //模糊查找的内容
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate; //开始时间
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate; //结束时间

}
