package com.tmd.service;

import com.tmd.entity.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public interface MailService {
    MailVO getMailById(Integer mailId);

    ScrollResult getAllMails(MailPackage mailPackage);

    PageResult getSelfMails(Integer page, Integer size, String status);

    void sendMail(MailDTO mailDTO);

    void comment(Integer mailId, MailDTO mailDTO);

    PageResult getReceivedMails(Integer page, Integer size, String status);
}
