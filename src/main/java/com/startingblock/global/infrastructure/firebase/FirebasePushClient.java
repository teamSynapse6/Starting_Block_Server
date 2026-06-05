package com.startingblock.global.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FirebasePushClient {

    @Value("${notification.firebase.enabled:false}")
    private boolean enabled;

    @Value("${notification.firebase.project-id:}")
    private String projectId;

    @Value("${notification.firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${notification.firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${notification.firebase.android-channel-id:fcm_default}")
    private String androidChannelId;

    @Getter
    private boolean ready;

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("Firebase FCM is disabled");
            return;
        }

        try (InputStream credentials = openCredentials()) {
            if (credentials == null) {
                log.warn("Firebase FCM is enabled but no service account was provided");
                return;
            }

            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials));
            if (StringUtils.hasText(projectId)) {
                builder.setProjectId(projectId);
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(builder.build());
            }
            ready = true;
            log.info("Firebase FCM initialized project_id={}", StringUtils.hasText(projectId) ? projectId : "(from credentials)");
        } catch (Exception exception) {
            ready = false;
            log.warn("Firebase FCM initialization failed; push notifications will be skipped", exception);
        }
    }

    public FcmSendResult sendToTokens(
            final List<String> tokens,
            final String title,
            final String body,
            final Map<String, String> data
    ) {
        if (tokens == null || tokens.isEmpty()) {
            return FcmSendResult.skipped(0);
        }
        if (!ready) {
            log.info("Firebase FCM skipped because client is not ready token_count={}", tokens.size());
            return FcmSendResult.skipped(tokens.size());
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data == null ? Map.of() : data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(androidChannelId)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-push-type", "alert")
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Firebase FCM send result token_count={} success={} failure={}",
                    tokens.size(), response.getSuccessCount(), response.getFailureCount());
            return FcmSendResult.sent(tokens.size(), response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException exception) {
            log.warn("Firebase FCM send failed token_count={}", tokens.size(), exception);
            return FcmSendResult.sent(tokens.size(), 0, tokens.size());
        }
    }

    private InputStream openCredentials() throws IOException {
        if (StringUtils.hasText(serviceAccountJson)) {
            String normalized = serviceAccountJson.replace("\\n", "\n");
            return new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(serviceAccountPath)) {
            Path configuredPath = Path.of(serviceAccountPath);
            if (Files.isRegularFile(configuredPath)) {
                return new FileInputStream(configuredPath.toFile());
            }

            Path mountedPath = Path.of("/app/secrets", configuredPath.getFileName().toString());
            if (Files.isRegularFile(mountedPath)) {
                return new FileInputStream(mountedPath.toFile());
            }

            return new FileInputStream(configuredPath.toFile());
        }
        return null;
    }

    public record FcmSendResult(int requestedTokenCount, int successCount, int failureCount, boolean skipped) {
        public static FcmSendResult skipped(final int requestedTokenCount) {
            return new FcmSendResult(requestedTokenCount, 0, 0, true);
        }

        public static FcmSendResult sent(final int requestedTokenCount, final int successCount, final int failureCount) {
            return new FcmSendResult(requestedTokenCount, successCount, failureCount, false);
        }
    }
}
