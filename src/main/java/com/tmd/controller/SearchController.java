package com.tmd.controller;


import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.SearchDTO;
import com.tmd.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@Slf4j
public class SearchController {
    @Autowired
    private SearchService searchService;
    @GetMapping
    public Result search(@RequestParam String keyword, @RequestParam Integer page, @RequestParam Integer size, @RequestParam String type){
        log.info("用户正在搜索:{}", keyword);
        SearchDTO searchDTO=SearchDTO.builder()
                .keyword(keyword)
                .page(page)
                .size(size)
                .type(type)
                .build();
        searchService.search(searchDTO);
    }
}
