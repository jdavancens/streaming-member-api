import { gql } from 'graphql-request';
import { memberClient, entitlementClient } from '../helpers/client';

const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) { member { id } }
  }
`;

const ACQUIRE = gql`
  mutation Acquire($memberId: ID!, $deviceId: String!) {
    acquireStream(memberId: $memberId, deviceId: $deviceId) {
      streamId expiresAt
    }
  }
`;

const HEARTBEAT = gql`
  mutation Heartbeat($memberId: ID!, $streamId: ID!) {
    heartbeatStream(memberId: $memberId, streamId: $streamId)
  }
`;

const RELEASE = gql`
  mutation Release($memberId: ID!, $streamId: ID!) {
    releaseStream(memberId: $memberId, streamId: $streamId)
  }
`;

describe('entitlement-service', () => {
  let memberId: string;
  let streamId: string;

  beforeAll(async () => {
    const res = await memberClient.request<{ register: { member: { id: string } } }>(REGISTER, {
      input: { email: `entitlement-test-${Date.now()}@example.com`, password: 'secret', fullName: 'Entitlement Test', country: 'US' },
    });
    memberId = res.register.member.id;
  });

  test('acquireStream returns a streamId with future expiry', async () => {
    const res = await entitlementClient.request<{ acquireStream: { streamId: string; expiresAt: string } }>(
      ACQUIRE, { memberId, deviceId: 'device-test-1' }
    );
    expect(res.acquireStream.streamId).toBeTruthy();
    expect(new Date(res.acquireStream.expiresAt).getTime()).toBeGreaterThan(Date.now());
    streamId = res.acquireStream.streamId;
  });

  test('heartbeatStream extends the slot TTL', async () => {
    const res = await entitlementClient.request<{ heartbeatStream: boolean }>(
      HEARTBEAT, { memberId, streamId }
    );
    expect(res.heartbeatStream).toBe(true);
  });

  test('acquireStream up to default limit (2) succeeds', async () => {
    const res = await entitlementClient.request<{ acquireStream: { streamId: string } }>(
      ACQUIRE, { memberId, deviceId: 'device-test-2' }
    );
    expect(res.acquireStream.streamId).toBeTruthy();
  });

  test('acquireStream beyond limit throws', async () => {
    await expect(
      entitlementClient.request(ACQUIRE, { memberId, deviceId: 'device-test-3' })
    ).rejects.toThrow();
  });

  test('releaseStream frees the slot', async () => {
    const res = await entitlementClient.request<{ releaseStream: boolean }>(
      RELEASE, { memberId, streamId }
    );
    expect(res.releaseStream).toBe(true);
  });
});
