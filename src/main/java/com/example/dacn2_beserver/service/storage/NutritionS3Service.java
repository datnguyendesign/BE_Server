package com.example.dacn2_beserver.service.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NutritionS3Service {

    private static final Set<String> ALLOWED_CT = Set.of("image/jpeg", "image/png", "image/webp");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final S3Presigner presigner;
    private final S3Client s3Client;

    @Value("${aws.s3.nutrition.bucket}")
    private String bucket;

    @Value("${aws.s3.nutrition.prefix}")
    private String prefix;

    @Value("${aws.s3.presign.put-ttl-seconds}")
    private long putTtlSeconds;

    @Value("${aws.s3.presign.get-ttl-seconds}")
    private long getTtlSeconds;

    @Value("${aws.s3.nutrition.max-bytes}")
    private long maxBytes;

    private static String normalizePrefix(String p) {
        if (p == null || p.isBlank()) return "";
        return p.endsWith("/") ? p : (p + "/");
    }

    public PresignPutResult presignPut(String userId, String contentType, long sizeBytes) {
        requireConfigured();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        if (!ALLOWED_CT.contains(contentType)) throw new IllegalArgumentException("Unsupported contentType");
        if (sizeBytes <= 0 || sizeBytes > maxBytes) throw new IllegalArgumentException("Invalid file size");

        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };

        // Key format: nutrition/{userId}/{yyyyMMddHH}/{uuid}.ext
        String hour = LocalDateTime.now().format(HOUR_FMT);
        String objectKey = normalizePrefix(prefix) + userId + "/" + hour + "/" + UUID.randomUUID() + "." + ext;

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(putTtlSeconds))
                .putObjectRequest(putReq)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignReq);

        return new PresignPutResult(objectKey, presigned.url().toString(), Instant.now().plusSeconds(putTtlSeconds));
    }

    public String presignGetUrl(String objectKey) {
        requireConfigured();
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(getTtlSeconds))
                .getObjectRequest(getReq)
                .build();

        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignReq);
        return presigned.url().toString();
    }

    public void deleteObjectBestEffort(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (Exception ignored) {
            // best-effort. (Bước sau có thể thêm pending_deletes nếu bạn muốn)

        }
    }

    public void assertOwnedByUser(String userId, String objectKey) {
        String expectedPrefix = normalizePrefix(prefix) + userId + "/";
        if (objectKey == null || !objectKey.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("objectKey is invalid or not owned by user");
        }
    }

    private void requireConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS_S3_NUTRITION_BUCKET is not configured");
        }
    }

    public record PresignPutResult(String objectKey, String uploadUrl, Instant expiresAt) {
    }
}