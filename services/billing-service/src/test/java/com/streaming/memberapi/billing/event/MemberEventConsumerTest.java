package com.streaming.memberapi.billing.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.memberapi.billing.model.Plan;
import com.streaming.memberapi.billing.repository.PlanRepository;
import com.streaming.memberapi.billing.service.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberEventConsumerTest {

    @Mock
    BillingService billingService;
    @Mock
    PlanRepository planRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MemberEventConsumer consumer() {
        return new MemberEventConsumer(billingService, planRepository, objectMapper);
    }

    private Plan basicPlan() {
        Plan p = new Plan();
        p.setId(2L);
        p.setName("BASIC");
        return p;
    }

    @Test
    void memberRegistered_autoSubscribesToBasicPlan() {
        MemberEventConsumer c = consumer();
        UUID memberId = UUID.randomUUID();
        when(planRepository.findAll()).thenReturn(List.of(basicPlan()));

        String payload = "{\"eventType\":\"MemberRegistered\",\"memberId\":\"" + memberId + "\"}";
        c.onMemberEvent(payload);

        verify(billingService).subscribe(memberId, 2L);
    }

    @Test
    void memberRegistered_doesNothingWhenNoBasicPlan() {
        MemberEventConsumer c = consumer();
        UUID memberId = UUID.randomUUID();
        Plan premium = new Plan();
        premium.setId(3L);
        premium.setName("PREMIUM");
        when(planRepository.findAll()).thenReturn(List.of(premium));

        c.onMemberEvent("{\"eventType\":\"MemberRegistered\",\"memberId\":\"" + memberId + "\"}");

        verify(billingService, never()).subscribe(any(), anyLong());
    }

    @Test
    void memberRegistered_swallowsSubscribeFailure() {
        MemberEventConsumer c = consumer();
        UUID memberId = UUID.randomUUID();
        when(planRepository.findAll()).thenReturn(List.of(basicPlan()));
        when(billingService.subscribe(eq(memberId), eq(2L)))
            .thenThrow(new IllegalStateException("already subscribed"));

        // Should not propagate
        c.onMemberEvent("{\"eventType\":\"MemberRegistered\",\"memberId\":\"" + memberId + "\"}");

        verify(billingService).subscribe(memberId, 2L);
    }

    @Test
    void otherEventTypes_areIgnored() {
        MemberEventConsumer c = consumer();
        UUID memberId = UUID.randomUUID();

        c.onMemberEvent("{\"eventType\":\"MemberCancelled\",\"memberId\":\"" + memberId + "\"}");

        verify(planRepository, never()).findAll();
        verify(billingService, never()).subscribe(any(), anyLong());
    }

    @Test
    void malformedPayload_doesNotThrow() {
        MemberEventConsumer c = consumer();

        c.onMemberEvent("not-json");

        verify(billingService, never()).subscribe(any(), anyLong());
    }
}
