package com.startingblock.global.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.startingblock.global.infrastructure.feign")
public class FeignConfig {
}
