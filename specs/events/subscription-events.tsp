import "@typespec/openapi3";

namespace MemberApi.Events;

@doc("Emitted on topic: subscription.events when a member subscribes to a plan.")
model SubscriptionCreatedEvent {
  eventId: string;
  eventType: "SubscriptionCreated";
  occurredAt: utcDateTime;
  subscriptionId: string;
  memberId: string;
  planId: string;
  planName: string;
  maxStreams: int32;
  periodEnd: utcDateTime;
}

@doc("Emitted on topic: subscription.events when a member cancels their subscription.")
model SubscriptionCancelledEvent {
  eventId: string;
  eventType: "SubscriptionCancelled";
  occurredAt: utcDateTime;
  subscriptionId: string;
  memberId: string;
  effectiveDate: utcDateTime;
}

@doc("Emitted on topic: subscription.events when a member changes their plan tier.")
model PlanChangedEvent {
  eventId: string;
  eventType: "PlanChanged";
  occurredAt: utcDateTime;
  subscriptionId: string;
  memberId: string;
  previousPlanId: string;
  newPlanId: string;
  newPlanName: string;
  newMaxStreams: int32;
}

@doc("Emitted on topic: subscription.events when a billing cycle renews successfully.")
model SubscriptionRenewedEvent {
  eventId: string;
  eventType: "SubscriptionRenewed";
  occurredAt: utcDateTime;
  subscriptionId: string;
  memberId: string;
  newPeriodEnd: utcDateTime;
  amountCharged: float64;
}

union SubscriptionEvent =
  | SubscriptionCreatedEvent
  | SubscriptionCancelledEvent
  | PlanChangedEvent
  | SubscriptionRenewedEvent;
