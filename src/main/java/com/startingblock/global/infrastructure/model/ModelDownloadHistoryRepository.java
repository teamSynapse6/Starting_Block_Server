package com.startingblock.global.infrastructure.model;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ModelDownloadHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @PostConstruct
    public void ensureSchema() {
        if (environment.acceptsProfiles(Profiles.of("openapi"))) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS model_download_histories (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NULL,
                    model_name VARCHAR(255) NOT NULL,
                    size BIGINT NOT NULL,
                    downloaded_at DATETIME NOT NULL,
                    INDEX ix_model_download_histories_user_id (user_id),
                    INDEX ix_model_download_histories_model_name (model_name),
                    INDEX ix_model_download_histories_downloaded_at (downloaded_at),
                    CONSTRAINT fk_model_download_histories_user_id
                        FOREIGN KEY (user_id) REFERENCES `user`(id)
                        ON DELETE SET NULL
                )
                """);
    }

    public void save(final Long userId, final String modelName, final long size) {
        jdbcTemplate.update(
                """
                        INSERT INTO model_download_histories (user_id, model_name, size, downloaded_at)
                        VALUES (?, ?, ?, ?)
                        """,
                userId,
                modelName,
                size,
                LocalDateTime.now()
        );
    }
}
