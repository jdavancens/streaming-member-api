import "@typespec/openapi3";

namespace MemberApi.Events;

@doc("Emitted on topic: member.events when a new member completes registration.")
model MemberRegisteredEvent {
  eventId: string;
  eventType: "MemberRegistered";
  occurredAt: utcDateTime;
  memberId: string;
  email: string;
  fullName: string;
  country: string;
}

@doc("Emitted on topic: member.events when a member cancels their account.")
model MemberCancelledEvent {
  eventId: string;
  eventType: "MemberCancelled";
  occurredAt: utcDateTime;
  memberId: string;
  cancellationReason?: string;
}

@doc("Emitted on topic: member.events when member profile details change.")
model MemberUpdatedEvent {
  eventId: string;
  eventType: "MemberUpdated";
  occurredAt: utcDateTime;
  memberId: string;
  changedFields: string[];
}

union MemberEvent = MemberRegisteredEvent | MemberCancelledEvent | MemberUpdatedEvent;
