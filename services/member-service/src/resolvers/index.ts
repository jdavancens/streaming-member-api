import { randomUUID } from 'crypto';
import { GetCommand, PutCommand } from '@aws-sdk/lib-dynamodb';
import { ddb } from '../datasources/dynamodb';
import { publish } from '../datasources/sns';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';

const TABLE = 'members';
const JWT_SECRET = process.env.JWT_SECRET ?? 'dev-secret-change-in-prod';
const TOPIC_ARN = process.env.MEMBER_EVENTS_TOPIC_ARN ?? '';

async function getMember(id: string) {
  const res = await ddb.send(new GetCommand({ TableName: TABLE, Key: { id } }));
  return res.Item ?? null;
}

export const resolvers = {
  Query: {
    member(_: unknown, { id }: { id: string }) {
      return getMember(id);
    },
  },

  Mutation: {
    async register(
      _: unknown,
      { input }: { input: { email: string; password: string; fullName: string; country: string } }
    ) {
      const id = randomUUID();
      const now = new Date().toISOString();
      const passwordHash = await bcrypt.hash(input.password, 10);

      const member = {
        id,
        email: input.email,
        passwordHash,
        fullName: input.fullName,
        country: input.country,
        status: 'ACTIVE',
        createdAt: now,
      };

      await ddb.send(new PutCommand({ TableName: TABLE, Item: member }));

      if (TOPIC_ARN) {
        await publish(TOPIC_ARN, { type: 'MemberRegistered', memberId: id, email: input.email });
      }

      const token = jwt.sign({ sub: id }, JWT_SECRET, { expiresIn: '7d' });

      return { member, token };
    },
  },

  Member: {
    __resolveReference({ id }: { id: string }) {
      return getMember(id);
    },
  },
};
