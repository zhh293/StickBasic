package com.tmd.controller;


import cn.hutool.core.util.StrUtil;
import com.tmd.entity.dto.QianVO;
import com.tmd.entity.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.chrono.MinguoEra;

@RestController
@RequestMapping("/qian")

@Slf4j
@RequiredArgsConstructor
public class QianController {

    @Autowired
    @Qualifier("DistinguishWordClient")
    private ChatClient DistinguishChatClient;

    @Autowired
    @Qualifier("testWordChatClient")
    private ChatClient testWordChatClient;
//
//    @Autowired
//    private VectorStore vectorStore;
    
    @PostMapping(value = "/generate", consumes = "multipart/form-data")
    public Result generate(@RequestParam("file") MultipartFile file) {
        log.info("generate");
        //检查file是否是 图片
        if (file == null || file.isEmpty()) {
            return Result.error("请上传图片文件");
        }
        String originalFilename = file.getOriginalFilename();
        if(StrUtil.isBlank(originalFilename)){
            return Result.error("请上传图片文件");
        }
        if (!originalFilename.endsWith(".png") && !originalFilename.endsWith(".jpg") && !originalFilename.endsWith(".jpeg")) {
            return Result.error("请上传图片文件");
        }
        
        String result = "";
        try {
            result = DistinguishChatClient.prompt()
                    .user(u->u.text("请严格按照系统提示词进行图片识别")
                            .media(getType(originalFilename),file.getResource()))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("图片识别过程中发生异常: ", e);
            return Result.error("图片识别失败: " + e.getMessage());
        }
        
        if(StrUtil.isBlank(result)){
            return Result.error("图片识别失败");
        }
        if (result.equals("FAILED")){
            return Result.error("图片识别失败");
        }

        log.info("图片识别结果: {}", result);
        //上面交给一个AI即可，下面的知识库挂载和结构输出放在一个AI中
        QianVO result2;
        try {
            result2 = testWordChatClient.prompt()
                    .user("用户输入的字为"+result)
//                    .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
//                            .similarityThreshold(0.7)
//                            .topK(10)
//                            .query(result)
//                            .build()
//                    ))
                    .call()
                    .entity(QianVO.class);
            log.info("知识库查询结果: {}", result2);
        } catch (Exception e) {
            log.error("知识库查询过程中发生异常: ", e);
            return Result.error("知识库查询失败: " + e.getMessage());
        }
        return Result.success(result2);
    }
    private MimeType getType(String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "png":
                return MimeType.valueOf("image/png");
            case "jpg":
                return MimeType.valueOf("image/jpeg");
            case "jpeg":
                return MimeType.valueOf("image/jpeg");
            case "gif":
                return MimeType.valueOf("image/gif");
            case "bmp":
                return MimeType.valueOf("image/bmp");
            case "webp":
                return MimeType.valueOf("image/webp");
            default:
                return MimeType.valueOf("application/octet-stream");
        }
    }
}