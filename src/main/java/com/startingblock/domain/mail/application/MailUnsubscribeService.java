package com.startingblock.domain.mail.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.mail.domain.ContactBlocklist;
import com.startingblock.domain.mail.domain.repository.ContactBlocklistRepository;
import com.startingblock.global.error.DefaultException;
import com.startingblock.global.payload.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailUnsubscribeService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_SEPARATOR = ".";
    private static final String PAYLOAD_SEPARATOR = "\\|";

    private final AnnouncementRepository announcementRepository;
    private final ContactBlocklistRepository contactBlocklistRepository;

    @Value("${mail.unsubscribe.secret}")
    private String secret;

    @Value("${mail.unsubscribe.token-ttl-days:3650}")
    private long tokenTtlDays;

    @Value("${mail.unsubscribe.base-url:https://www.startingblock.co.kr/api/v1/web/mail/unsubscribe}")
    private String unsubscribeBaseUrl;

    public String createUnsubscribeLink(final Long announcementId, final String contact) {
        String token = createToken(announcementId, contact);
        return unsubscribeBaseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @Transactional
    public void unsubscribe(final String token) {
        TokenPayload payload = verifyToken(token);
        Announcement announcement = announcementRepository.findById(payload.announcementId())
                .orElseThrow(InvalidAnnouncementException::new);

        String currentContact = announcement.getContact() == null ? "" : announcement.getContact().trim();
        String normalizedContact = normalizeContact(currentContact);
        if (!normalizedContact.equals(payload.contact())) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "문의처 정보가 일치하지 않습니다.");
        }

        if (!contactBlocklistRepository.existsByAnnouncementIdAndContactIgnoreCase(announcement.getId(), normalizedContact)) {
            contactBlocklistRepository.save(new ContactBlocklist(normalizedContact, announcement.getId()));
        }
        announcementRepository.blockContactsByNormalizedContact(normalizedContact);
    }

    private String createToken(final Long announcementId, final String contact) {
        String normalizedContact = normalizeContact(contact);
        if (announcementId == null || normalizedContact.isBlank()) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰을 만들 수 없습니다.");
        }
        long expiresAt = Instant.now().plus(tokenTtlDays, ChronoUnit.DAYS).getEpochSecond();
        String payload = announcementId + "|" + normalizedContact + "|" + expiresAt;
        return base64Url(payload.getBytes(StandardCharsets.UTF_8)) + TOKEN_SEPARATOR + base64Url(sign(payload));
    }

    private TokenPayload verifyToken(final String token) {
        if (token == null || token.isBlank()) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰이 없습니다.");
        }

        String[] parts = token.split("\\" + TOKEN_SEPARATOR, 2);
        if (parts.length != 2) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰 형식이 올바르지 않습니다.");
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰을 해석할 수 없습니다.");
        }

        byte[] expectedSignature = sign(payload);
        byte[] actualSignature;
        try {
            actualSignature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 서명을 해석할 수 없습니다.");
        }
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰 서명이 올바르지 않습니다.");
        }

        String[] payloadParts = payload.split(PAYLOAD_SEPARATOR, 3);
        if (payloadParts.length != 3) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰 내용이 올바르지 않습니다.");
        }

        try {
            Long announcementId = Long.parseLong(payloadParts[0]);
            String contact = normalizeContact(payloadParts[1]);
            long expiresAt = Long.parseLong(payloadParts[2]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰이 만료되었습니다.");
            }
            return new TokenPayload(announcementId, contact);
        } catch (NumberFormatException exception) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰 값이 올바르지 않습니다.");
        }
    }

    private String normalizeContact(final String contact) {
        return contact == null ? "" : contact.trim().toLowerCase();
    }

    private byte[] sign(final String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new DefaultException(ErrorCode.INVALID_PARAMETER, "수신 거부 토큰 처리에 실패했습니다.");
        }
    }

    private String base64Url(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record TokenPayload(Long announcementId, String contact) {
    }
}
