package com.example.dacn2_beserver.controller;

import com.example.dacn2_beserver.config.RateLimitProperties;
import com.example.dacn2_beserver.dto.ai.*;
import com.example.dacn2_beserver.dto.common.ApiResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.exception.ErrorCode;
import com.example.dacn2_beserver.security.AuthPrincipal;
import com.example.dacn2_beserver.service.chat.ChatService;
import com.example.dacn2_beserver.service.ratelimit.RedisRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final RedisRateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProps;

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) CreateChatSessionRequest req
    ) {
        return ApiResponse.ok(chatService.createSession(principal.userId(), req));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ApiResponse.ok(chatService.listSessions(principal.userId()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String sessionId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") int limit
    ) {
        Instant cursor = parseBefore(before);
        return ApiResponse.ok(chatService.listMessages(principal.userId(), sessionId, cursor, limit));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SendChatMessageResponse> sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String sessionId,
            @RequestBody SendChatMessageRequest req
    ) {
        rateLimitService.checkOrThrow(
                "rl:chat:msg:" + principal.userId() + ":" + sessionId,
                rateLimitProps.getChat().getSend().getLimit(),
                rateLimitProps.getChat().getSend().getWindowSeconds()
        );
        return ApiResponse.ok(chatService.sendMessage(principal.userId(), sessionId, req));
    }

    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionResponse> updateSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String sessionId,
            @RequestBody UpdateChatSessionRequest req
    ) {
        return ApiResponse.ok(chatService.updateTitle(principal.userId(), sessionId, req));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String sessionId
    ) {
        chatService.deleteSession(principal.userId(), sessionId);
        return ApiResponse.ok(null);
    }

    private Instant parseBefore(String before) {
        if (before == null || before.isBlank()) return null;
        try {
            return Instant.parse(before.trim());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "before must be ISO-8601 instant, e.g. 2025-12-26T10:00:00Z");
        }
    }
}