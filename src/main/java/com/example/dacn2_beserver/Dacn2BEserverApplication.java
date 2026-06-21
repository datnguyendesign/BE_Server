package com.example.dacn2_beserver;

import com.example.dacn2_beserver.config.RateLimitProperties;

import jakarta.annotation.PostConstruct;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties.class)
public class Dacn2BEserverApplication {

    @PostConstruct
    public void init() {
        // Ép toàn bộ ứng dụng chạy theo múi giờ UTC để khớp hoàn toàn với MongoDB
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(Dacn2BEserverApplication.class, args);
    }
}