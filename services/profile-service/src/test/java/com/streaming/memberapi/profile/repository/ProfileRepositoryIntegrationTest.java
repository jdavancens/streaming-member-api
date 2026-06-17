package com.streaming.memberapi.profile.repository;

import com.streaming.memberapi.profile.model.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the real Cassandra datastore layer. Spins up a Cassandra
 * container and exercises the {@link ProfileRepository}, including the partitioned
 * member_id / clustered profile_id key and the custom member-scoped queries.
 */
@DataCassandraTest
@Testcontainers
class ProfileRepositoryIntegrationTest {

    @Container
    static final CassandraContainer<?> CASSANDRA =
        new CassandraContainer<>(DockerImageName.parse("cassandra:4.1"))
            .withInitScript("cassandra-profile-init.cql");

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points",
            () -> CASSANDRA.getHost() + ":" + CASSANDRA.getMappedPort(9042));
        registry.add("spring.cassandra.local-datacenter", CASSANDRA::getLocalDatacenter);
        registry.add("spring.cassandra.keyspace-name", () -> "member_api");
        registry.add("spring.cassandra.schema-action", () -> "none");
    }

    @Autowired
    ProfileRepository profileRepository;

    private Profile newProfile(UUID memberId, String name) {
        Profile profile = new Profile();
        profile.setMemberId(memberId);
        profile.setProfileId(UUID.randomUUID());
        profile.setName(name);
        profile.setAvatarUrl("https://cdn.example.com/" + name + ".png");
        profile.setIsKids(false);
        profile.setLanguage("en");
        return profile;
    }

    @Test
    void savesAndFindsByMemberId() {
        UUID memberId = UUID.randomUUID();
        profileRepository.save(newProfile(memberId, "Adult"));
        profileRepository.save(newProfile(memberId, "Kids"));
        // Different member — must not leak into the query above.
        profileRepository.save(newProfile(UUID.randomUUID(), "Other"));

        List<Profile> profiles = profileRepository.findByMemberId(memberId);

        assertThat(profiles).hasSize(2);
        assertThat(profiles).extracting(Profile::getName)
            .containsExactlyInAnyOrder("Adult", "Kids");
    }

    @Test
    void findByMemberIdAndProfileId_returnsSingleProfile() {
        UUID memberId = UUID.randomUUID();
        Profile saved = profileRepository.save(newProfile(memberId, "Adult"));

        Optional<Profile> found =
            profileRepository.findByMemberIdAndProfileId(memberId, saved.getProfileId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Adult");
        assertThat(found.get().getLanguage()).isEqualTo("en");
        assertThat(found.get().getIsKids()).isFalse();
    }

    @Test
    void countByMemberId_reflectsSavedProfiles() {
        UUID memberId = UUID.randomUUID();
        profileRepository.save(newProfile(memberId, "One"));
        profileRepository.save(newProfile(memberId, "Two"));
        profileRepository.save(newProfile(memberId, "Three"));

        assertThat(profileRepository.countByMemberId(memberId)).isEqualTo(3);
    }

    @Test
    void deleteRemovesProfile() {
        UUID memberId = UUID.randomUUID();
        Profile saved = profileRepository.save(newProfile(memberId, "Temp"));

        profileRepository.delete(saved);

        assertThat(profileRepository.findByMemberId(memberId)).isEmpty();
    }
}
