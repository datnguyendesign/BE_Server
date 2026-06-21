package com.example.dacn2_beserver.service.ai;

import com.example.dacn2_beserver.dto.ai.AiFoodPredictRequest;
import com.example.dacn2_beserver.dto.ai.AiFoodPredictResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class AiFoodClient {

    private final RestClient aiRestClient;

    @Value("${ai.food.predict-path}")
    private String predictPath;

    @Value("${ai.internal-token:}")
    private String internalToken;

    @Value("${ai.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${ai.retry.backoff-ms:200}")
    private long backoffMs;

    public AiFoodPredictResponse predictFoodByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "image_url is required");
        }

        AiFoodPredictRequest req = AiFoodPredictRequest.builder()
                .imageUrl(imageUrl)
                .build();

        return withRetry(() -> {
            var spec = aiRestClient.post()
                    .uri(predictPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);

            if (internalToken != null && !internalToken.isBlank()) {
                spec = spec.header("X-Internal-Token", internalToken);
            }

            return spec.body(req)
                    .retrieve()
                    .body(AiFoodPredictResponse.class);
        }, "AI food predict");
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

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}