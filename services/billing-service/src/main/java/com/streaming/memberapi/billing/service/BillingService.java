package com.streaming.memberapi.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.memberapi.billing.model.Plan;
import com.streaming.memberapi.billing.model.Subscription;
import com.streaming.memberapi.billing.repository.PlanRepository;
import com.streaming.memberapi.billing.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String TOPIC = "subscription.events";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public BillingService(PlanRepository planRepository,
                          SubscriptionRepository subscriptionRepository,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Plan> findAllPlans() {
        return planRepository.findAll();
    }

    public Optional<Plan> findPlanById(Long id) {
        return planRepository.findById(id);
    }

    @Transactional
    public Subscription subscribe(UUID memberId, Long planId) {
        Plan plan = planRepository.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")
            .ifPresent(s -> { throw new IllegalStateException("Member already has an active subscription"); });

        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setMemberId(memberId);
        sub.setPlan(plan);
        sub.setStatus("ACTIVE");
        sub.setPeriodStart(Instant.now());
        sub.setPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        sub = subscriptionRepository.save(sub);

        publishEvent("SubscriptionCreated", Map.of(
            "subscriptionId", sub.getId().toString(),
            "memberId", memberId.toString(),
            "planId", planId.toString(),
            "planName", plan.getName(),
            "maxStreams", plan.getMaxStreams(),
            "periodEnd", sub.getPeriodEnd().toString()
        ), memberId.toString());

        return sub;
    }

    @Transactional
    public Subscription cancel(UUID memberId) {
        Subscription sub = subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")
            .orElseThrow(() -> new IllegalStateException("No active subscription for member: " + memberId));

        sub.setStatus("CANCELLED");
        sub.setCancelledAt(Instant.now());
        sub = subscriptionRepository.save(sub);

        publishEvent("SubscriptionCancelled", Map.of(
            "subscriptionId", sub.getId().toString(),
            "memberId", memberId.toString(),
            "effectiveDate", sub.getPeriodEnd().toString()
        ), memberId.toString());

        return sub;
    }

    @Transactional
    public Subscription changePlan(UUID memberId, Long newPlanId) {
        Subscription sub = subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")
            .orElseThrow(() -> new IllegalStateException("No active subscription for member: " + memberId));

        Plan previousPlan = sub.getPlan();
        Plan newPlan = planRepository.findById(newPlanId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + newPlanId));

        sub.setPlan(newPlan);
        sub = subscriptionRepository.save(sub);

        publishEvent("PlanChanged", Map.of(
            "subscriptionId", sub.getId().toString(),
            "memberId", memberId.toString(),
            "previousPlanId", previousPlan.getId().toString(),
            "newPlanId", newPlan.getId().toString(),
            "newPlanName", newPlan.getName(),
            "newMaxStreams", newPlan.getMaxStreams()
        ), memberId.toString());

        return sub;
    }

    public Optional<Subscription> findActiveSubscription(UUID memberId) {
        return subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE");
    }

    private void publishEvent(String eventType, Map<String, Object> fields, String key) {
        try {
            Map<String, Object> event = new java.util.HashMap<>(fields);
            event.put("eventId", UUID.randomUUID().toString());
            event.put("eventType", eventType);
            event.put("occurredAt", Instant.now().toString());
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, key, payload);
        } catch (Exception e) {
            log.error("Failed to publish {} event", eventType, e);
        }
    }
}
