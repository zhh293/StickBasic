package com.tmd.WebSocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/websocket/{userId}")
@Slf4j
public class WebSocketServer {

    // 存储所有连接的客户端
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        sessions.put(userId, session);
        log.info("WebSocket连接已打开，用户ID: {}", userId);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") String userId) {
        log.info("收到用户 {} 的消息：{}", userId, message);
        // 可以在这里处理收到的消息
    }

    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        sessions.remove(userId);
        log.info("WebSocket连接已关闭，用户ID: {}", userId);
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("userId") String userId) {
        sessions.remove(userId);
        log.error("WebSocket发生错误，用户ID: {}，错误信息：{}", userId, error.getMessage());
    }

    /**
     * 向指定用户发送消息
     * 
     * @param userId  用户ID
     * @param message 消息内容
     */
    public void sendToUser(String userId, String message) {
        Session session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.info("向用户 {} 发送消息成功", userId);
            } catch (Exception e) {
                log.error("向用户 {} 发送消息失败", userId, e);
            }
        } else {
            log.warn("用户 {} 的WebSocket连接不存在或已关闭", userId);
        }
    }

    public boolean Open(String userId) {
        Session session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 广播消息给所有连接的用户
     * 
     * @param message 消息内容
     */
    public void broadcast(String message) {
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            String userId = entry.getKey();
            Session session = entry.getValue();
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (Exception e) {
                    log.error("向用户 {} 广播消息失败", userId, e);
                }
            }
        }
    }

    /**
     * 获取当前在线用户数
     * 
     * @return 在线用户数
     */
    public static int getOnlineCount() {
        return sessions.size();
    }
}
