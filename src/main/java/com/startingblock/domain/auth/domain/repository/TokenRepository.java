package com.startingblock.domain.auth.domain.repository;

import com.startingblock.domain.auth.domain.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {
    Optional<Token> findByProviderId(String providerId);
    Optional<Token> findByRefreshToken(String refreshToken);
}