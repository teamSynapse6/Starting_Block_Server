package com.startingblock.domain.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.auth.dto.AppleSignInReq;
import com.startingblock.global.config.security.AuthConfig;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.payload.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppleAuthService {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";

    private final AuthConfig authConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AppleAccount resolve(final AppleSignInReq request) {
        if (request == null) {
            throw invalidAppleLogin();
        }

        String identityToken = trimToNull(request.getIdentityToken());
        if (identityToken == null && trimToNull(request.getAuthorizationCode()) != null) {
            identityToken = exchangeAuthorizationCode(request.getAuthorizationCode());
        }
        if (identityToken == null) {
            throw invalidAppleLogin();
        }

        Claims claims = verifyIdentityToken(identityToken);
        String subject = claims.getSubject();
        String requestProviderId = firstNonBlank(request.getProviderId(), request.getUserIdentifier());
        if (requestProviderId != null && !requestProviderId.equals(subject)) {
            throw invalidAppleLogin();
        }

        String email = firstNonBlank(claims.get("email", String.class), request.getEmail());
        return new AppleAccount(subject, email);
    }

    private String exchangeAuthorizationCode(final String authorizationCode) {
        String clientId = appleClientId();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", createClientSecret(clientId));
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            JsonNode response = restTemplate.postForObject(
                    APPLE_TOKEN_URL,
                    new HttpEntity<>(form, headers),
                    JsonNode.class
            );
            String identityToken = response == null ? null : trimToNull(response.path("id_token").asText(null));
            if (identityToken == null) {
                throw invalidAppleLogin();
            }
            return identityToken;
        } catch (DefaultAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }
    }

    private Claims verifyIdentityToken(final String identityToken) {
        JsonNode header;
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw invalidAppleLogin();
            }
            header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        } catch (DefaultAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }

        PublicKey publicKey = applePublicKey(header.path("kid").asText(), header.path("alg").asText());
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }

        validateAudience(claims);
        return claims;
    }

    private PublicKey applePublicKey(final String keyId, final String algorithm) {
        try {
            JsonNode keys = restTemplate.getForObject(APPLE_KEYS_URL, JsonNode.class).path("keys");
            for (JsonNode key : keys) {
                if (Objects.equals(keyId, key.path("kid").asText())
                        && Objects.equals(algorithm, key.path("alg").asText())) {
                    byte[] modulusBytes = Base64.getUrlDecoder().decode(key.path("n").asText());
                    byte[] exponentBytes = Base64.getUrlDecoder().decode(key.path("e").asText());
                    RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                            new BigInteger(1, modulusBytes),
                            new BigInteger(1, exponentBytes)
                    );
                    return KeyFactory.getInstance("RSA").generatePublic(keySpec);
                }
            }
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }
        throw invalidAppleLogin();
    }

    private void validateAudience(final Claims claims) {
        String configuredClientId = appleClientId();

        Object audience = claims.getAudience();
        if (audience instanceof String value && configuredClientId.equals(value)) {
            return;
        }
        if (audience instanceof List<?> values && values.contains(configuredClientId)) {
            return;
        }
        throw invalidAppleLogin();
    }

    private String createClientSecret(final String clientId) {
        AuthConfig.Apple apple = authConfig.getAuth().getApple();
        String teamId = requiredConfig(apple.getTeamId());
        String keyId = requiredConfig(apple.getKeyId());
        PrivateKey privateKey = applePrivateKey(requiredConfig(apple.getPrivateKeyPath()));

        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam("kid", keyId)
                .setIssuer(teamId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(300)))
                .setAudience(APPLE_ISSUER)
                .setSubject(clientId)
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
    }

    private PrivateKey applePrivateKey(final String privateKeyPath) {
        try {
            String pem = Files.readString(Path.of(privateKeyPath), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }
    }

    private String appleClientId() {
        return requiredConfig(authConfig.getAuth().getApple().getClientId());
    }

    private String requiredConfig(final String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw invalidAppleLogin();
        }
        return trimmed;
    }

    private String firstNonBlank(final String first, final String second) {
        String trimmedFirst = trimToNull(first);
        return trimmedFirst != null ? trimmedFirst : trimToNull(second);
    }

    private String trimToNull(final String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DefaultAuthenticationException invalidAppleLogin() {
        return new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
    }

    public record AppleAccount(String providerId, String email) {
    }
}
