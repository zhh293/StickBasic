package com.tmd.controller;

import com.tmd.entity.dto.Result;
import com.tmd.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Slf4j
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public Result search(@RequestParam String keyword,
                         @RequestParam(required = false, defaultValue = "all") String type,
                         @RequestParam(required = false, defaultValue = "1") Integer page,
                         @RequestParam(required = false, defaultValue = "10") Integer size) {
        log.info("全文搜索: keyword={}, type={}, page={}, size={}", keyword, type, page, size);
        return searchService.search(keyword, type, page, size);
    }
}
