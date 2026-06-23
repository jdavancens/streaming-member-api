import { gql } from 'graphql-request';
import { memberClient, billingClient } from '../helpers/client';

const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) { member { id } }
  }
`;

const LIST_PLANS = gql`
  query { plans { id name monthlyPrice maxStreams videoQuality } }
`;

const SUBSCRIBE = gql`
  mutation Subscribe($input: SubscribeInput!) {
    subscribe(input: $input) {
      subscription { id status plan { name maxStreams } periodEnd }
    }
  }
`;

const CANCEL = gql`
  mutation Cancel($memberId: ID!) {
    cancelSubscription(memberId: $memberId) {
      subscription { id status cancelledAt }
    }
  }
`;

describe('billing-service', () => {
  let memberId: string;

  beforeAll(async () => {
    const res = await memberClient.request<{ register: { member: { id: string } } }>(REGISTER, {
      input: { email: `billing-test-${Date.now()}@example.com`, password: 'secret', fullName: 'Billing Test', country: 'US' },
    });
    memberId = res.register.member.id;
  });

  test('plans returns all 4 tiers', async () => {
    const res = await billingClient.request<{ plans: Array<{ name: string }> }>(LIST_PLANS);
    const names = res.plans.map(p => p.name);
    expect(names).toContain('MOBILE');
    expect(names).toContain('BASIC');
    expect(names).toContain('STANDARD');
    expect(names).toContain('PREMIUM');
  });

  test('PREMIUM plan has 4 max streams and UHD quality', async () => {
    const res = await billingClient.request<{ plans: Array<{ name: string; maxStreams: number; videoQuality: string }> }>(LIST_PLANS);
    const premium = res.plans.find(p => p.name === 'PREMIUM')!;
    expect(premium.maxStreams).toBe(4);
    expect(premium.videoQuality).toBe('UHD');
  });

  test('subscribe creates an active subscription', async () => {
    const res = await billingClient.request<{ subscribe: { subscription: { status: string; plan: { name: string } } } }>(
      SUBSCRIBE, { input: { memberId, planId: 'plan-standard' } }
    );
    expect(res.subscribe.subscription.status).toBe('ACTIVE');
    expect(res.subscribe.subscription.plan.name).toBe('STANDARD');
  });

  test('cancelSubscription marks subscription cancelled', async () => {
    const res = await billingClient.request<{ cancelSubscription: { subscription: { status: string; cancelledAt: string } } }>(
      CANCEL, { memberId }
    );
    expect(res.cancelSubscription.subscription.status).toBe('CANCELLED');
    expect(res.cancelSubscription.subscription.cancelledAt).toBeTruthy();
  });
});
