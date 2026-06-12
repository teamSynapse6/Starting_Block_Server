package com.startingblock.global.infrastructure.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "llm_model_config",
        uniqueConstraints = @UniqueConstraint(name = "uk_llm_model_config_model_name", columnNames = "model_name")
)
public class LlmModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, unique = true)
    private String modelName;

    @Column(name = "temperature")
    private Float temperature;

    @Column(name = "top_p")
    private Float topP;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(name = "system_instruction", columnDefinition = "TEXT")
    private String systemInstruction;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LlmModelConfig(final String modelName) {
        this.modelName = modelName;
    }

    public void update(
            final Float temperature,
            final Float topP,
            final Integer topK,
            final Integer maxOutputTokens,
            final String systemInstruction
    ) {
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxOutputTokens = maxOutputTokens;
        this.systemInstruction = systemInstruction;
        this.updatedAt = LocalDateTime.now();
    }
}
