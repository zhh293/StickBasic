package com.tmd.entity.po;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/21
 */
@Data
public class PStick {
    private Long id;
    private Long UserId;
    private Long StickId;
    private String name;
    private String content;
    private Integer spirits;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createdAt; //创建时间
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate updatedAt; //修改时间
}
