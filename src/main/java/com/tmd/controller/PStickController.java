package com.tmd.controller;

import cn.hutool.json.JSONUtil;
import com.tmd.entity.dto.PStickVO;
import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.Result;
import com.tmd.entity.po.PStickQueryParam;
import com.tmd.service.PStickService;
import com.tmd.tools.BaseContext;
import com.tmd.tools.SimpleTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.tmd.constants.common.ERROR_CODE;

/**
 * @Description 人物磁贴
 * @Author Bluegod
 * @Date 2025/9/14
 */
@Slf4j
@RestController
@RequestMapping("/persons")
public class PStickController {
    @Autowired
    PStickService pStickService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping
    public Result getPSticks(PStickQueryParam pStickQueryParam, @RequestHeader("authentication") String authorization )
    {
        log.info("查询请求参数：{}", pStickQueryParam.toString());
        long uid;
        uid= BaseContext.get();
        if (uid != ERROR_CODE){
            String s = redisTemplate.opsForValue().get("PStick:" + uid);
            if(StringUtils.hasText(s)){
                // 验证字符串是否为有效的JSON对象格式（以 { 开头）
                if (s.trim().startsWith("{")) {
                    PStickVO bean = JSONUtil.toBean(s, PStickVO.class);
                    return Result.success(bean);
                } else {
                    // 如果不是有效的JSON对象，则清除无效缓存
                    log.warn("Redis中存在无效的缓存数据，已清理");
                }
            }
            pStickQueryParam.setUserId(uid);
            PageResult PStickVOList = pStickService.getPTiles(pStickQueryParam);
            log.info("查询结果：{}", PStickVOList);
            return Result.success(PStickVOList);
        }
        return Result.error("验证失败,非法访问");
    }

    @PutMapping("/{pTileId}")
    public Result updatePName(@PathVariable Long pTileId, @RequestBody Map<String, String> requestBody, @RequestHeader("authentication") String authorization )
    {
        String content = requestBody.get("content");
        String name = requestBody.get("name");
        log.info("正在更改姓名：{},正在更改内容：{}", content, name);
        long uid;
        uid= BaseContext.get();
        if (uid != ERROR_CODE){
            if(pStickService.updatePTile(pTileId, content, name)) {
                PStickVO pStickVO = pStickService.getPTile(pTileId);
                return Result.success(pStickVO);
            }
            return Result.error("修改失败");
        }
        return Result.error("验证失败,非法访问");
    }
    
    @DeleteMapping("/{pTileId}")
    public Result deletePTile(@PathVariable Long pTileId, @RequestHeader("authentication") String authorization) {
        log.info("正在删除人物磁贴，ID：{}", pTileId);
        long uid;
        uid= BaseContext.get();
        if (uid != ERROR_CODE) {
            if (pStickService.deletePTile(pTileId)) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败，人物磁贴不存在或已删除");
        }
        return Result.error("验证失败,非法访问");
    }
}