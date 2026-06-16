package com.netflix.memberapi.member.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.memberapi.member.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class MemberEventProducer {

    private static final Logger log = LoggerFactory.getLogger(MemberEventProducer.class);
    private static final String TOPIC = "member.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MemberEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishMemberRegistered(Member member) {
        publish(Map.of(
            "eventId", UUID.randomUUID().toString(),
            "eventType", "MemberRegistered",
            "occurredAt", Instant.now().toString(),
            "memberId", member.getId().toString(),
            "email", member.getEmail(),
            "fullName", member.getFullName(),
            "country", member.getCountry()
        ), member.getId().toString());
    }

    public void publishMemberCancelled(Member member) {
        publish(Map.of(
            "eventId", UUID.randomUUID().toString(),
            "eventType", "MemberCancelled",
            "occurredAt", Instant.now().toString(),
            "memberId", member.getId().toString()
        ), member.getId().toString());
    }

    private void publish(Map<String, Object> event, String key) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event type={}", event.get("eventType"), ex);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
        }
    }
}
