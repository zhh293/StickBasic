package com.tmd.service;

import com.tmd.entity.dto.Result;

public interface SearchService {
    Result search(String keyword,
                  String type,
                  Integer page,
                  Integer size);
}
