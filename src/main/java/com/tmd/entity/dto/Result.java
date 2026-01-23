package com.tmd.entity.dto;

import lombok.Data;

@Data
public class Result {
    private Integer code;//编码:1成功,0失败
    private String msg;//错误信息
    private Object data;//数据

    public static Result success(){
        Result result = new Result();
        result.code = 200;
        result.msg = "success";
        return result;
    }

    public static Result success(Object data){
        Result result = success();
        result.data = data;
        return result;
    }

    public static Result error(String msg){
        Result result = new Result();
        result.code = 500;
        result.msg = msg;
        return result;
    }

}
