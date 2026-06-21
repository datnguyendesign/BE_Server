package com.example.dacn2_beserver.service.ai;

import com.example.dacn2_beserver.dto.ai.AiChatRequest;
import com.example.dacn2_beserver.dto.ai.AiChatResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiChatClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient aiRestClient;

    @Value("${ai.chat.path}")
    private String chatPath;

    @Value("${ai.internal-token:}")
    private String internalToken;

    // A: 2 attempts total = 1 retry
    @Value("${ai.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${ai.retry.backoff-ms:200}")
    private long backoffMs;

    public AiChatResponse chat(AiChatRequest req) {
        return withRetry(() -> {
            var spec = aiRestClient.post()
                    .uri(chatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);

            if (internalToken != null && !internalToken.isBlank()) {
                spec = spec.header("X-Internal-Token", internalToken);
            }

            Map<String, Object> raw = spec.body(req).retrieve().body(MAP_TYPE);
            return normalize(raw);
        }, "AI chat");
    }

    private <T> T withRetry(ThrowingSupplier<T> call, String label) {
        int attempts = Math.max(1, maxAttempts);
        RuntimeException last = null;

        for (int i = 1; i <= attempts; i++) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                last = e;
            } catch (RestClientResponseException e) {
                // Retry only on 5xx
                if (e.getStatusCode().is5xxServerError()) {
                    last = e;
                } else {
                    throw new ApiException(ErrorCode.AI_SERVICE_ERROR, label + " failed: " + e.getMessage());
                }
            } catch (Exception e) {
                throw new ApiException(ErrorCode.AI_SERVICE_ERROR, label + " failed: " + e.getMessage());
            }

            if (i < attempts) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw new ApiException(ErrorCode.AI_SERVICE_ERROR, label + " failed after retries: " + (last != null ? last.getMessage() : "unknown"));
    }

    @SuppressWarnings("unchecked")
    private AiChatResponse normalize(Map<String, Object> raw) {
        if (raw == null) return AiChatResponse.builder().build();

        Object dataObj = raw.get("data");
        Map<String, Object> data = (dataObj instanceof Map<?, ?>) ? (Map<String, Object>) dataObj : raw;

        String content = firstString(data, "text_response", "content", "text", "answer", "message");

        List<String> suggested = new ArrayList<>();
        Object actionsObj = data.get("suggested_actions");
        if (actionsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    if (!s.isBlank()) suggested.add(s);
                } else if (item instanceof Map<?, ?> m) {
                    String label = firstString((Map<String, Object>) m, "label", "text", "title", "action");
                    if (label != null && !label.isBlank()) suggested.add(label);
                }
            }
        }

        return AiChatResponse.builder()
                .content(content)
                .suggestedActions(suggested.isEmpty() ? null : suggested)
                .meta(raw)
                .build();
    }

    private String firstString(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}