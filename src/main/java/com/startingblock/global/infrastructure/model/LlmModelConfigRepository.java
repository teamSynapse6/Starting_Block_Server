package com.startingblock.global.infrastructure.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmModelConfigRepository extends JpaRepository<LlmModelConfig, Long> {

    Optional<LlmModelConfig> findByModelName(String modelName);

    void deleteByModelName(String modelName);
}
