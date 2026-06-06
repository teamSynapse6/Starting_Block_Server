package com.startingblock.global.infrastructure.airag;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LlmConversationQueryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @PostConstruct
    public void ensureSchema() {
        if (environment.acceptsProfiles(Profiles.of("openapi"))) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS llm_threads (
                    thread_id VARCHAR(36) NOT NULL PRIMARY KEY,
                    user_id BIGINT NULL,
                    announcement_id INT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'active',
                    created_at DATETIME NOT NULL,
                    last_activity DATETIME NOT NULL,
                    summary_text TEXT NULL,
                    summary_updated_at DATETIME NULL,
                    archived_at DATETIME NULL,
                    INDEX ix_llm_threads_status_last_activity (status, last_activity),
                    INDEX ix_llm_threads_user_status_last_activity (user_id, status, last_activity),
                    CONSTRAINT fk_llm_threads_user_id
                        FOREIGN KEY (user_id) REFERENCES `user`(id)
                        ON DELETE SET NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS llm_messages (
                    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    thread_id VARCHAR(36) NOT NULL,
                    seq INT NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    content TEXT NOT NULL,
                    compute_type VARCHAR(32) NULL,
                    model_name VARCHAR(128) NULL,
                    created_at DATETIME NOT NULL,
                    INDEX ix_llm_messages_thread_id (thread_id),
                    UNIQUE KEY uq_llm_messages_thread_seq (thread_id, seq),
                    CONSTRAINT fk_llm_messages_thread_id
                        FOREIGN KEY (thread_id) REFERENCES llm_threads(thread_id)
                        ON DELETE CASCADE
                )
                """);

        ensureColumn("llm_threads", "user_id", "ALTER TABLE llm_threads ADD COLUMN user_id BIGINT NULL");
        ensureColumn("llm_threads", "summary_text", "ALTER TABLE llm_threads ADD COLUMN summary_text TEXT NULL");
        ensureColumn("llm_threads", "summary_updated_at", "ALTER TABLE llm_threads ADD COLUMN summary_updated_at DATETIME NULL");
        ensureColumn("llm_messages", "compute_type", "ALTER TABLE llm_messages ADD COLUMN compute_type VARCHAR(32) NULL");
        ensureColumn("llm_messages", "model_name", "ALTER TABLE llm_messages ADD COLUMN model_name VARCHAR(128) NULL");
        ensureIndex(
                "llm_threads",
                "ix_llm_threads_user_status_last_activity",
                "CREATE INDEX ix_llm_threads_user_status_last_activity ON llm_threads (user_id, status, last_activity)"
        );
        ensureForeignKey(
                "fk_llm_threads_user_id",
                """
                        ALTER TABLE llm_threads
                        ADD CONSTRAINT fk_llm_threads_user_id
                            FOREIGN KEY (user_id) REFERENCES `user`(id)
                            ON DELETE SET NULL
                        """
        );
    }

    public List<LlmConversationSummaryRes> findActiveConversations(final Long userId) {
        String sql = """
                SELECT
                    t.thread_id,
                    t.announcement_id,
                    a.title,
                    m.content AS last_message,
                    m.created_at AS last_message_created_at
                FROM llm_threads t
                INNER JOIN `user` u ON u.id = t.user_id
                LEFT JOIN announcement a ON a.id = t.announcement_id
                LEFT JOIN (
                    SELECT lm.thread_id, lm.content, lm.created_at
                    FROM llm_messages lm
                    INNER JOIN (
                        SELECT thread_id, MAX(seq) AS max_seq
                        FROM llm_messages
                        GROUP BY thread_id
                    ) latest ON latest.thread_id = lm.thread_id AND latest.max_seq = lm.seq
                ) m ON m.thread_id = t.thread_id
                WHERE u.id = ?
                  AND t.status = 'active'
                ORDER BY COALESCE(m.created_at, t.last_activity, t.created_at) DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new LlmConversationSummaryRes(
                rs.getString("thread_id"),
                rs.getObject("announcement_id", Long.class),
                rs.getString("title"),
                rs.getString("last_message"),
                toLocalDateTime(rs.getTimestamp("last_message_created_at"))
        ), userId);
    }

    public Optional<String> findLatestActiveThreadId(final Long userId, final Long announcementId) {
        String sql = """
                SELECT t.thread_id
                FROM llm_threads t
                INNER JOIN `user` u ON u.id = t.user_id
                WHERE u.id = ?
                  AND t.announcement_id = ?
                  AND t.status = 'active'
                ORDER BY t.last_activity DESC, t.created_at DESC
                LIMIT 1
                """;

        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("thread_id"), userId, announcementId);
        return rows.stream().findFirst();
    }

    public Optional<LlmCompletionNotificationInfo> findCompletionNotification(final String threadId) {
        String sql = """
                SELECT
                    t.thread_id,
                    t.user_id,
                    t.announcement_id,
                    a.title,
                    COALESCE(am.content, lm.content, '') AS preview
                FROM llm_threads t
                INNER JOIN `user` u ON u.id = t.user_id
                LEFT JOIN announcement a ON a.id = t.announcement_id
                LEFT JOIN (
                    SELECT lm1.thread_id, lm1.content
                    FROM llm_messages lm1
                    INNER JOIN (
                        SELECT thread_id, MAX(seq) AS max_seq
                        FROM llm_messages
                        WHERE role = 'assistant'
                        GROUP BY thread_id
                    ) latest ON latest.thread_id = lm1.thread_id AND latest.max_seq = lm1.seq
                ) am ON am.thread_id = t.thread_id
                LEFT JOIN (
                    SELECT lm2.thread_id, lm2.content
                    FROM llm_messages lm2
                    INNER JOIN (
                        SELECT thread_id, MAX(seq) AS max_seq
                        FROM llm_messages
                        GROUP BY thread_id
                    ) latest ON latest.thread_id = lm2.thread_id AND latest.max_seq = lm2.seq
                ) lm ON lm.thread_id = t.thread_id
                WHERE t.thread_id = ?
                  AND t.user_id IS NOT NULL
                LIMIT 1
                """;

        List<LlmCompletionNotificationInfo> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new LlmCompletionNotificationInfo(
                rs.getString("thread_id"),
                rs.getObject("user_id", Long.class),
                rs.getObject("announcement_id", Long.class),
                rs.getString("title"),
                rs.getString("preview")
        ), threadId);
        return rows.stream().findFirst();
    }

    private LocalDateTime toLocalDateTime(final Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime();
    }

    private void ensureColumn(final String tableName, final String columnName, final String ddl) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.COLUMNS
                        WHERE UPPER(TABLE_NAME) = UPPER(?)
                          AND UPPER(COLUMN_NAME) = UPPER(?)
                        """,
                Integer.class,
                tableName,
                columnName
        );

        if (exists == null || exists == 0) {
            executeIfNotAlreadyExists(ddl);
        }
    }

    private void ensureIndex(final String tableName, final String indexName, final String ddl) {
        executeIfNotAlreadyExists(ddl);
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
}
