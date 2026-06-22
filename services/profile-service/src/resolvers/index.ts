import { randomUUID } from 'crypto';
import { createHash } from 'crypto';
import { QueryCommand, PutCommand, UpdateCommand, ScanCommand } from '@aws-sdk/lib-dynamodb';
import { ddb } from '../datasources/dynamodb';

const TABLE = 'profiles';

interface CreateProfileInput {
  name: string;
  avatarUrl?: string;
  isKids?: boolean;
  language?: string;
  pin?: string;
}

interface UpdateProfileInput {
  name?: string;
  avatarUrl?: string;
  language?: string;
}

async function getProfileById(profileId: string) {
  // Full scan filtered by id — acceptable at dev scale.
  // In prod, add a GSI on `id`.
  const res = await ddb.send(new ScanCommand({
    TableName: TABLE,
    FilterExpression: 'id = :id AND (attribute_not_exists(deleted) OR deleted = :f)',
    ExpressionAttributeValues: { ':id': profileId, ':f': false },
  }));
  return res.Items?.[0] ?? null;
}

export const resolvers = {
  Member: {
    __resolveReference(member: { id: string }) {
      return member;
    },
    async profiles(member: { id: string }) {
      const res = await ddb.send(new QueryCommand({
        TableName: TABLE,
        KeyConditionExpression: 'memberId = :mid',
        FilterExpression: 'attribute_not_exists(deleted) OR deleted = :f',
        ExpressionAttributeValues: { ':mid': member.id, ':f': false },
      }));
      return res.Items ?? [];
    },
  },

  Mutation: {
    async createProfile(
      _: unknown,
      { memberId, input }: { memberId: string; input: CreateProfileInput }
    ) {
      const id = randomUUID();
      const profile = {
        memberId,
        id,
        name: input.name,
        avatarUrl: input.avatarUrl ?? null,
        isKids: input.isKids ?? false,
        language: input.language ?? 'en',
        hasPinLock: !!input.pin,
        pinHash: input.pin ? createHash('sha256').update(input.pin).digest('hex') : null,
        deleted: false,
      };
      await ddb.send(new PutCommand({ TableName: TABLE, Item: profile }));
      return profile;
    },

    async updateProfile(
      _: unknown,
      { profileId, input }: { profileId: string; input: UpdateProfileInput }
    ) {
      const existing = await getProfileById(profileId);
      if (!existing) throw new Error('Profile not found');

      const updates: string[] = [];
      const values: Record<string, unknown> = {};

      if (input.name !== undefined) { updates.push('name = :n'); values[':n'] = input.name; }
      if (input.avatarUrl !== undefined) { updates.push('avatarUrl = :a'); values[':a'] = input.avatarUrl; }
      if (input.language !== undefined) { updates.push('language = :l'); values[':l'] = input.language; }

      if (updates.length > 0) {
        await ddb.send(new UpdateCommand({
          TableName: TABLE,
          Key: { memberId: existing.memberId, id: profileId },
          UpdateExpression: 'SET ' + updates.join(', '),
          ExpressionAttributeValues: values,
        }));
      }

      return { ...existing, ...input };
    },

    async deleteProfile(_: unknown, { profileId }: { profileId: string }) {
      const existing = await getProfileById(profileId);
      if (!existing) return false;
      await ddb.send(new UpdateCommand({
        TableName: TABLE,
        Key: { memberId: existing.memberId, id: profileId },
        UpdateExpression: 'SET deleted = :t',
        ExpressionAttributeValues: { ':t': true },
      }));
      return true;
    },

    async verifyProfilePin(_: unknown, { profileId, pin }: { profileId: string; pin: string }) {
      const existing = await getProfileById(profileId);
      if (!existing || !existing.hasPinLock) return false;
      const hash = createHash('sha256').update(pin).digest('hex');
      return existing.pinHash === hash;
    },
  },
};
