package com.tmd;

import com.tmd.config.L2CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableConfigurationProperties(L2CacheProperties.class)
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling
@Slf4j
public class BasicBackApplication {
//    @Autowired
//    private VectorStore vectorStore;
//    @PostConstruct
//    private void initVectorStore() {
//        try {
//            // 读取资源文件
//            TextReader textReader = new TextReader(new FileSystemResource("src/main/resources/测字所需要的必要资料.txt"));
//            List<Document> rawDocuments = textReader.get();
//
//            // 数据清洗和预处理
//            List<Document> processedDocuments = preprocessDocuments(rawDocuments);
//
//            // 批量添加到向量数据库
//            if (!processedDocuments.isEmpty()) {
//                vectorStore.add(processedDocuments);
//                log.info("成功加载 {} 个文档到向量数据库", processedDocuments.size());
//            } else {
//                log.warn("没有有效文档被加载到向量数据库");
//            }
//        } catch (Exception e) {
//            log.error("初始化向量数据库时发生错误", e);
//            throw new RuntimeException("向量数据库初始化失败", e);
//        }
//    }
//
//    /**
//     * 文档预处理方法
//     */
//    private List<Document> preprocessDocuments(List<Document> documents) {
//        List<Document> processed = new ArrayList<>();
//
//        for (Document doc : documents) {
//            try {
//                // 1. 清洗文本内容
//                String cleanedContent = cleanText(doc.getText());
//
//                // 2. 验证内容有效性
//                if (isValidContent(cleanedContent)) {
//                    // 3. 创建新的文档对象
//                    Document processedDoc = new Document(
//                            cleanedContent,
//                            doc.getMetadata()
//                    );
//
//                    // 4. 添加额外元数据（如文档ID、创建时间等）
//                    processedDoc.getMetadata().put("processed_at", LocalDateTime.now().toString());
//                    processedDoc.getMetadata().put("source_file", "测字所需要的必要资料.txt");
//
//                    processed.add(processedDoc);
//                }
//            } catch (Exception e) {
//                log.warn("处理文档时发生错误，跳过该文档: {}", doc.getText(), e);
//                continue;
//            }
//        }
//
//        return processed;
//    }
//
//    /**
//     * 文本清洗方法
//     */
//    private String cleanText(String text) {
//        if (text == null || text.trim().isEmpty()) {
//            return "";
//        }
//
//        // 去除首尾空白字符
//        text = text.trim();
//
//        // 移除多余的空行
//        text = text.replaceAll("\\n\\s*\\n", "\n");
//
//        // 移除HTML标签（如果存在）
//        text = text.replaceAll("<[^>]*>", "");
//
//        // 移除特殊字符（保留中文、英文、数字、基本标点）
//        text = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s\\p{Punct}]", "");
//
//        // 统一换行符
//        text = text.replaceAll("\\r\\n|\\r", "\n");
//
//        return text;
//    }
//
//    /**
//     * 内容有效性验证
//     */
//    private boolean isValidContent(String content) {
//        if (content == null || content.length() < 10) {
//            return false;
//        }
//
//        // 至少包含一个汉字或英文字母
//        return content.matches(".*[\\u4e00-\\u9fa5a-zA-Z].*");
//    }


    public static void main(String[] args) {
        SpringApplication.run(BasicBackApplication.class, args);
    }

}
