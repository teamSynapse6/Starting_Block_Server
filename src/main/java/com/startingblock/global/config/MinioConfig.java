package com.startingblock.global.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${minio.endpoint}") final String endpoint,
            @Value("${minio.access-key}") final String accessKey,
            @Value("${minio.secret-key}") final String secretKey,
            @Value("${minio.secure:false}") final boolean secure
    ) {
        String normalizedEndpoint = endpoint;
        if (!normalizedEndpoint.startsWith("http://") && !normalizedEndpoint.startsWith("https://")) {
            normalizedEndpoint = (secure ? "https://" : "http://") + normalizedEndpoint;
        }
        return MinioClient.builder()
                .endpoint(normalizedEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
