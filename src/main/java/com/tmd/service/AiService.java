package com.tmd.service;

import org.springframework.stereotype.Service;

@Service
public interface AiService {
    void induction(String prompt, Long uid, Long sid);
}
