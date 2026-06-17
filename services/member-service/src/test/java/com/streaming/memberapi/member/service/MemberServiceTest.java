package com.streaming.memberapi.member.service;

import com.streaming.memberapi.member.event.MemberEventProducer;
import com.streaming.memberapi.member.model.Member;
import com.streaming.memberapi.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    MemberEventProducer eventProducer;

    @InjectMocks
    MemberService memberService;

    private Member sampleMember(UUID id, String status) {
        return new Member(id, "alice@example.com", "hash", "Alice Smith", "US", status, Instant.now());
    }

    @Test
    void register_persistsMemberHashesPasswordAndPublishesEvent() {
        when(memberRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("ENCODED");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = memberService.register("alice@example.com", "secret123", "Alice Smith", "US");

        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("ENCODED");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("ENCODED");
        verify(eventProducer).publishMemberRegistered(result);
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(memberRepository.findByEmail("alice@example.com"))
            .thenReturn(Optional.of(sampleMember(UUID.randomUUID(), "ACTIVE")));

        assertThatThrownBy(() ->
            memberService.register("alice@example.com", "secret123", "Alice", "US"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already registered");

        verify(memberRepository, never()).save(any());
        verify(eventProducer, never()).publishMemberRegistered(any());
    }

    @Test
    void findById_returnsRepositoryResult() {
        UUID id = UUID.randomUUID();
        Member m = sampleMember(id, "ACTIVE");
        when(memberRepository.findById(id)).thenReturn(Optional.of(m));

        assertThat(memberService.findById(id)).contains(m);
    }

    @Test
    void findByEmail_returnsRepositoryResult() {
        Member m = sampleMember(UUID.randomUUID(), "ACTIVE");
        when(memberRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(m));

        assertThat(memberService.findByEmail("alice@example.com")).contains(m);
    }

    @Test
    void verifyPassword_delegatesToEncoder() {
        Member m = sampleMember(UUID.randomUUID(), "ACTIVE");
        when(passwordEncoder.matches("raw", "hash")).thenReturn(true);

        assertThat(memberService.verifyPassword(m, "raw")).isTrue();
    }

    @Test
    void cancel_setsStatusCancelledAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Member m = sampleMember(id, "ACTIVE");
        when(memberRepository.findById(id)).thenReturn(Optional.of(m));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = memberService.cancel(id);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(eventProducer).publishMemberCancelled(result);
    }

    @Test
    void cancel_throwsWhenMemberMissing() {
        UUID id = UUID.randomUUID();
        when(memberRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.cancel(id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");

        verify(eventProducer, never()).publishMemberCancelled(any());
    }
}
