package com.tmd.service.impl;

import com.tmd.mapper.FuncMapper;
import com.tmd.service.FuncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/10
 */
@Service
public class FuncServiceimpl implements FuncService {

    @Autowired
    private FuncMapper funcMapper;
    @Override
    public String saying() {
        return funcMapper.saying();
    }
}
