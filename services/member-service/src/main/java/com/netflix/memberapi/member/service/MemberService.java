package com.netflix.memberapi.member.service;

import com.netflix.memberapi.member.event.MemberEventProducer;
import com.netflix.memberapi.member.model.Member;
import com.netflix.memberapi.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberEventProducer eventProducer;

    public MemberService(MemberRepository memberRepository,
                         PasswordEncoder passwordEncoder,
                         MemberEventProducer eventProducer) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventProducer = eventProducer;
    }

    public Member register(String email, String password, String fullName, String country) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        Member member = new Member(
            UUID.randomUUID(),
            email,
            passwordEncoder.encode(password),
            fullName,
            country,
            "ACTIVE",
            Instant.now()
        );
        member = memberRepository.save(member);
        eventProducer.publishMemberRegistered(member);
        return member;
    }

    public Optional<Member> findById(UUID id) {
        return memberRepository.findById(id);
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    public boolean verifyPassword(Member member, String rawPassword) {
        return passwordEncoder.matches(rawPassword, member.getPasswordHash());
    }

    public Member cancel(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        member.setStatus("CANCELLED");
        member = memberRepository.save(member);
        eventProducer.publishMemberCancelled(member);
        return member;
    }
}
