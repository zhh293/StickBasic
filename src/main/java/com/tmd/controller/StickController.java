package com.tmd.controller;

import cn.hutool.json.JSONUtil;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.po.StickQueryParam;
import com.tmd.service.StickService;
import com.tmd.tools.BaseContext;
import com.tmd.tools.SimpleTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.tmd.constants.common.ERROR_CODE;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/11
 */
@Slf4j
@RestController
@RequestMapping("/tiles")
public class StickController {
    @Autowired
    StickService stickService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ThreadPoolConfig threadPoolConfig;

    @GetMapping
    public Result getTiles(StickQueryParam stickQueryParam, @RequestHeader("authentication") String authorization ){
        log.info("查询请求参数：{}",stickQueryParam.toString());
        long uid;
        uid= BaseContext.get();
        log.info("用户id为{}", uid);
        AtomicLong count = new AtomicLong(uid);
        log.info("用户正在查询磁贴{}", uid);
        if (uid != ERROR_CODE){
            String s = redisTemplate.opsForValue().get("Stick:" + uid);
            //缓存中我想要存全部的数据
            if(StringUtils.hasText(s)){
                // 根据条件查询对应的磁贴
                List<StickVO> StickVOList = JSONUtil.toList(s, StickVO.class);
                List<StickVO> filteredList = StickVOList.stream()
                        .filter(stick -> {
                            // 如果 content 不为空，则检查是否包含该内容（模糊匹配）
                            if (StringUtils.hasText(stickQueryParam.getContent())) {
                                if (!stick.getContent().contains(stickQueryParam.getContent())) {
                                    return false;
                                }
                            }
                            // 如果 startDate 不为空，则检查创建时间是否大于等于 startDate
                            if (stickQueryParam.getStartDate() != null) {
                                String createdAt = stick.getCreatedAt();
                                if (createdAt != null && !createdAt.trim().isEmpty()) {
                                    LocalDateTime createdDate = LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                    if (createdDate.toLocalDate().isBefore(stickQueryParam.getStartDate())) {
                                        return false;
                                    }
                                } else {
                                    // 如果创建时间为空，根据业务需求决定是否过滤掉该记录
                                    return false; // 或者根据业务逻辑选择返回true
                                }
                            }
// 如果 endDate 不为空，则检查创建时间是否小于等于 endDate
                            if (stickQueryParam.getEndDate() != null) {
                                String createdAt = stick.getCreatedAt();
                                if (createdAt != null && !createdAt.trim().isEmpty()) {
                                    LocalDateTime createdDate = LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                    if (createdDate.toLocalDate().isAfter(stickQueryParam.getEndDate())) {
                                        return false;
                                    }
                                } else {
                                    // 如果创建时间为空，根据业务需求决定是否过滤掉该记录
                                    return false; // 或者根据业务逻辑选择返回true
                                }
                            }
                            return true;
                        })
                        .toList();
// 分页处理
                int total = filteredList.size();
                int page = stickQueryParam.getPage();
                int pageSize = stickQueryParam.getPageSize();
                int fromIndex = (page - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, total);
// 构造 PageResult
                List<StickVO> pagedResult = filteredList.subList(fromIndex, toIndex);
                PageResult result = PageResult.builder()
                        .total((long) total)
                        .rows(pagedResult)
                        .currentPage(page)
                        .build();
                return Result.success(result);
            }
            stickQueryParam.setUserId(uid);
            PageResult StickVOList = stickService.getTiles(stickQueryParam);
            log.info("查询成功");
            threadPoolConfig.threadPoolExecutor().execute(() -> {
                //恢复缓存
                //先查询数据库中所有磁贴
                List<StickVO> StickVOAllList = stickService.getAllTiles(count.get());
                //转换为json字符串
                String json = JSONUtil.toJsonStr(StickVOAllList);
                redisTemplate.opsForValue().set("Stick:" + count.get(), json);
                log.info("缓存成功");
            });
            return Result.success(StickVOList);
        }
        return Result.error("验证失败,非法访问");
    }

    @PostMapping
    public Result addTile(@RequestBody Map<String, String> requestBody, @RequestHeader("authentication") String authorization ){
        String content = requestBody.get("content");
        log.info("添加请求内容：{}",content);
        long uid;
        uid= BaseContext.get();
        if (uid != ERROR_CODE){
            StickVO stickVO = new StickVO();
            stickVO.setContent(content);
            stickVO.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            stickVO.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            stickService.addTile(stickVO, uid);
            stickVO = stickService.getTile(stickVO.getId());
            log.info("添加成功");
            redisTemplate.delete("Stick:" + uid);
            return Result.success(stickVO);
        }
        return Result.error("验证失败,非法访问");
    }
    @PutMapping("/{tileId}")
    public Result updateTile(@PathVariable Long tileId, @RequestBody Map<String, String> requestBody, @RequestHeader("authentication") String authorization ){
        String content = requestBody.get("content");
        log.info("正在修改{}磁贴内容为：{}",tileId, content);
        long uid;
        uid= BaseContext.get();
        if (uid != ERROR_CODE){
            if(stickService.updateTile(tileId, content)) {
                StickVO stickVO = stickService.getTile(tileId);
                log.info("修改成功");
                redisTemplate.delete("Stick:" + uid);
                return Result.success(stickVO);
            }
            return Result.error("修改失败");
        }
        return Result.error("验证失败,非法访问");
    }
    
    @DeleteMapping("/{tileId}")
    public Result deleteTile(@PathVariable Long tileId, @RequestHeader("authentication") String authorization) {
        log.info("正在删除磁贴，ID：{}", tileId);
        long uid;
        uid= BaseContext.get();
        if (uid != ERROR_CODE) {
            if (stickService.deleteTile(tileId)) {
                redisTemplate.delete("Stick:" + uid);
                return Result.success("删除成功");
            }
            return Result.error("删除失败，磁贴不存在或已删除");
        }
        return Result.error("验证失败,非法访问");
    }
}