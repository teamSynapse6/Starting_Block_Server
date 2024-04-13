package com.startingblock.global.config;

import feign.Logger;
import feign.codec.Encoder;
import feign.form.FormEncoder;
import feign.form.spring.SpringFormEncoder;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.naming.spi.ObjectFactory;

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
    public Encoder feignFormEncoder() {
        return new FormEncoder();
    }

    @Data
    public static class ServiceKey {
        private String openData;
        private String bizInfo;
    }

}
