import { gql } from 'graphql-request';
import { client } from '../helpers/client';

const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) { member { id } }
  }
`;

const CAN_STREAM = gql`
  query CanStream($memberId: ID!) {
    member(id: $memberId) {
      canStream { allowed concurrentStreams maxStreams reason }
    }
  }
`;

const ACQUIRE = gql`
  mutation AcquireStream($memberId: ID!, $deviceId: String!) {
    acquireStream(memberId: $memberId, deviceId: $deviceId) { streamId expiresAt }
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

describe('Stream slot lifecycle (via router)', () => {
  let memberId: string;
  const streamIds: string[] = [];

  beforeAll(async () => {
    const res = await client.request<{ register: { member: { id: string } } }>(REGISTER, {
      input: { email: `stream-test-${Date.now()}@example.com`, password: 'secret', fullName: 'Stream Test', country: 'US' },
    });
    memberId = res.register.member.id;
  });

  test('canStream shows allowed with 0 concurrent before acquiring', async () => {
    const res = await client.request<{ member: { canStream: { allowed: boolean; concurrentStreams: number } } }>(
      CAN_STREAM, { memberId }
    );
    expect(res.member.canStream.allowed).toBe(true);
    expect(res.member.canStream.concurrentStreams).toBe(0);
  });

  test('acquireStream slot 1 returns a streamId with future expiry', async () => {
    const res = await client.request<{ acquireStream: { streamId: string; expiresAt: string } }>(
      ACQUIRE, { memberId, deviceId: 'device-1' }
    );
    expect(res.acquireStream.streamId).toBeTruthy();
    expect(new Date(res.acquireStream.expiresAt).getTime()).toBeGreaterThan(Date.now());
    streamIds.push(res.acquireStream.streamId);
  });

  test('canStream shows 1 concurrent stream after acquiring', async () => {
    const res = await client.request<{ member: { canStream: { concurrentStreams: number } } }>(
      CAN_STREAM, { memberId }
    );
    expect(res.member.canStream.concurrentStreams).toBe(1);
  });

  test('acquireStream slot 2 succeeds (default limit is 2)', async () => {
    const res = await client.request<{ acquireStream: { streamId: string } }>(
      ACQUIRE, { memberId, deviceId: 'device-2' }
    );
    streamIds.push(res.acquireStream.streamId);
    expect(res.acquireStream.streamId).toBeTruthy();
  });

  test('canStream shows not allowed after reaching limit', async () => {
    const res = await client.request<{ member: { canStream: { allowed: boolean; reason: string } } }>(
      CAN_STREAM, { memberId }
    );
    expect(res.member.canStream.allowed).toBe(false);
    expect(res.member.canStream.reason).toBeTruthy();
  });

  test('heartbeat extends slot TTL without error', async () => {
    const res = await client.request<{ heartbeatStream: boolean }>(
      HEARTBEAT, { memberId, streamId: streamIds[0] }
    );
    expect(res.heartbeatStream).toBe(true);
  });

  test('releasing a slot allows a new acquisition', async () => {
    await client.request(RELEASE, { memberId, streamId: streamIds[0] });
    const res = await client.request<{ acquireStream: { streamId: string } }>(
      ACQUIRE, { memberId, deviceId: 'device-3' }
    );
    expect(res.acquireStream.streamId).toBeTruthy();
    streamIds.push(res.acquireStream.streamId);
  });

  afterAll(async () => {
    for (const streamId of streamIds) {
      await client.request(RELEASE, { memberId, streamId }).catch(() => {});
    }
  });
});
