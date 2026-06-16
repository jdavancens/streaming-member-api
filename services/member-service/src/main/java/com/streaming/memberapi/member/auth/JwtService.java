package com.streaming.memberapi.member.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final long ACCESS_TOKEN_MINUTES = 15;
    private static final long REFRESH_TOKEN_DAYS = 7;

    private final SecretKey signingKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String memberId, String email) {
        return Jwts.builder()
            .subject(memberId)
            .claim("email", email)
            .claim("type", "access")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact();
    }

    public String generateRefreshToken(String memberId) {
        return Jwts.builder()
            .subject(memberId)
            .claim("type", "refresh")
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS)))
            .signWith(signingKey)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractMemberId(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("type", String.class));
    }
}
