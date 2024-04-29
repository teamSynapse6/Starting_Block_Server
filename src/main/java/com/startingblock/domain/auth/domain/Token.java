package com.startingblock.domain.auth.domain;

import com.startingblock.domain.common.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Token extends BaseEntity {

    @Id
    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;

    @Column(name = "refresh_token", nullable = false)
    @Lob
    private String refreshToken;

    public Token updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    @Builder
    public Token(String providerId, String refreshToken) {
        this.providerId = providerId;
        this.refreshToken = refreshToken;
    }

}
