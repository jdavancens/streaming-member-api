package com.streaming.memberapi.entitlement.datafetcher;

import com.streaming.memberapi.entitlement.service.EntitlementService;
import com.streaming.memberapi.entitlement.service.EntitlementService.StreamEntitlement;
import com.streaming.memberapi.entitlement.service.EntitlementService.StreamSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementDataFetcherTest {

    @Mock
    EntitlementService entitlementService;

    @InjectMocks
    EntitlementDataFetcher dataFetcher;

    @Test
    void resolveMemberEntity_returnsCanStreamEntitlement() {
        String memberId = UUID.randomUUID().toString();
        StreamEntitlement entitlement = new StreamEntitlement(true, null, 0, 2);
        when(entitlementService.canStream(memberId)).thenReturn(entitlement);

        Map<String, Object> result = dataFetcher.resolveMemberEntity(Map.of("id", memberId));

        assertThat(result).containsEntry("id", memberId);
        assertThat(result.get("canStream")).isEqualTo(entitlement);
    }

    @Test
    void acquireStream_delegatesToService() {
        StreamSession session = new StreamSession("stream-1", "2026-06-17T00:00:00Z");
        when(entitlementService.acquireStream("m1", "d1")).thenReturn(session);

        assertThat(dataFetcher.acquireStream("m1", "d1")).isEqualTo(session);
    }

    @Test
    void releaseStream_delegatesToService() {
        when(entitlementService.releaseStream("m1", "s1")).thenReturn(true);

        assertThat(dataFetcher.releaseStream("m1", "s1")).isTrue();
    }

    @Test
    void heartbeatStream_delegatesToService() {
        when(entitlementService.heartbeat("m1", "s1")).thenReturn(false);

        assertThat(dataFetcher.heartbeatStream("m1", "s1")).isFalse();
    }
}
