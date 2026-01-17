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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @Autowired
    private VectorStore vectorStore;
    @PostMapping("/generate")
    public Result generate(@RequestParam MultipartFile  file) {
        log.info("generate");
        //检查file是否是 图片
        String originalFilename = file.getOriginalFilename();
        if(StrUtil.isBlank(originalFilename)){
            return Result.error("请上传图片文件");
        }
        if (!originalFilename.endsWith(".png") && !originalFilename.endsWith(".jpg") && !originalFilename.endsWith(".jpeg")) {
            return Result.error("请上传图片文件");
        }
        String result=DistinguishChatClient.prompt()
                .user(u->u.text("请严格按照系统提示词进行图片识别")
                        .media(getType(originalFilename),file.getResource()))
                .call()
                .content();
        if(StrUtil.isBlank(result)){
            return Result.error("图片识别失败");
        }
        if (result.equals("FAILED")){
            return Result.error("图片识别失败");
        }
        //上面交给一个AI即可，下面的知识库挂载和结构输出放在一个AI中
        QianVO result2=testWordChatClient.prompt()
                .user("用户输入的字为"+result)
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
                        .similarityThreshold(0.7)
                        .topK(10)
                        .query(result)
                        .build()
                ))
                .call()
                .entity(QianVO.class);
        return Result.success(result2);
    }
    private MimeType getType(String originalFilename) {
        String type = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        String lowerCase = type.toLowerCase();
        return MimeTypeUtils.parseMimeType(lowerCase);
    }
}
