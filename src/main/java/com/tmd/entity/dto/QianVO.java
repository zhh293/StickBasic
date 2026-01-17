package com.tmd.entity.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QianVO {
    private String date;
    private String level;
    private String poem;
    private String desc;
    private String yi;
    private String ji;
}
