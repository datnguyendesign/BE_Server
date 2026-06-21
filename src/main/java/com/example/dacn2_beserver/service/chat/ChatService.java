package com.example.dacn2_beserver.service.chat;

import com.example.dacn2_beserver.dto.ai.*;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import com.example.dacn2_beserver.model.ai.ChatMessage;
import com.example.dacn2_beserver.model.ai.ChatSession;
import com.example.dacn2_beserver.model.enums.ChatRole;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.repository.ChatMessageRepository;
import com.example.dacn2_beserver.repository.ChatSessionRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import com.example.dacn2_beserver.service.ai.AiChatClient;
import com.example.dacn2_beserver.service.storage.ChatMediaS3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String DEFAULT_IMAGE_ONLY_MESSAGE = "Analyze this image";

    // Fallback content (A)
    private static final String AI_FALLBACK_MESSAGE =
            "Sorry, I'm having trouble right now. Please try again in a moment.";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    private final ChatMediaS3Service chatMediaS3Service;
    private final AiChatClient aiChatClient;

    private final UserRepository userRepository;

    public ChatSessionResponse createSession(String userId, CreateChatSessionRequest req) {
        String title = (req != null && req.getTitle() != null) ? req.getTitle().trim() : null;
        if (title == null || title.isBlank()) title = "New chat";

        ChatSession s = ChatSession.builder()
                .userId(userId)
                .title(title)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .contextSnapshot(req != null ? req.getContextSnapshot() : null)
                .deletedAt(null)
                .build();

        s = chatSessionRepository.save(s);
        return toSessionResponse(s);
    }

    public List<ChatSessionResponse> listSessions(String userId) {
        return chatSessionRepository.findTop50ByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)
                .stream().map(this::toSessionResponse).toList();
    }

    // Pagination (A): before + limit
    public List<ChatMessageResponse> listMessages(String userId, String sessionId, Instant before, int limit) {
        ChatSession s = chatSessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Session not found"));

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Instant cursor = (before != null) ? before : Instant.now().plusSeconds(1);

        // Fetch DESC (newest -> oldest), then reverse to ASC for FE rendering.
        List<ChatMessage> desc = chatMessageRepository.findBySessionIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                s.getId(),
                cursor,
                PageRequest.of(0, safeLimit)
        );

        Collections.reverse(desc);
        return desc.stream().map(this::toMessageResponse).toList();
    }

    public SendChatMessageResponse sendMessage(String userId, String sessionId, SendChatMessageRequest req) {
        ChatSession session = chatSessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Session not found"));

        String content = req != null ? req.getContent() : null;
        String imageObjectKey = req != null ? req.getImageObjectKey() : null;

        boolean hasText = content != null && !content.trim().isBlank();
        boolean hasImage = imageObjectKey != null && !imageObjectKey.trim().isBlank();

        if (!hasText && !hasImage) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Either content or imageObjectKey is required");
        }

        Map<String, Object> userMeta = new HashMap<>();
        if (req != null && req.getMeta() != null) userMeta.putAll(req.getMeta());

        // For storage: keep only objectKey (do NOT store URL).
        // For AI fetch: use presigned GET URL.
        String aiImageUrl = null;
        if (hasImage) {
            chatMediaS3Service.assertOwnedByUser(userId, imageObjectKey);
            aiImageUrl = chatMediaS3Service.presignGetUrl(imageObjectKey);

            userMeta.put("image", Map.of("objectKey", imageObjectKey));
        }

        ChatMessage userMsg = ChatMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role(ChatRole.USER)
                .content(hasText ? content.trim() : null)
                .meta(userMeta.isEmpty() ? null : userMeta)
                .createdAt(Instant.now())
                .build();
        userMsg = chatMessageRepository.save(userMsg);

        Map<String, Object> userContext = buildUserContextForAi(userId);
        String finalMessage = hasText ? content.trim() : DEFAULT_IMAGE_ONLY_MESSAGE;

        AiChatRequest aiReq = AiChatRequest.builder()
                .sessionId(session.getId())
                .message(finalMessage)
                .imageUrl(aiImageUrl)
                .userContext(userContext)
                .build();

        AiChatResponse aiRes = null;
        Map<String, Object> assistantMeta = new HashMap<>();

        try {
            aiRes = aiChatClient.chat(aiReq);
        } catch (ApiException e) {
            // Persist fallback assistant message even if AI fails.
            if (e.getErrorCode() == ErrorCode.AI_SERVICE_ERROR) {
                assistantMeta.put("ai_error", Map.of(
                        "code", e.getErrorCode().getCode(),
                        "message", e.getMessage()
                ));
                assistantMeta.put("suggested_actions", List.of("Try again"));
                aiRes = AiChatResponse.builder()
                        .content(AI_FALLBACK_MESSAGE)
                        .suggestedActions(List.of("Try again"))
                        .meta(Map.of("fallback", true))
                        .build();
            } else {
                throw e;
            }
        }

        if (aiRes != null && aiRes.getSuggestedActions() != null) {
            assistantMeta.put("suggested_actions", aiRes.getSuggestedActions());
        }
        if (aiRes != null && aiRes.getMeta() != null) {
            assistantMeta.put("ai_meta", aiRes.getMeta());
        }

        ChatMessage assistantMsg = ChatMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role(ChatRole.ASSISTANT)
                .content(aiRes != null ? aiRes.getContent() : null)
                .meta(assistantMeta.isEmpty() ? null : assistantMeta)
                .createdAt(Instant.now())
                .build();
        assistantMsg = chatMessageRepository.save(assistantMsg);

        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);

        return SendChatMessageResponse.builder()
                .userMessage(toMessageResponse(userMsg))
                .assistantMessage(toMessageResponse(assistantMsg))
                .build();
    }

    // Soft delete (A)
    public void deleteSession(String userId, String sessionId) {
        ChatSession s = chatSessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Session not found"));

        s.setDeletedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        chatSessionRepository.save(s);
    }

    public ChatSessionResponse updateTitle(String userId, String sessionId, UpdateChatSessionRequest req) {
        ChatSession s = chatSessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Session not found"));

        String title = (req != null) ? req.getTitle() : null;
        if (title != null) {
            String t = title.trim();
            if (t.isBlank()) throw new ApiException(ErrorCode.VALIDATION_ERROR, "title must not be blank");
            s.setTitle(t);
            s.setUpdatedAt(Instant.now());
            s = chatSessionRepository.save(s);
        }

        return toSessionResponse(s);
    }

    private Map<String, Object> buildUserContextForAi(String userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("user_id", userId);

        if (u.getProfile() != null) {
            Integer age = computeAge(u.getProfile().getBirthday(), safeZoneId(u));
            if (age != null) ctx.put("age", age);

            if (u.getProfile().getGender() != null) {
                ctx.put("gender", u.getProfile().getGender().name().toLowerCase());
            }
            if (u.getProfile().getHeightCm() != null) {
                ctx.put("height", u.getProfile().getHeightCm());
            }
            if (u.getProfile().getWeightKg() != null) {
                ctx.put("weight", u.getProfile().getWeightKg());
            }
        }

        ctx.put("medical_conditions", List.of());
        return ctx;
    }

    private Integer computeAge(Date birthday, ZoneId zoneId) {
        if (birthday == null) return null;
        LocalDate birthDate = birthday.toInstant().atZone(zoneId).toLocalDate();
        LocalDate now = LocalDate.now(zoneId);
        int years = Period.between(birthDate, now).getYears();
        return years < 0 ? null : years;
    }

    private ZoneId safeZoneId(User u) {
        try {
            if (u.getSettings() != null && u.getSettings().getTimezone() != null) {
                return ZoneId.of(u.getSettings().getTimezone());
            }
        } catch (Exception ignored) {
        }
        return ZoneId.of("UTC");
    }

    private ChatSessionResponse toSessionResponse(ChatSession s) {
        return ChatSessionResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .contextSnapshot(s.getContextSnapshot())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage m) {
        List<String> suggested = null;

        if (m.getRole() == ChatRole.ASSISTANT && m.getMeta() != null) {
            Object v = m.getMeta().get("suggested_actions");
            if (v instanceof List<?> list) {
                suggested = list.stream()
                        .filter(it -> it instanceof String)
                        .map(it -> (String) it)
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
                if (suggested.isEmpty()) suggested = null;
            }
        }

        return ChatMessageResponse.builder()
                .id(m.getId())
                .sessionId(m.getSessionId())
                .userId(m.getUserId())
                .role(m.getRole())
                .content(m.getContent())
                .suggestedActions(suggested)
                .meta(m.getMeta())
                .createdAt(m.getCreatedAt())
                .build();
    }
}