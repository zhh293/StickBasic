package com.tmd.service;

import org.springframework.stereotype.Service;

@Service
public interface AiService {
    void induction(String prompt, Long uid, Long sid);

    String extractMailInsights(String context);

    java.util.List<String> generateReplySuggestions(String context, int count, String style);
}
