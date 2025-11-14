package com.tmd.service;

import com.tmd.entity.dto.Result;

public interface PrivateChatService {
    Result send(Long toUserId, String content);

    Result pullOffline(Integer page, Integer size);

    Result history(Long otherUserId,
                   Integer size,
                   Long max,
                   Integer offset);

    Result markRead(Long messageId);

    Result recall(Long messageId);

    Result ack(Long messageId);
}