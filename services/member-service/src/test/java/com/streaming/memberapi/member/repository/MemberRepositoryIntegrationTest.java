package com.streaming.memberapi.member.repository;

import com.streaming.memberapi.member.model.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the real Cassandra datastore layer. Spins up a Cassandra
 * container, lets the {@link MemberRepository} read and write against it, and
 * verifies entity mapping plus the custom {@code findByEmail} query.
 */
@DataCassandraTest
@Testcontainers
class MemberRepositoryIntegrationTest {

    @Container
    static final CassandraContainer<?> CASSANDRA =
        new CassandraContainer<>(DockerImageName.parse("cassandra:4.1"))
            .withInitScript("cassandra-member-init.cql");

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points",
            () -> CASSANDRA.getHost() + ":" + CASSANDRA.getMappedPort(9042));
        registry.add("spring.cassandra.local-datacenter", CASSANDRA::getLocalDatacenter);
        registry.add("spring.cassandra.keyspace-name", () -> "member_api");
        registry.add("spring.cassandra.schema-action", () -> "none");
    }

    @Autowired
    MemberRepository memberRepository;

    private Member newMember(String email) {
        return new Member(UUID.randomUUID(), email, "hash", "Alice Smith",
            "US", "ACTIVE", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void savesAndReadsBackById() {
        Member saved = memberRepository.save(newMember("alice@example.com"));

        Optional<Member> found = memberRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getFullName()).isEqualTo("Alice Smith");
        assertThat(found.get().getCountry()).isEqualTo("US");
        assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(found.get().getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findByEmail_returnsMatchingMember() {
        memberRepository.save(newMember("bob@example.com"));

        Optional<Member> found = memberRepository.findByEmail("bob@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void findByEmail_returnsEmptyWhenAbsent() {
        assertThat(memberRepository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void updatesStatusInPlace() {
        Member saved = memberRepository.save(newMember("carol@example.com"));
        saved.setStatus("CANCELLED");
        memberRepository.save(saved);

        Optional<Member> found = memberRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("CANCELLED");
    }
}
