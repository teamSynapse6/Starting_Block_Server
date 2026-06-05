package com.startingblock.domain.notification.application;

import com.startingblock.domain.notification.dto.FcmTokenRegisterReq;
import com.startingblock.domain.notification.dto.NotificationSendReq;
import com.startingblock.domain.notification.dto.NotificationSendRes;
import com.startingblock.domain.notification.infrastructure.FcmTokenRepository;
import com.startingblock.domain.user.domain.Role;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.error.DefaultException;
import com.startingblock.global.infrastructure.airag.LlmConversationQueryRepository;
import com.startingblock.global.infrastructure.firebase.FirebasePushClient;
import com.startingblock.global.payload.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final int PREVIEW_MAX_LENGTH = 120;

    private final FcmTokenRepository fcmTokenRepository;
    private final FirebasePushClient firebasePushClient;
    private final LlmConversationQueryRepository llmConversationQueryRepository;

    @Transactional
    public void registerToken(final Long userId, final FcmTokenRegisterReq request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER);
        }
        fcmTokenRepository.upsert(userId, request.token().trim(), normalize(request.platform()), normalize(request.deviceId()));
    }

    @Transactional
    public void deleteToken(final Long userId, final String token) {
        if (token == null || token.isBlank()) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER);
        }
        fcmTokenRepository.deactivate(userId, token.trim());
    }

    public NotificationSendRes sendManual(final UserPrincipal currentUser, final NotificationSendReq request) {
        requireAdmin(currentUser);
        if (request == null || request.userId() == null || isBlank(request.title()) || isBlank(request.body())) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER);
        }
        return sendToUser(request.userId(), request.title(), request.body(), request.data());
    }

    @Async
    public void sendLlmComplete(final String threadId) {
        try {
            llmConversationQueryRepository.findCompletionNotification(threadId).ifPresent(info -> {
                String preview = truncate(info.preview(), PREVIEW_MAX_LENGTH);
                String body = !isBlank(preview) ? preview : nullToDefault(info.title(), "답변이 준비되었습니다.");
                Map<String, String> data = new LinkedHashMap<>();
                data.put("type", "llm_complete");
                data.put("thread_id", info.threadId());
                data.put("announcement_id", info.announcementId() == null ? "" : String.valueOf(info.announcementId()));
                data.put("title", nullToDefault(info.title(), ""));
                data.put("preview", preview);

                NotificationSendRes result = sendToUser(info.userId(), "AI 공고 분석이 완료됐어요", body, data);
                log.info("LLM completion notification sent thread_id={} user_id={} requested={} success={} failure={} skipped={}",
                        threadId, info.userId(), result.requestedTokenCount(), result.successCount(), result.failureCount(), result.skipped());
            });
        } catch (Exception exception) {
            log.warn("LLM completion notification failed thread_id={}", threadId, exception);
        }
    }

    public NotificationSendRes sendToUser(final Long userId, final String title, final String body, final Map<String, String> data) {
        List<FcmTokenRepository.FcmTokenRow> tokenRows = fcmTokenRepository.findActiveTokensByUserId(userId);
        List<String> tokens = tokenRows.stream().map(FcmTokenRepository.FcmTokenRow::token).toList();
        FirebasePushClient.FcmSendResult result = firebasePushClient.sendToTokens(tokens, title, body, data);
        return new NotificationSendRes(
                result.requestedTokenCount(),
                result.successCount(),
                result.failureCount(),
                result.skipped()
        );
    }

    private void requireAdmin(final UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
        }
        boolean admin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> Role.ADMIN.getValue().equals(authority.getAuthority()));
        if (!admin) {
            throw new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
        }
    }

    private String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(final String value, final int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String nullToDefault(final String value, final String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
