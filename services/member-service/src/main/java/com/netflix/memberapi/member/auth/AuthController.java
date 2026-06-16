package com.netflix.memberapi.member.auth;

import com.netflix.memberapi.member.model.Member;
import com.netflix.memberapi.member.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final JwtService jwtService;

    public AuthController(MemberService memberService, JwtService jwtService) {
        this.memberService = memberService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return memberService.findByEmail(request.email())
            .filter(m -> memberService.verifyPassword(m, request.password()))
            .map(m -> ResponseEntity.ok(buildTokenResponse(m)))
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid credentials")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            String token = request.refreshToken();
            if (!jwtService.isRefreshToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not a refresh token"));
            }
            String memberId = jwtService.extractMemberId(token);
            return memberService.findById(UUID.fromString(memberId))
                .map(m -> ResponseEntity.ok(buildTokenResponse(m)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Member not found")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid or expired refresh token"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        // Stateless logout: refresh tokens expire naturally.
        // For a revocation list, store the jti in Redis with TTL matching expiry.
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> buildTokenResponse(Member member) {
        return Map.of(
            "accessToken", jwtService.generateAccessToken(member.getId().toString(), member.getEmail()),
            "refreshToken", jwtService.generateRefreshToken(member.getId().toString()),
            "expiresIn", 900,
            "tokenType", "Bearer"
        );
    }
}
