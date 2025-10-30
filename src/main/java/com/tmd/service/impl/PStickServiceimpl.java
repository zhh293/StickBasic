package com.tmd.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tmd.entity.dto.PStickVO;
import com.tmd.entity.dto.PageResult;
import com.tmd.entity.po.PStickQueryParam;
import com.tmd.mapper.PStickMapper;
import com.tmd.service.PStickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/14
 */
@Service
@Slf4j
public class PStickServiceimpl implements PStickService {

    @Autowired
    PStickMapper pStickMapper;
    
    @Override
    public PageResult getPTiles(PStickQueryParam pStickQueryParam) {
        //1.设置分页参数
        Page<PStickVO> page = PageHelper.startPage(pStickQueryParam.getPage(), pStickQueryParam.getPageSize());

        //2.执行查询
        List<PStickVO> pStickVOList = pStickMapper.pStickList(pStickQueryParam);

        //3.封装并返回
        PageInfo<PStickVO> pageInfo = new PageInfo<>(pStickVOList);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public Long addPTile(PStickVO pStickVO, Long userId) {
        return pStickMapper.addPStick(pStickVO, userId);
    }

    @Override
    public PStickVO getPTile(Long id) {
        return pStickMapper.getPStick(id);
    }

    @Override
    public boolean updatePTile(Long pStickId, String content, String name) {
        try {
            int rowsAffected = pStickMapper.updatePStick(pStickId, content, name);
            return rowsAffected > 0;
        } catch (Exception e) {
            log.info("更新P磁贴内容失败：{}", e.getMessage());
        }
        return false;
    }
    
    @Override
    public boolean deletePTile(Long id) {
        try {
            int rowsAffected = pStickMapper.deletePStick(id);
            return rowsAffected > 0;
        } catch (Exception e) {
            log.info("删除P磁贴失败：{}", e.getMessage());
        }
        return false;
    }
}