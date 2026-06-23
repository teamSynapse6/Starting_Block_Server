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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppleAuthService {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";

    private final AuthConfig authConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AppleAccount resolve(final AppleSignInReq request) {
        if (request == null) {
            throw invalidAppleLogin();
        }

        String identityToken = trimToNull(request.getIdentityToken());
        String authorizationCode = trimToNull(request.getAuthorizationCode());
        AppleTokenResponse tokenResponse = null;
        if (authorizationCode != null) {
            tokenResponse = exchangeAuthorizationCode(authorizationCode, identityToken == null);
            if (identityToken == null && tokenResponse != null) {
                identityToken = tokenResponse.identityToken();
            }
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
        return new AppleAccount(subject, email, tokenResponse == null ? null : tokenResponse.refreshToken());
    }

    public void revokeRefreshToken(final String refreshToken) {
        String token = requiredConfig(refreshToken);
        String clientId = appleClientId();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", createClientSecret(clientId));
        form.add("token", token);
        form.add("token_type_hint", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForEntity(
                    APPLE_REVOKE_URL,
                    new HttpEntity<>(form, headers),
                    String.class
            );
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }
    }

    public List<AppleServerNotificationEvent> resolveServerNotification(final String payload) {
        Claims claims = verifyAppleJwt(payload);
        List<AppleServerNotificationEvent> events = new ArrayList<>();
        appendAppleEvents(events, objectMapper.valueToTree(claims.get("events")), claims.getSubject(), null);

        if (events.isEmpty()) {
            String type = trimToNull(claims.get("type", String.class));
            String providerId = trimToNull(claims.getSubject());
            if (type != null && providerId != null) {
                events.add(new AppleServerNotificationEvent(type, providerId));
            }
        }

        return events;
    }

    private AppleTokenResponse exchangeAuthorizationCode(final String authorizationCode, final boolean required) {
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
            String refreshToken = response == null ? null : trimToNull(response.path("refresh_token").asText(null));
            if (identityToken == null) {
                throw invalidAppleLogin();
            }
            return new AppleTokenResponse(identityToken, refreshToken);
        } catch (DefaultAuthenticationException exception) {
            if (!required) {
                return null;
            }
            throw exception;
        } catch (Exception exception) {
            if (!required) {
                return null;
            }
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
        Claims claims = parseAppleJwt(identityToken, publicKey);

        validateAudience(claims);
        return claims;
    }

    private Claims verifyAppleJwt(final String token) {
        JsonNode header;
        try {
            String[] parts = token.split("\\.");
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
        Claims claims = parseAppleJwt(token, publicKey);
        validateAudience(claims);
        return claims;
    }

    private Claims parseAppleJwt(final String token, final PublicKey publicKey) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception exception) {
            throw invalidAppleLogin();
        }
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

    private void appendAppleEvents(
            final List<AppleServerNotificationEvent> events,
            final JsonNode node,
            final String defaultProviderId,
            final String defaultType
    ) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode eventNode : node) {
                appendAppleEvents(events, eventNode, defaultProviderId, defaultType);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        if (node.has("type") || node.has("sub")) {
            addAppleEvent(events, node, defaultProviderId, defaultType);
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            appendAppleEvents(events, field.getValue(), defaultProviderId, field.getKey());
        }
    }

    private void addAppleEvent(
            final List<AppleServerNotificationEvent> events,
            final JsonNode node,
            final String defaultProviderId,
            final String defaultType
    ) {
        String type = firstNonBlank(node.path("type").asText(null), defaultType);
        String providerId = firstNonBlank(node.path("sub").asText(null), defaultProviderId);
        if (type != null && providerId != null) {
            events.add(new AppleServerNotificationEvent(type, providerId));
        }
    }

    private String trimToNull(final String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DefaultAuthenticationException invalidAppleLogin() {
        return new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
    }

    public record AppleAccount(String providerId, String email, String refreshToken) {
    }

    public record AppleServerNotificationEvent(String type, String providerId) {
    }

    private record AppleTokenResponse(String identityToken, String refreshToken) {
    }
}
