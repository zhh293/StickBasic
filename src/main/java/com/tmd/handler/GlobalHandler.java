package com.tmd.handler;


import com.tmd.constants.MessageConstant;
import com.tmd.entity.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLIntegrityConstraintViolationException;

@ControllerAdvice
@Slf4j
public class GlobalHandler {
    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(Exception ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        log.error("异常信息{}", ex.getMessage());
        if(ex.getMessage().contains("Duplicate entry")){
            String []split = ex.getMessage().split(" ");
            String username=split[2];
            String message=username+ MessageConstant.ALREADY_EXITS;
            return Result.error(message);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);

        }
    }
}
