import { randomUUID } from 'crypto';
import { PutCommand, DeleteCommand, QueryCommand, UpdateCommand } from '@aws-sdk/lib-dynamodb';
import { ddb } from '../datasources/dynamodb';

const TABLE = 'stream_slots';
const DEFAULT_MAX_STREAMS = 2;
const TTL_SECONDS = parseInt(process.env.STREAM_SLOT_TTL_SECONDS ?? '14400', 10);

// In-memory plan cache keyed by memberId. Updated by SQS consumer.
const maxStreamsCache = new Map<string, number>();

function getMaxStreams(memberId: string): number {
  return maxStreamsCache.get(memberId) ?? DEFAULT_MAX_STREAMS;
}

export function updateMaxStreams(memberId: string, maxStreams: number): void {
  maxStreamsCache.set(memberId, maxStreams);
}

async function getActiveSlots(memberId: string) {
  const now = Math.floor(Date.now() / 1000);
  const res = await ddb.send(new QueryCommand({
    TableName: TABLE,
    IndexName: 'memberId-index',
    KeyConditionExpression: 'memberId = :mid',
    FilterExpression: 'expiresAt > :now',
    ExpressionAttributeValues: { ':mid': memberId, ':now': now },
  }));
  return res.Items ?? [];
}

export const resolvers = {
  Member: {
    __resolveReference(member: { id: string }) {
      return member;
    },
    async canStream(member: { id: string }) {
      const slots = await getActiveSlots(member.id);
      const maxStreams = getMaxStreams(member.id);
      const concurrentStreams = slots.length;
      const allowed = concurrentStreams < maxStreams;
      return {
        allowed,
        reason: allowed ? null : 'Maximum concurrent streams reached',
        concurrentStreams,
        maxStreams,
      };
    },
  },

  Mutation: {
    async acquireStream(_: unknown, { memberId, deviceId }: { memberId: string; deviceId: string }) {
      const slots = await getActiveSlots(memberId);
      const maxStreams = getMaxStreams(memberId);

      if (slots.length >= maxStreams) {
        throw new Error('Maximum concurrent streams reached');
      }

      const streamId = randomUUID();
      const expiresAt = Math.floor(Date.now() / 1000) + TTL_SECONDS;

      await ddb.send(new PutCommand({
        TableName: TABLE,
        Item: { streamId, memberId, deviceId, expiresAt },
      }));

      return { streamId, expiresAt: new Date(expiresAt * 1000).toISOString() };
    },

    async releaseStream(_: unknown, { memberId, streamId }: { memberId: string; streamId: string }) {
      await ddb.send(new DeleteCommand({
        TableName: TABLE,
        Key: { streamId },
      }));
      return true;
    },

    async heartbeatStream(_: unknown, { memberId, streamId }: { memberId: string; streamId: string }) {
      const expiresAt = Math.floor(Date.now() / 1000) + TTL_SECONDS;
      try {
        await ddb.send(new UpdateCommand({
          TableName: TABLE,
          Key: { streamId },
          UpdateExpression: 'SET expiresAt = :exp',
          ConditionExpression: 'memberId = :mid',
          ExpressionAttributeValues: { ':exp': expiresAt, ':mid': memberId },
        }));
        return true;
      } catch {
        return false;
      }
    },
  },
};
