package com.example.dacn2_beserver.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiRestClientConfig {

    @Value("${ai.base-url}")
    private String aiBaseUrl;

    @Value("${ai.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${ai.read-timeout-ms}")
    private int readTimeoutMs;


    @Bean
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        ClientHttpRequestInterceptor requestIdInterceptor = (request, body, execution) -> {
            // Propagate request id to AI server if available.
            String rid = MDC.get("requestId");
            if (rid != null && !rid.isBlank() && !request.getHeaders().containsKey("X-Request-Id")) {
                request.getHeaders().add("X-Request-Id", rid);
            }
            return execution.execute(request, body);
        };

        return RestClient.builder()
                .baseUrl(aiBaseUrl)
                .requestFactory(factory)
                .requestInterceptor(requestIdInterceptor)
                .build();
    }
}