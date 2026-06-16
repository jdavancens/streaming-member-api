package com.streaming.memberapi.billing.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.streaming.memberapi.billing.model.Plan;
import com.streaming.memberapi.billing.model.Subscription;
import com.streaming.memberapi.billing.service.BillingService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@DgsComponent
public class BillingDataFetcher {

    private final BillingService billingService;

    public BillingDataFetcher(BillingService billingService) {
        this.billingService = billingService;
    }

    @DgsQuery
    public List<Plan> plans() {
        return billingService.findAllPlans();
    }

    @DgsQuery
    public Plan plan(@InputArgument String id) {
        return billingService.findPlanById(Long.parseLong(id)).orElse(null);
    }

    @DgsMutation
    public Map<String, Subscription> subscribe(@InputArgument Map<String, String> input) {
        UUID memberId = UUID.fromString(input.get("memberId"));
        Long planId = Long.parseLong(input.get("planId"));
        return Map.of("subscription", billingService.subscribe(memberId, planId));
    }

    @DgsMutation
    public Map<String, Subscription> cancelSubscription(@InputArgument String memberId) {
        return Map.of("subscription", billingService.cancel(UUID.fromString(memberId)));
    }

    @DgsMutation
    public Map<String, Subscription> changePlan(@InputArgument String memberId,
                                                 @InputArgument String planId) {
        return Map.of("subscription", billingService.changePlan(
            UUID.fromString(memberId), Long.parseLong(planId)));
    }

    // Federation: resolve Member's subscription field
    @DgsEntityFetcher(name = "Member")
    public Map<String, Object> resolveMemberEntity(Map<String, Object> values) {
        String memberId = (String) values.get("id");
        Subscription sub = billingService.findActiveSubscription(UUID.fromString(memberId)).orElse(null);
        return Map.of("id", memberId, "subscription", sub != null ? sub : Map.of());
    }
}
