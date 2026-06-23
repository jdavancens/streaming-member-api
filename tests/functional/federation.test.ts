import { gql } from 'graphql-request';
import { client } from '../helpers/client';

const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) { member { id email } }
  }
`;

const SUBSCRIBE = gql`
  mutation Subscribe($input: SubscribeInput!) {
    subscribe(input: $input) { subscription { id status } }
  }
`;

const CREATE_PROFILE = gql`
  mutation CreateProfile($memberId: ID!, $input: CreateProfileInput!) {
    createProfile(memberId: $memberId, input: $input) { id name }
  }
`;

const MEMBER_DASHBOARD = gql`
  query MemberDashboard($memberId: ID!) {
    member(id: $memberId) {
      email
      fullName
      subscription {
        plan { name maxStreams }
        status
        periodEnd
      }
      profiles {
        name
        isKids
      }
      canStream {
        allowed
        concurrentStreams
        maxStreams
      }
    }
  }
`;

describe('Federation: MemberDashboard query', () => {
  let memberId: string;

  beforeAll(async () => {
    const reg = await client.request<{ register: { member: { id: string } } }>(REGISTER, {
      input: { email: `fed-test-${Date.now()}@example.com`, password: 'secret', fullName: 'Federation Test', country: 'US' },
    });
    memberId = reg.register.member.id;
    await client.request(SUBSCRIBE, { input: { memberId, planId: 'plan-premium' } });
    await client.request(CREATE_PROFILE, { memberId, input: { name: 'Main', isKids: false } });
  });

  test('stitches fields from all 4 subgraphs in a single query', async () => {
    const res = await client.request<{
      member: {
        email: string;
        subscription: { plan: { name: string; maxStreams: number }; status: string };
        profiles: Array<{ name: string }>;
        canStream: { allowed: boolean; concurrentStreams: number; maxStreams: number };
      };
    }>(MEMBER_DASHBOARD, { memberId });

    expect(res.member.email).toContain('@example.com');
    expect(res.member.subscription.plan.name).toBe('PREMIUM');
    expect(res.member.subscription.plan.maxStreams).toBe(4);
    expect(res.member.subscription.status).toBe('ACTIVE');
    expect(res.member.profiles).toHaveLength(1);
    expect(res.member.profiles[0].name).toBe('Main');
    expect(res.member.canStream.allowed).toBe(true);
    expect(res.member.canStream.concurrentStreams).toBe(0);
  });

  test('member returns null for unknown id', async () => {
    const res = await client.request<{ member: null }>(
      gql`query { member(id: "does-not-exist") { email } }`
    );
    expect(res.member).toBeNull();
  });
});
