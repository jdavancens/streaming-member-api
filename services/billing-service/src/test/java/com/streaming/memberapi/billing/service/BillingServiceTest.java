package com.streaming.memberapi.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.memberapi.billing.model.Plan;
import com.streaming.memberapi.billing.model.Subscription;
import com.streaming.memberapi.billing.repository.PlanRepository;
import com.streaming.memberapi.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock
    PlanRepository planRepository;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BillingService billingService;

    private BillingService service() {
        return new BillingService(planRepository, subscriptionRepository, kafkaTemplate, objectMapper);
    }

    private Plan plan(Long id, String name, int maxStreams) {
        Plan p = new Plan();
        p.setId(id);
        p.setName(name);
        p.setMaxStreams(maxStreams);
        p.setMonthlyPrice(new BigDecimal("9.99"));
        p.setMaxDownloads(10);
        p.setVideoQuality("HD");
        return p;
    }

    @Test
    void findAllPlans_delegatesToRepository() {
        billingService = service();
        List<Plan> plans = List.of(plan(1L, "PREMIUM", 4));
        when(planRepository.findAll()).thenReturn(plans);

        assertThat(billingService.findAllPlans()).isEqualTo(plans);
    }

    @Test
    void findPlanById_delegatesToRepository() {
        billingService = service();
        Plan p = plan(1L, "PREMIUM", 4);
        when(planRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThat(billingService.findPlanById(1L)).contains(p);
    }

    @Test
    void subscribe_createsActiveSubscriptionAndPublishesEvent() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        Plan p = plan(3L, "PREMIUM", 4);
        when(planRepository.findById(3L)).thenReturn(Optional.of(p));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription sub = billingService.subscribe(memberId, 3L);

        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getMemberId()).isEqualTo(memberId);
        assertThat(sub.getPlan()).isEqualTo(p);
        assertThat(sub.getPeriodEnd()).isAfter(sub.getPeriodStart());
        verify(kafkaTemplate).send(eqTopic(), anyString(), anyString());
    }

    @Test
    void subscribe_throwsWhenPlanMissing() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.subscribe(memberId, 99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plan not found");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_throwsWhenMemberAlreadyHasActiveSubscription() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        Plan p = plan(3L, "PREMIUM", 4);
        when(planRepository.findById(3L)).thenReturn(Optional.of(p));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE"))
            .thenReturn(Optional.of(new Subscription()));

        assertThatThrownBy(() -> billingService.subscribe(memberId, 3L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has an active subscription");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancel_marksSubscriptionCancelledAndPublishesEvent() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setMemberId(memberId);
        existing.setStatus("ACTIVE");
        existing.setPeriodEnd(Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = billingService.cancel(memberId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getCancelledAt()).isNotNull();
        verify(kafkaTemplate).send(eqTopic(), anyString(), anyString());
    }

    @Test
    void cancel_throwsWhenNoActiveSubscription() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.cancel(memberId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No active subscription");
    }

    @Test
    void changePlan_updatesPlanAndPublishesEvent() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        Plan oldPlan = plan(2L, "BASIC", 1);
        Plan newPlan = plan(3L, "PREMIUM", 4);
        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setMemberId(memberId);
        existing.setStatus("ACTIVE");
        existing.setPlan(oldPlan);
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.of(existing));
        when(planRepository.findById(3L)).thenReturn(Optional.of(newPlan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = billingService.changePlan(memberId, 3L);

        assertThat(result.getPlan()).isEqualTo(newPlan);
        verify(kafkaTemplate).send(eqTopic(), anyString(), anyString());
    }

    @Test
    void changePlan_throwsWhenNoActiveSubscription() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.changePlan(memberId, 3L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No active subscription");
    }

    @Test
    void changePlan_throwsWhenNewPlanMissing() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setStatus("ACTIVE");
        existing.setPlan(plan(2L, "BASIC", 1));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.of(existing));
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.changePlan(memberId, 99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plan not found");
    }

    @Test
    void findActiveSubscription_delegatesToRepository() {
        billingService = service();
        UUID memberId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE")).thenReturn(Optional.of(sub));

        assertThat(billingService.findActiveSubscription(memberId)).contains(sub);
    }

    private static String eqTopic() {
        return org.mockito.ArgumentMatchers.eq("subscription.events");
    }
}
