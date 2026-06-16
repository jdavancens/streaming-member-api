package com.netflix.memberapi.member;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.memberapi.member.datafetcher.MemberDataFetcher;
import com.netflix.memberapi.member.model.Member;
import com.netflix.memberapi.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {DgsAutoConfiguration.class, MemberDataFetcher.class})
class MemberDataFetcherTest {

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @MockBean
    MemberService memberService;

    @Test
    void memberQuery_returnsCorrectFields() {
        UUID id = UUID.randomUUID();
        Member member = new Member(id, "alice@example.com", "hash", "Alice Smith", "US", "ACTIVE", Instant.now());
        when(memberService.findById(id)).thenReturn(Optional.of(member));

        String email = dgsQueryExecutor.executeAndExtractJsonPath(
            "{ member(id: \"" + id + "\") { email fullName status } }",
            "data.member.email"
        );

        assertThat(email).isEqualTo("alice@example.com");
    }

    @Test
    void register_mutation_returnsMember() {
        UUID id = UUID.randomUUID();
        Member member = new Member(id, "bob@example.com", "hash", "Bob Jones", "GB", "ACTIVE", Instant.now());
        when(memberService.register(any(), any(), any(), any())).thenReturn(member);

        String memberId = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            mutation {
              register(input: {
                email: "bob@example.com"
                password: "secret123"
                fullName: "Bob Jones"
                country: "GB"
              }) {
                member { id email }
              }
            }
            """,
            "data.register.member.id"
        );

        assertThat(memberId).isEqualTo(id.toString());
    }
}
