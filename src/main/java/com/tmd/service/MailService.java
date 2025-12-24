package com.tmd.service;

import com.tmd.entity.dto.*;
import org.springframework.stereotype.Service;

@Service
public interface MailService {
    MailVO getMailById(Integer mailId);

    ScrollResult getAllMails(MailPackage mailPackage);

    PageResult getSelfMails(Integer page, Integer size, String status);

    void sendMail(MailDTO mailDTO);

    Result comment(Long mailId, MailDTO mailDTO,Boolean isFirst);

    PageResult getReceivedMails(Integer page, Integer size, String status);

    PageResult getSelfCommentMails(Integer page, Integer size);

    Result agentInsight(Long mailId);

    Result agentSuggest(Long mailId, Integer count, String style);
}
