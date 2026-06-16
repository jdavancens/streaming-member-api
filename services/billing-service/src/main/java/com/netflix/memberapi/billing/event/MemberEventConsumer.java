package com.netflix.memberapi.billing.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.memberapi.billing.repository.PlanRepository;
import com.netflix.memberapi.billing.service.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MemberEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemberEventConsumer.class);

    private final BillingService billingService;
    private final PlanRepository planRepository;
    private final ObjectMapper objectMapper;

    public MemberEventConsumer(BillingService billingService,
                               PlanRepository planRepository,
                               ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.planRepository = planRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "member.events", groupId = "billing-service")
    public void onMemberEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.get("eventType").asText();

            if ("MemberRegistered".equals(eventType)) {
                UUID memberId = UUID.fromString(event.get("memberId").asText());
                // Auto-subscribe new members to BASIC plan (id=2) as a trial
                planRepository.findAll().stream()
                    .filter(p -> "BASIC".equals(p.getName()))
                    .findFirst()
                    .ifPresent(plan -> {
                        try {
                            billingService.subscribe(memberId, plan.getId());
                        } catch (Exception e) {
                            log.warn("Could not auto-subscribe member {}: {}", memberId, e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            log.error("Failed to process member event: {}", payload, e);
        }
    }
}
