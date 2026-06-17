package com.streaming.memberapi.billing.datafetcher;

import com.streaming.memberapi.billing.model.Plan;
import com.streaming.memberapi.billing.model.Subscription;
import com.streaming.memberapi.billing.service.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingDataFetcherTest {

    @Mock
    BillingService billingService;

    @InjectMocks
    BillingDataFetcher dataFetcher;

    private Plan plan(Long id) {
        Plan p = new Plan();
        p.setId(id);
        p.setName("PREMIUM");
        return p;
    }

    @Test
    void plans_returnsAllPlans() {
        List<Plan> plans = List.of(plan(1L));
        when(billingService.findAllPlans()).thenReturn(plans);

        assertThat(dataFetcher.plans()).isEqualTo(plans);
    }

    @Test
    void plan_returnsPlanWhenFound() {
        Plan p = plan(2L);
        when(billingService.findPlanById(2L)).thenReturn(Optional.of(p));

        assertThat(dataFetcher.plan("2")).isEqualTo(p);
    }

    @Test
    void plan_returnsNullWhenMissing() {
        when(billingService.findPlanById(2L)).thenReturn(Optional.empty());

        assertThat(dataFetcher.plan("2")).isNull();
    }

    @Test
    void subscribe_wrapsSubscriptionInMap() {
        UUID memberId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(billingService.subscribe(memberId, 3L)).thenReturn(sub);

        Map<String, Subscription> result = dataFetcher.subscribe(
            Map.of("memberId", memberId.toString(), "planId", "3"));

        assertThat(result).containsEntry("subscription", sub);
    }

    @Test
    void cancelSubscription_wrapsSubscriptionInMap() {
        UUID memberId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(billingService.cancel(memberId)).thenReturn(sub);

        assertThat(dataFetcher.cancelSubscription(memberId.toString()))
            .containsEntry("subscription", sub);
    }

    @Test
    void changePlan_wrapsSubscriptionInMap() {
        UUID memberId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(billingService.changePlan(memberId, 5L)).thenReturn(sub);

        assertThat(dataFetcher.changePlan(memberId.toString(), "5"))
            .containsEntry("subscription", sub);
    }

    @Test
    void resolveMemberEntity_returnsSubscriptionWhenActive() {
        UUID memberId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(billingService.findActiveSubscription(memberId)).thenReturn(Optional.of(sub));

        Map<String, Object> result = dataFetcher.resolveMemberEntity(Map.of("id", memberId.toString()));

        assertThat(result).containsEntry("id", memberId.toString());
        assertThat(result.get("subscription")).isEqualTo(sub);
    }

    @Test
    void resolveMemberEntity_returnsEmptyMapWhenNoActiveSubscription() {
        UUID memberId = UUID.randomUUID();
        when(billingService.findActiveSubscription(any())).thenReturn(Optional.empty());

        Map<String, Object> result = dataFetcher.resolveMemberEntity(Map.of("id", memberId.toString()));

        assertThat(result).containsEntry("id", memberId.toString());
        assertThat(result.get("subscription")).isEqualTo(Map.of());
    }
}
