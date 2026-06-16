package com.netflix.memberapi.entitlement.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.memberapi.entitlement.service.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper;

    public SubscriptionEventConsumer(EntitlementService entitlementService, ObjectMapper objectMapper) {
        this.entitlementService = entitlementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "entitlement-service")
    public void onSubscriptionEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.get("eventType").asText();
            String memberId = event.get("memberId").asText();

            switch (eventType) {
                case "SubscriptionCreated", "PlanChanged" -> {
                    int maxStreams = event.get("maxStreams").asInt(1);
                    entitlementService.updateMaxStreams(memberId, maxStreams);
                }
                case "SubscriptionCancelled" -> {
                    entitlementService.updateMaxStreams(memberId, 0);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process subscription event: {}", payload, e);
        }
    }
}
