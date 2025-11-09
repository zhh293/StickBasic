package com.tmd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "l2cache")
@Component
public class L2CacheProperties {
    /**
     * 缓存配置
     */
    private L2CacheConfig config;

    public L2CacheConfig getConfig() {
        return config;
    }

    public void setConfig(L2CacheConfig config) {
        this.config = config;
    }
}
