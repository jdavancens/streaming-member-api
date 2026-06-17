package com.streaming.memberapi.member.auth;

import com.streaming.memberapi.member.model.Member;
import com.streaming.memberapi.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    MemberService memberService;
    @Mock
    JwtService jwtService;

    @InjectMocks
    AuthController authController;

    private Member member(UUID id) {
        return new Member(id, "alice@example.com", "hash", "Alice", "US", "ACTIVE", Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_returnsTokensForValidCredentials() {
        UUID id = UUID.randomUUID();
        Member m = member(id);
        when(memberService.findByEmail("alice@example.com")).thenReturn(Optional.of(m));
        when(memberService.verifyPassword(m, "secret")).thenReturn(true);
        when(jwtService.generateAccessToken(id.toString(), "alice@example.com")).thenReturn("access-tok");
        when(jwtService.generateRefreshToken(id.toString())).thenReturn("refresh-tok");

        ResponseEntity<?> response = authController.login(new LoginRequest("alice@example.com", "secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("accessToken", "access-tok")
            .containsEntry("refreshToken", "refresh-tok")
            .containsEntry("tokenType", "Bearer")
            .containsEntry("expiresIn", 900);
    }

    @Test
    void login_rejectsWrongPassword() {
        Member m = member(UUID.randomUUID());
        when(memberService.findByEmail("alice@example.com")).thenReturn(Optional.of(m));
        when(memberService.verifyPassword(m, "wrong")).thenReturn(false);

        ResponseEntity<?> response = authController.login(new LoginRequest("alice@example.com", "wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(memberService.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.login(new LoginRequest("ghost@example.com", "secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refresh_issuesNewTokensForValidRefreshToken() {
        UUID id = UUID.randomUUID();
        Member m = member(id);
        when(jwtService.isRefreshToken("refresh-tok")).thenReturn(true);
        when(jwtService.extractMemberId("refresh-tok")).thenReturn(id.toString());
        when(memberService.findById(id)).thenReturn(Optional.of(m));
        when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("new-refresh");

        ResponseEntity<?> response = authController.refresh(new RefreshRequest("refresh-tok"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("accessToken", "new-access");
    }

    @Test
    void refresh_rejectsAccessTokenUsedAsRefresh() {
        when(jwtService.isRefreshToken("access-tok")).thenReturn(false);

        ResponseEntity<?> response = authController.refresh(new RefreshRequest("access-tok"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_rejectsWhenMemberNotFound() {
        UUID id = UUID.randomUUID();
        when(jwtService.isRefreshToken("refresh-tok")).thenReturn(true);
        when(jwtService.extractMemberId("refresh-tok")).thenReturn(id.toString());
        when(memberService.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.refresh(new RefreshRequest("refresh-tok"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_rejectsInvalidTokenThatThrows() {
        when(jwtService.isRefreshToken("bad")).thenThrow(new RuntimeException("expired"));

        ResponseEntity<?> response = authController.refresh(new RefreshRequest("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_returnsNoContent() {
        ResponseEntity<Void> response = authController.logout(new RefreshRequest("any"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
