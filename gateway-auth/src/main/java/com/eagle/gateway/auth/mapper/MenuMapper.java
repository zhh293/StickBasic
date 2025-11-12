package com.eagle.gateway.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eagle.gateway.auth.domain.Menu;


import java.util.List;

public interface MenuMapper extends BaseMapper<Menu> {
    List<String> selectPermsByUserId(Long userId);
}
