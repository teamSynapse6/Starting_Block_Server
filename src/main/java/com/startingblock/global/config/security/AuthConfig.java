package com.startingblock.global.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AuthConfig {
    private final Auth auth = new Auth();

    @Data
    public static class Auth {
        private String tokenSecret;
        private long accessTokenExpirationMsec;
        private long refreshTokenExpirationMsec;
        private String kakaoAdminKey;
        private Apple apple = new Apple();
    }

    @Data
    public static class Apple {
        private String clientId;
        private String teamId;
        private String keyId;
        private String privateKeyPath;
    }

    public Auth getAuth() {
        return auth;
    }

}
