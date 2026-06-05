package com.startingblock.global.infrastructure.airag;

public record LlmCompletionNotificationInfo(
        String threadId,
        Long userId,
        Long announcementId,
        String title,
        String preview
) {
}
