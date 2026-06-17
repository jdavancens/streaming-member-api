package com.streaming.memberapi.entitlement.service;

import com.streaming.memberapi.entitlement.service.EntitlementService.StreamEntitlement;
import com.streaming.memberapi.entitlement.service.EntitlementService.StreamSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntitlementServiceTest {

    @Mock
    StringRedisTemplate redis;
    @Mock
    SetOperations<String, String> setOps;
    @Mock
    ValueOperations<String, String> valueOps;

    EntitlementService service;

    private final String memberId = UUID.randomUUID().toString();
    private final String slotsKey = "entitlement:" + memberId + ":streams";

    @BeforeEach
    void setUp() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new EntitlementService(redis);
    }

    @Test
    void canStream_allowedWhenBelowLimit() {
        // default max is 1, current count 0
        when(setOps.size(slotsKey)).thenReturn(0L);

        StreamEntitlement result = service.canStream(memberId);

        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.concurrentStreams()).isZero();
        assertThat(result.maxStreams()).isEqualTo(1);
    }

    @Test
    void canStream_deniedWhenAtLimit() {
        when(setOps.size(slotsKey)).thenReturn(1L);

        StreamEntitlement result = service.canStream(memberId);

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("limit reached");
        assertThat(result.concurrentStreams()).isEqualTo(1);
    }

    @Test
    void canStream_respectsUpdatedMaxStreams() {
        service.updateMaxStreams(memberId, 3);
        when(setOps.size(slotsKey)).thenReturn(2L);

        StreamEntitlement result = service.canStream(memberId);

        assertThat(result.allowed()).isTrue();
        assertThat(result.maxStreams()).isEqualTo(3);
        assertThat(result.concurrentStreams()).isEqualTo(2);
    }

    @Test
    void acquireStream_addsSlotAndReturnsSession() {
        when(setOps.size(slotsKey)).thenReturn(0L);

        StreamSession session = service.acquireStream(memberId, "device-1");

        assertThat(session.streamId()).isNotBlank();
        assertThat(session.expiresAt()).isNotBlank();
        verify(setOps).add(eq(slotsKey), anyString());
        verify(redis).expire(eq(slotsKey), eq(Duration.ofSeconds(90)));
        verify(valueOps).set(anyString(), eq("device-1"), eq(Duration.ofSeconds(90)));
    }

    @Test
    void acquireStream_throwsWhenAtLimit() {
        when(setOps.size(slotsKey)).thenReturn(1L);

        assertThatThrownBy(() -> service.acquireStream(memberId, "device-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("limit reached");
        verify(setOps, never()).add(anyString(), anyString());
    }

    @Test
    void releaseStream_removesMatchingSlotAndKey() {
        String streamId = UUID.randomUUID().toString();
        when(setOps.members(slotsKey)).thenReturn(Set.of(streamId + ":device-1", "other:device-2"));

        boolean result = service.releaseStream(memberId, streamId);

        assertThat(result).isTrue();
        verify(setOps).remove(slotsKey, streamId + ":device-1");
        verify(redis).delete("entitlement:" + memberId + ":slot:" + streamId);
    }

    @Test
    void releaseStream_handlesNullMembers() {
        String streamId = UUID.randomUUID().toString();
        when(setOps.members(slotsKey)).thenReturn(null);

        boolean result = service.releaseStream(memberId, streamId);

        assertThat(result).isTrue();
        verify(redis).delete("entitlement:" + memberId + ":slot:" + streamId);
    }

    @Test
    void heartbeat_refreshesTtlWhenSlotExists() {
        String streamId = UUID.randomUUID().toString();
        String slotKey = "entitlement:" + memberId + ":slot:" + streamId;
        when(redis.hasKey(slotKey)).thenReturn(true);

        boolean result = service.heartbeat(memberId, streamId);

        assertThat(result).isTrue();
        verify(redis).expire(eq(slotKey), eq(Duration.ofSeconds(90)));
        verify(redis).expire(eq(slotsKey), eq(Duration.ofSeconds(90)));
    }

    @Test
    void heartbeat_returnsFalseWhenSlotMissing() {
        String streamId = UUID.randomUUID().toString();
        String slotKey = "entitlement:" + memberId + ":slot:" + streamId;
        when(redis.hasKey(slotKey)).thenReturn(false);

        boolean result = service.heartbeat(memberId, streamId);

        assertThat(result).isFalse();
        verify(redis, never()).expire(any(), any());
    }
}
