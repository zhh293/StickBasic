package com.tmd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.tmd.entity.po.PStick;
import com.tmd.entity.po.PStickQueryParam;
import com.tmd.mapper.AiMapper;
import com.tmd.service.AiService;
import jdk.jfr.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/21
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiServiceimpl implements AiService {
    private final ChatClient chatClient;
    @Autowired
    private AiMapper aiMapper;
    @Override
    public void induction(String modelResponse, Long uid, Long sid) {
        log.info(modelResponse);
        JSONArray rows = JSON.parseArray(modelResponse);
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                JSONObject r = rows.getJSONObject(i);
                PStick pStick = new PStick();
                pStick.setUserId(uid);
                pStick.setStickId(sid);
                pStick.setName(r.getString("name"));
                pStick.setCreatedAt(LocalDateTime.now().toLocalDate());
                pStick.setContent(r.getString("content"));
                pStick.setSpirits(r.getIntValue("spirits"));
                aiMapper.insert(pStick);
            }
        }
    }
}
