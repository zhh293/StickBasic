package com.tmd;

import com.tmd.config.L2CacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(L2CacheProperties.class)
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling
public class BasicBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicBackApplication.class, args);
    }

}
