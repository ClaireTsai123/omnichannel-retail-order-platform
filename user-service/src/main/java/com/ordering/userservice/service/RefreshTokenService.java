package com.ordering.userservice.service;

import com.ordering.common.exception.UnauthorizedException;
import com.ordering.userservice.entity.RefreshToken;
import com.ordering.userservice.entity.User;
import com.ordering.userservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawToken();
        saveRefreshToken(user, rawToken);
        return rawToken;
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        RefreshToken current = findActiveToken(rawToken);
        User user = current.getUser();
        user.getUsername();
        user.getRole();

        String newRawToken = createRefreshToken(current.getUser());
        RefreshToken replacement = refreshTokenRepository.findByTokenHash(hash(newRawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        current.setRevokedAt(Instant.now());
        current.setReplacedByTokenId(replacement.getId());
        refreshTokenRepository.save(current);

        return new RotatedRefreshToken(user, newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        RefreshToken refreshToken = findActiveToken(rawToken);
        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);
    }

    private RefreshToken findActiveToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!refreshToken.isActive()) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        return refreshToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void saveRefreshToken(User user, String rawToken) {
        RefreshToken refreshToken = new RefreshToken();
        Instant now = Instant.now();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusMillis(refreshTokenExpirationMs));
        refreshTokenRepository.save(refreshToken);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record RotatedRefreshToken(User user, String refreshToken) {
    }
}
