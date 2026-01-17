package com.tmd.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class NeedTools {

    @Tool
    public String getTime(){
        return "现在时间是：" + java.time.LocalDateTime.now();
    }

}
