package com.tmd.controller;

import com.tmd.config.RabbitMQConfig;
import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.dto.UserContent;
import com.tmd.entity.po.StickQueryParam;
import com.tmd.publisher.MessageProducer;
import com.tmd.repository.ChatHistoryRepository;
import com.tmd.repository.FileRepository;
import com.tmd.service.AiService;
import com.tmd.service.PStickService;
import com.tmd.service.StickService;
import com.tmd.tools.SimpleTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import static com.tmd.constants.common.ERROR_CODE;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor.FILTER_EXPRESSION;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/16
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final ChatClient pdfChatClient;

    private final ChatClient chatClient;

    private final ChatClient convertClient;

    private final ChatClient storyClient;

    private final ChatClient summaryClient;

    private final ChatHistoryRepository chatHistoryRepository;

    private final FileRepository fileRepository;

    private final StickService stickService;

    private final PStickService pStickService;

    private final VectorStore vectorStore;
    @Autowired
    private AiService aiService;
    @Autowired
    private MessageProducer messageProducer;
    private boolean[]isRoutingKey = {true,false};
    @GetMapping(value = "/summary")
    public Result summaryai(@RequestParam List<Long> stickIds, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization){
        log.info("用户尝试调用Ai总结磁贴分析");
        var uid = SimpleTools.checkToken(authorization);
        if (uid != ERROR_CODE){
            List<String> contents = stickIds.stream().map(sid -> stickService.getTile(sid).getContent()).toList();
            //请求模型
            log.info("用户正在调用Ai总结磁贴分析");
            Random random = new Random();
            UserContent userContent = new UserContent();
            userContent.setUserId(uid);
            userContent.setContents(contents);
            messageProducer.sendDirectMessage(userContent,isRoutingKey[random.nextInt(2)]);
            return Result.success("正在后台处理，请稍后查看结果");
        }
        return Result.error("验证失败,非法访问");
    }

    @GetMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chatai(String content,String chatId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization){
        log.info("用户尝试调用Ai对话功能");
        var uid = SimpleTools.checkToken(authorization);
        if (uid != ERROR_CODE){
            log.info("用户正在调用Ai对话功能");
            StickQueryParam stickQueryParam = new StickQueryParam();
            stickQueryParam.setUserId(uid);
            String history = stickService.getTiles(stickQueryParam).toString();
            return chatClient.prompt()
                    .user(history + content)
                    .stream()
                    .content();
        }
        log.error("验证失败,非法访问");
        return null;
    }

    @GetMapping(value = "/story")
    public Result storyai(@RequestParam List<Long> pstickIds, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization){
        log.info("用户正在调用Ai总结人物磁贴");
        var uid = SimpleTools.checkToken(authorization);
        log.info(String.valueOf(uid));
        if (uid != ERROR_CODE){
            List<String> contents = pstickIds.stream().map(sid -> pStickService.getPTile(sid).getContent()).toList();
            log.info(contents.toString());
            //请求模型
            log.info("用户正在调用Ai总结人物磁贴");;
            String response = storyClient.prompt()
                    .user(contents.toString())
                    .call()
                    .content();
            log.info(response);
            return Result.success(response);
        }
        return Result.error("验证失败,非法访问");
    }
    @GetMapping("/stick2pstick")
    public Result stick2pstick(Long sid, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization ){
        log.info("用户正在调用Ai将磁贴归纳到人物中");
        var uid = SimpleTools.checkToken(authorization);
        if (uid != ERROR_CODE){
            StickVO stickVO = stickService.getTile(sid);
//            log.info(stickVO.toString());
            //请求模型
            String modelResponse = convertClient.prompt()
                    .user(stickVO.getContent())
                    .call()
                    .content();
            try {
                aiService.induction(modelResponse, uid, sid);
                return Result.success();
            } catch (Exception e) {
                log.error("插入失败",e);
                return Result.error("AI分析磁贴插入失败");
            }
        }
        return Result.error("验证失败,非法访问");
    }

    @RequestMapping(value = "/pdfchat", produces = "text/html;charset=utf-8")
    public Flux<String> pdfchat(String prompt, String chatId) {
        // 1.找到会话文件
        Resource file = fileRepository.getFile(chatId);
        if (!file.exists()) {
            // 文件不存在，不回答
            throw new RuntimeException("会话文件不存在！");
        }
        // 2.保存会话id
        chatHistoryRepository.save("pdf", chatId);
        // 3.请求模型
        return (pdfChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .advisors(a -> a.param(FILTER_EXPRESSION, "file_name == '" + file.getFilename() + "'"))
                .stream()
                .content());
    }

    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验文件是否为PDF格式
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                return Result.error("只能上传PDF文件！");
            }
            // 2.保存文件
            boolean success = fileRepository.save(chatId, file.getResource());
            if (!success) {
                return Result.error("保存文件失败！");
            }
            // 3.写入向量库
            this.writeToVectorStore(file.getResource());
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to upload PDF.", e);
            return Result.error("上传文件失败！");
        }
    }
    private void writeToVectorStore(Resource resource) {
        // 1.创建PDF的读取器
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource, // 文件源
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1) // 每1页PDF作为一个Document
                        .build()
        );
        // 2.读取PDF文档，拆分为Document
        List<Document> documents = reader.read();
        // 3.写入向量库
        vectorStore.add(documents);
    }
}
