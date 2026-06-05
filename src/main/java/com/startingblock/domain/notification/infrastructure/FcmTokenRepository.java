package com.startingblock.domain.notification.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FcmTokenRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @PostConstruct
    public void ensureSchema() {
        if (environment.acceptsProfiles(Profiles.of("openapi"))) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fcm_tokens (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    token VARCHAR(512) NOT NULL,
                    platform VARCHAR(32) NULL,
                    device_id VARCHAR(128) NULL,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    last_registered_at DATETIME NOT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    UNIQUE KEY uq_fcm_tokens_token (token),
                    INDEX ix_fcm_tokens_user_active (user_id, is_active),
                    CONSTRAINT fk_fcm_tokens_user_id
                        FOREIGN KEY (user_id) REFERENCES `user`(id)
                        ON DELETE CASCADE
                )
                """);

        ensureForeignKey(
                "fk_fcm_tokens_user_id",
                """
                        ALTER TABLE fcm_tokens
                        ADD CONSTRAINT fk_fcm_tokens_user_id
                            FOREIGN KEY (user_id) REFERENCES `user`(id)
                            ON DELETE CASCADE
                        """
        );
    }

    public void upsert(final Long userId, final String token, final String platform, final String deviceId) {
        jdbcTemplate.update("""
                INSERT INTO fcm_tokens (
                    user_id, token, platform, device_id, is_active,
                    last_registered_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 1, NOW(), NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    platform = VALUES(platform),
                    device_id = VALUES(device_id),
                    is_active = 1,
                    last_registered_at = NOW(),
                    updated_at = NOW()
                """, userId, token, platform, deviceId);
    }

    public void deactivate(final Long userId, final String token) {
        jdbcTemplate.update("""
                UPDATE fcm_tokens
                SET is_active = 0,
                    updated_at = NOW()
                WHERE user_id = ?
                  AND token = ?
                """, userId, token);
    }

    public List<FcmTokenRow> findActiveTokensByUserId(final Long userId) {
        return jdbcTemplate.query("""
                SELECT id, token
                FROM fcm_tokens
                WHERE user_id = ?
                  AND is_active = 1
                """, (rs, rowNum) -> new FcmTokenRow(
                rs.getLong("id"),
                rs.getString("token")
        ), userId);
    }

    public void deactivateByIds(final List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        jdbcTemplate.update(
                "UPDATE fcm_tokens SET is_active = 0, updated_at = NOW() WHERE id IN (" + placeholders + ")",
                ids.toArray()
        );
    }

    private void ensureForeignKey(final String constraintName, final String ddl) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.REFERENTIAL_CONSTRAINTS
                        WHERE CONSTRAINT_SCHEMA = DATABASE()
                          AND CONSTRAINT_NAME = ?
                        """,
                Integer.class,
                constraintName
        );

        if (exists == null || exists == 0) {
            executeIfNotAlreadyExists(ddl);
        }
    }

    private void executeIfNotAlreadyExists(final String ddl) {
        try {
            jdbcTemplate.execute(ddl);
        } catch (DataAccessException exception) {
            String message = exception.getMostSpecificCause().getMessage();
            if (message != null && (
                    message.contains("Duplicate column")
                            || message.contains("Duplicate key name")
                            || message.contains("Duplicate foreign key constraint name")
                            || message.contains("already exists")
                            || message.contains("exists already")
            )) {
                return;
            }
            throw exception;
        }
    }

    public record FcmTokenRow(Long id, String token) {
    }
}
