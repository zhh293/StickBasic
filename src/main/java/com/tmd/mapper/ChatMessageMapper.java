package com.tmd.mapper;

import com.tmd.entity.dto.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {
    int insert(ChatMessage message);

    List<ChatMessage> selectConversation(@Param("a") Long a,
                                         @Param("b") Long b,
                                         @Param("offset") Integer offset,
                                         @Param("size") Integer size);

    ChatMessage selectById(@Param("id") Long id);

    int markRead(@Param("id") Long id, @Param("readAt") java.time.LocalDateTime readAt);

    int markRecalled(@Param("id") Long id, @Param("recalledAt") java.time.LocalDateTime recalledAt);
}