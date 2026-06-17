package com.streaming.memberapi.entitlement.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.memberapi.entitlement.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventConsumerTest {

    @Mock
    EntitlementService entitlementService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SubscriptionEventConsumer consumer;

    private final String memberId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionEventConsumer(entitlementService, objectMapper);
    }

    @Test
    void subscriptionCreated_updatesMaxStreams() {
        consumer.onSubscriptionEvent(
            "{\"eventType\":\"SubscriptionCreated\",\"memberId\":\"" + memberId + "\",\"maxStreams\":4}");

        verify(entitlementService).updateMaxStreams(memberId, 4);
    }

    @Test
    void planChanged_updatesMaxStreams() {
        consumer.onSubscriptionEvent(
            "{\"eventType\":\"PlanChanged\",\"memberId\":\"" + memberId + "\",\"maxStreams\":2}");

        verify(entitlementService).updateMaxStreams(memberId, 2);
    }

    @Test
    void subscriptionCancelled_setsMaxStreamsToZero() {
        consumer.onSubscriptionEvent(
            "{\"eventType\":\"SubscriptionCancelled\",\"memberId\":\"" + memberId + "\"}");

        verify(entitlementService).updateMaxStreams(memberId, 0);
    }

    @Test
    void unknownEventType_isIgnored() {
        consumer.onSubscriptionEvent(
            "{\"eventType\":\"SomethingElse\",\"memberId\":\"" + memberId + "\"}");

        verify(entitlementService, never()).updateMaxStreams(anyString(), anyInt());
    }

    @Test
    void malformedPayload_doesNotThrow() {
        consumer.onSubscriptionEvent("not-json");

        verify(entitlementService, never()).updateMaxStreams(anyString(), anyInt());
    }
}
