package com.streaming.memberapi.entitlement.service;

import com.streaming.memberapi.entitlement.service.EntitlementService.StreamEntitlement;
import com.streaming.memberapi.entitlement.service.EntitlementService.StreamSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the real Redis datastore layer. Spins up a Redis container,
 * drives the {@link EntitlementService} through a live {@link StringRedisTemplate},
 * and verifies the Set-based stream-slot accounting and per-slot TTL behaviour.
 */
@Testcontainers
class EntitlementServiceRedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    EntitlementService service;
    String memberId;

    @BeforeAll
    static void startTemplate() {
        RedisStandaloneConfiguration config =
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopTemplate() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        // Fresh keyspace per test so slot counts are deterministic.
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        service = new EntitlementService(redisTemplate);
        memberId = UUID.randomUUID().toString();
    }

    @Test
    void acquireStream_persistsSlotAndCountsAgainstLimit() {
        StreamSession session = service.acquireStream(memberId, "device-1");

        assertThat(session.streamId()).isNotBlank();
        assertThat(session.expiresAt()).isNotBlank();

        // Default limit is 1, so after acquiring one slot the member is at the limit.
        StreamEntitlement entitlement = service.canStream(memberId);
        assertThat(entitlement.concurrentStreams()).isEqualTo(1);
        assertThat(entitlement.allowed()).isFalse();
        assertThat(entitlement.reason()).contains("limit reached");
    }

    @Test
    void acquireStream_throwsWhenLimitReached() {
        service.acquireStream(memberId, "device-1");

        assertThatThrownBy(() -> service.acquireStream(memberId, "device-2"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("limit reached");
    }

    @Test
    void acquireStream_allowsUpToUpdatedMaxStreams() {
        service.updateMaxStreams(memberId, 3);

        service.acquireStream(memberId, "device-1");
        service.acquireStream(memberId, "device-2");
        service.acquireStream(memberId, "device-3");

        StreamEntitlement entitlement = service.canStream(memberId);
        assertThat(entitlement.concurrentStreams()).isEqualTo(3);
        assertThat(entitlement.maxStreams()).isEqualTo(3);
        assertThat(entitlement.allowed()).isFalse();
    }

    @Test
    void releaseStream_freesUpASlot() {
        service.updateMaxStreams(memberId, 2);
        StreamSession first = service.acquireStream(memberId, "device-1");
        service.acquireStream(memberId, "device-2");
        assertThat(service.canStream(memberId).concurrentStreams()).isEqualTo(2);

        boolean released = service.releaseStream(memberId, first.streamId());

        assertThat(released).isTrue();
        assertThat(service.canStream(memberId).concurrentStreams()).isEqualTo(1);
    }

    @Test
    void heartbeat_refreshesExistingSlotTtl() {
        StreamSession session = service.acquireStream(memberId, "device-1");
        String slotKey = "entitlement:" + memberId + ":slot:" + session.streamId();

        Long ttlBefore = redisTemplate.getExpire(slotKey);
        assertThat(ttlBefore).isPositive();

        boolean refreshed = service.heartbeat(memberId, session.streamId());

        assertThat(refreshed).isTrue();
        assertThat(redisTemplate.getExpire(slotKey)).isPositive();
    }

    @Test
    void heartbeat_returnsFalseForUnknownSlot() {
        boolean refreshed = service.heartbeat(memberId, UUID.randomUUID().toString());

        assertThat(refreshed).isFalse();
    }
}
