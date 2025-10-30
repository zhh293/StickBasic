package com.tmd.service;

import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.po.StickQueryParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/11
 */
@Service
public interface StickService {
    PageResult getTiles(StickQueryParam stickQueryParam);

    Long addTile(StickVO stickVO, Long uid);

    StickVO getTile(Long id);

    boolean updateTile(Long tileId, String content);
    
    boolean deleteTile(Long id);

    List<StickVO> getAllTiles(long uid);
}