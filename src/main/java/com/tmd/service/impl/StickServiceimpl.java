package com.tmd.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.po.StickQueryParam;
import com.tmd.mapper.StickMapper;
import com.tmd.service.StickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/11
 */
@Service
@Slf4j
public class StickServiceimpl implements StickService {
    @Autowired
    StickMapper stickMapper;
    
    @Override
    public PageResult getTiles(StickQueryParam stickQueryParam) {
        //1.设置分页参数
        Page<StickVO> page = PageHelper.startPage(stickQueryParam.getPage(), stickQueryParam.getPageSize());

        //2.执行查询
        List<StickVO> stickVOList = stickMapper.stickList(stickQueryParam);

        //3.封装并返回
        PageInfo<StickVO> pageInfo = new PageInfo<>(stickVOList);
        return PageResult.builder()
                .rows(pageInfo.getList())
                .currentPage(pageInfo.getPageNum())
                .total(pageInfo.getTotal())
                .build();
    }

    @Override
    public Long addTile(StickVO stickVO, Long uid) {
        return stickMapper.addTile(stickVO, uid);
    }

    @Override
    public StickVO getTile(Long id) {
        return stickMapper.getTile(id);
    }

    @Override
    public boolean updateTile(Long tileId, String content) {
        try {
            stickMapper.updateTile(tileId, content);
        } catch (Exception e) {
            log.info("更新磁贴内容失败：{}", e.getMessage());
            return false;
        }
        return true;
    }
    
    @Override
    public boolean deleteTile(Long id) {
        try {
            int rowsAffected = stickMapper.deleteTile(id);
            return rowsAffected > 0;
        } catch (Exception e) {
            log.info("删除磁贴失败：{}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<StickVO> getAllTiles(long uid) {
        return stickMapper.getAllTiles(uid);
    }
}