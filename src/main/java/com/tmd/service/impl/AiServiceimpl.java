package com.tmd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.tmd.entity.po.PStick;
import com.tmd.mapper.AiMapper;
import com.tmd.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

    @Override
    public String extractMailInsights(String context) {
        String prompt = "请阅读以下邮件线程内容，输出一个JSON，包含字段：" +
                "intent(字符串)，entities(数组)，deadlines(数组)，actions(数组[{title,owner,deadline,priority}])，tone(字符串)。\\n内容：\\n" + context;
        String resp = runWithTimeoutAndRetry(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content(), 3, 10_000L, 1000L);
        return normalizeInsightsJson(resp);
    }

    @Override
    public java.util.List<String> generateReplySuggestions(String context, int count, String style) {
        String s = style == null ? "专业且礼貌" : style;
        String prompt = "基于以下邮件线程，生成" + count + "条" + s + "风格的中文回复建议。" +
                "只返回JSON数组，每个元素为一条完整回复字符串。\n内容：\n" + context;
        String resp = runWithTimeoutAndRetry(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content(), 3, 10_000L, 1000L);
        java.util.List<String> list;
        try {
            list = com.alibaba.fastjson2.JSON.parseArray(resp, String.class);
        } catch (Exception e) {
            list = java.util.List.of(resp);
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s1 : list) {
            if (s1 == null) continue;
            String t = s1.replaceAll("\r|\n", " ").trim();
            if (t.isEmpty()) continue;
            if (t.length() > 500) t = t.substring(0, 500);
            out.add(t);
            if (out.size() >= count) break;
        }
        if (out.isEmpty()) out = java.util.List.of("好的，已收到。");
        return out;
    }

    private <T> T runWithTimeoutAndRetry(java.util.concurrent.Callable<T> callable, int maxAttempts, long timeoutMs, long backoffMs) {
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(callable);
            Thread t = new Thread(task, "ai-call-" + attempts);
            t.setDaemon(true);
            t.start();
            try {
                return task.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
            } finally {
                t.interrupt();
            }
        }
        throw new RuntimeException("AI call timeout after retries");
    }

    private String normalizeInsightsJson(String insights) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("intent", "");
        result.put("entities", new java.util.ArrayList<>());
        result.put("deadlines", new java.util.ArrayList<>());
        result.put("actions", new java.util.ArrayList<>());
        result.put("tone", "");
        try {
            JSONObject obj = JSON.parseObject(insights);
            Object intent = obj.get("intent");
            Object entities = obj.get("entities");
            Object deadlines = obj.get("deadlines");
            Object actions = obj.get("actions");
            Object tone = obj.get("tone");
            if (intent instanceof CharSequence) result.put("intent", intent.toString());
            if (tone instanceof CharSequence) result.put("tone", tone.toString());
            if (entities instanceof java.util.List) result.put("entities", entities);
            if (deadlines instanceof java.util.List) result.put("deadlines", deadlines);
            if (actions instanceof java.util.List) result.put("actions", actions);
        } catch (Exception e) {
            result.put("raw", insights);
        }
        return JSONUtil.toJsonStr(result);
    }
}
