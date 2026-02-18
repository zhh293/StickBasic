package com.tmd.config;

import com.tmd.entity.dto.AliOssUtil;
import com.tmd.properties.AliOssProperties;
import com.tmd.tools.NeedTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.tmd.constants.SystemConstants.*;

@Configuration
public class CommonConfiguration {

        @Bean
        public ChatMemory chatMemory() {
                return new InMemoryChatMemory();
        }

        @Bean
        public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
                return SimpleVectorStore.builder(embeddingModel).build();
        }
        @Bean("DistinguishWordClient")
        public ChatClient DistinguishChatClient(OpenAiChatModel model){
                return ChatClient.builder(model)
                        .defaultSystem(DISTINGUISH_WORD_PROMPT)
                        .defaultAdvisors()
                        .build();
        }

        @Bean("testWordChatClient")
        public ChatClient testWordChatClient(OpenAiChatModel model, NeedTools needTools){
                return ChatClient.builder(model)
                        .defaultSystem(PREDICT_WORD_PROMPT)
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .defaultTools(needTools)
                        .build();
        }

        @Bean("chatClient")
        public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory) {
                return ChatClient
                                .builder(model)
                                .defaultSystem(PSYCHOLOGIST_CHAT_PROMPT)
                                .defaultAdvisors(
                                                new SimpleLoggerAdvisor(),
                                                new MessageChatMemoryAdvisor(chatMemory)

                                )
                                .build();
        }

        @Bean("summaryClient")
        public ChatClient summaryClient(OpenAiChatModel model, ChatMemory chatMemory) {
                return ChatClient
                                .builder(model)
                                .defaultSystem(PSYCHOLOGIST_PROMPT)
                                .defaultAdvisors(
                                                new SimpleLoggerAdvisor(),
                                                new MessageChatMemoryAdvisor(chatMemory)

                                )
                                .build();
        }

//        @Bean
//        public OpenAiImageModel openAiImageModelCustom(@Value("${spring.ai.openai.api-key}") String apiKey, @Value("${spring.ai.openai.base-url}") String baseUrl) {
//                //设置超时时间为5分钟
//                int timeOut = 5*60*1000;
//                HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
//                clientHttpRequestFactory.setReadTimeout(timeOut);
//                return new OpenAiImageModel(new OpenAiImageApi(baseUrl, apiKey, RestClient.builder().requestFactory(clientHttpRequestFactory)));
//        }
        @Bean("convertClient")
        public ChatClient convertClient(OpenAiChatModel model, ChatMemory chatMemory) {
                return ChatClient
                                .builder(model)
                                .defaultSystem(PSYCHOLOGY_CONVERT_PROMPT)
                                .defaultAdvisors(
                                                new SimpleLoggerAdvisor(),
                                                new MessageChatMemoryAdvisor(chatMemory)

                                )
                                .build();
        }

        @Bean("storyClient")
        public ChatClient storyClient(OpenAiChatModel model, ChatMemory chatMemory) {
                return ChatClient
                                .builder(model)
                                .defaultSystem(PSYCHOLOGIST_STORY_PROMPT)
                                .defaultAdvisors(
                                                new SimpleLoggerAdvisor(),
                                                new MessageChatMemoryAdvisor(chatMemory)

                                )
                                .build();
        }

        @Bean("pdfChatClient")
        public ChatClient pdfChatClient(OpenAiChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
                return ChatClient
                                .builder(model)
                                .defaultSystem(PSYCHOLOGIST_STORY_PROMPT)
                                .defaultAdvisors(
                                                new SimpleLoggerAdvisor(),
                                                new MessageChatMemoryAdvisor(chatMemory),
                                                new QuestionAnswerAdvisor(
                                                                vectorStore,
                                                                SearchRequest.builder()
                                                                                .similarityThreshold(0.6)
                                                                                .topK(2)
                                                                                .build()))
                                .build();
        }

        /**
         * 图片内容审核客户端
         * 用于审核用户上传的图片是否包含违规内容
         * 
         * @param model ChatModel实例
         * @return 审核专用的ChatClient
         */
        @Bean("moderationClient")
        public ChatClient moderationClient(OpenAiChatModel model) {
                return ChatClient
                                .builder(model)
                                .defaultSystem(IMAGE_MODERATION_PROMPT)
                                .defaultAdvisors(
                                                new SimpleLoggerAdvisor()
                                // 审核不需要记忆功能，每次都是独立的审核任务
                                )
                                .build();
        }

        @Bean
        public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
                return new AliOssUtil(
                                aliOssProperties.getEndpoint(),
                                aliOssProperties.getAccessKeyId(),
                                aliOssProperties.getAccessKeySecret(),
                                aliOssProperties.getBucketName());
        }
}
