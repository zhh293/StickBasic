package com.tmd.service;

import com.tmd.entity.dto.PStickVO;
import com.tmd.entity.dto.PageResult;
import com.tmd.entity.po.PStickQueryParam;

public interface PStickService {
    PageResult getPTiles(PStickQueryParam pStickQueryParam);
    
    Long addPTile(PStickVO pStickVO, Long userId);
    
    PStickVO getPTile(Long id);
    
    boolean updatePTile(Long pStickId, String content, String name);
    
    boolean deletePTile(Long id);
}