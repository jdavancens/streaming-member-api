package com.streaming.memberapi.member.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.memberapi.member.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberEventProducerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MemberEventProducer producer;

    private Member member(UUID id) {
        return new Member(id, "alice@example.com", "hash", "Alice Smith", "US", "ACTIVE", Instant.now());
    }

    @BeforeEach
    void setUp() {
        producer = new MemberEventProducer(kafkaTemplate, objectMapper);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(future);
    }

    @Test
    void publishMemberRegistered_sendsToMemberEventsTopicKeyedByMemberId() throws Exception {
        UUID id = UUID.randomUUID();
        producer.publishMemberRegistered(member(id));

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo("member.events");
        assertThat(key.getValue()).isEqualTo(id.toString());

        var json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("MemberRegistered");
        assertThat(json.get("memberId").asText()).isEqualTo(id.toString());
        assertThat(json.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(json.get("country").asText()).isEqualTo("US");
        assertThat(json.has("eventId")).isTrue();
        assertThat(json.has("occurredAt")).isTrue();
    }

    @Test
    void publishMemberCancelled_sendsCancelledEvent() throws Exception {
        UUID id = UUID.randomUUID();
        producer.publishMemberCancelled(member(id));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("member.events"), eq(id.toString()), payload.capture());

        var json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("MemberCancelled");
        assertThat(json.get("memberId").asText()).isEqualTo(id.toString());
    }
}
