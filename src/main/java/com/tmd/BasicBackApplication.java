package com.tmd;

import com.tmd.config.L2CacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableConfigurationProperties(L2CacheProperties.class)
@EnableAspectJAutoProxy
public class BasicBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicBackApplication.class, args);
    }

}
