package com.startingblock.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Request;
import feign.codec.Encoder;
import feign.form.FormEncoder;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;


@Configuration
@ConfigurationProperties(prefix = "feign.client")
@EnableFeignClients(basePackages = "com.startingblock.global.infrastructure.feign")
@Getter
public class FeignConfig {

    private final ServiceKey serviceKey = new ServiceKey();

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    @Primary
    public Encoder feignEncoder(ObjectMapper objectMapper) {
        return new SpringEncoder(() -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)));
    }

    @Bean
    public Encoder feignFormEncoder() {
        return new FormEncoder();
    }

    @Data
    public static class ServiceKey {
        private String openData;
        private String bizInfo;
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                5000, // 연결 타임아웃 (5초)
                60000 // 읽기 타임아웃 (10초)
        );
    }

}
