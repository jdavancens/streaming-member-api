package com.streaming.memberapi.member.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Must be >= 256 bits (32 bytes) for HS256 key derivation.
    private static final String SECRET = "test-secret-key-that-is-long-enough-1234567890";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    @Test
    void generateAccessToken_isParseableWithExpectedClaims() {
        String memberId = UUID.randomUUID().toString();
        String token = jwtService.generateAccessToken(memberId, "alice@example.com");

        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo(memberId);
        assertThat(claims.get("email", String.class)).isEqualTo("alice@example.com");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void generateRefreshToken_hasRefreshTypeAndJti() {
        String memberId = UUID.randomUUID().toString();
        String token = jwtService.generateRefreshToken(memberId);

        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo(memberId);
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void extractMemberId_returnsSubject() {
        String memberId = UUID.randomUUID().toString();
        String token = jwtService.generateAccessToken(memberId, "alice@example.com");

        assertThat(jwtService.extractMemberId(token)).isEqualTo(memberId);
    }

    @Test
    void isRefreshToken_trueForRefreshFalseForAccess() {
        String memberId = UUID.randomUUID().toString();

        assertThat(jwtService.isRefreshToken(jwtService.generateRefreshToken(memberId))).isTrue();
        assertThat(jwtService.isRefreshToken(jwtService.generateAccessToken(memberId, "a@b.com"))).isFalse();
    }

    @Test
    void parseClaims_rejectsTokenSignedWithDifferentKey() {
        JwtService other = new JwtService("a-completely-different-secret-key-0987654321zzzz");
        String foreignToken = other.generateAccessToken(UUID.randomUUID().toString(), "a@b.com");

        assertThatThrownBy(() -> jwtService.parseClaims(foreignToken))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaims_rejectsGarbageToken() {
        assertThatThrownBy(() -> jwtService.parseClaims("not.a.jwt"))
            .isInstanceOf(JwtException.class);
    }
}
