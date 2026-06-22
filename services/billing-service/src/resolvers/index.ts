import { randomUUID } from 'crypto';
import { getPool } from '../datasources/mysql';
import { publish } from '../datasources/sns';
import type { RowDataPacket } from 'mysql2';

const TOPIC_ARN = process.env.SUBSCRIPTION_EVENTS_TOPIC_ARN ?? '';

interface PlanRow extends RowDataPacket {
  id: string;
  name: string;
  monthly_price: number;
  max_streams: number;
  max_downloads: number;
  video_quality: string;
}

interface SubRow extends RowDataPacket {
  id: string;
  member_id: string;
  plan_id: string;
  status: string;
  period_start: Date;
  period_end: Date;
  cancelled_at: Date | null;
}

function mapPlan(row: PlanRow) {
  return {
    id: row.id,
    name: row.name,
    monthlyPrice: row.monthly_price,
    maxStreams: row.max_streams,
    maxDownloads: row.max_downloads,
    videoQuality: row.video_quality,
  };
}

function mapSub(row: SubRow) {
  return {
    id: row.id,
    planId: row.plan_id,
    status: row.status,
    periodStart: row.period_start.toISOString(),
    periodEnd: row.period_end.toISOString(),
    cancelledAt: row.cancelled_at ? row.cancelled_at.toISOString() : null,
  };
}

async function getSubscriptionByMember(memberId: string) {
  const db = getPool();
  const [rows] = await db.query<SubRow[]>(
    'SELECT * FROM subscriptions WHERE member_id = ? AND status != ? ORDER BY period_start DESC LIMIT 1',
    [memberId, 'CANCELLED']
  );
  return rows[0] ? mapSub(rows[0]) : null;
}

async function getPlanById(planId: string) {
  const db = getPool();
  const [rows] = await db.query<PlanRow[]>('SELECT * FROM plans WHERE id = ?', [planId]);
  return rows[0] ? mapPlan(rows[0]) : null;
}

export const resolvers = {
  Query: {
    async plans() {
      const db = getPool();
      const [rows] = await db.query<PlanRow[]>('SELECT * FROM plans');
      return rows.map(mapPlan);
    },
    async plan(_: unknown, { id }: { id: string }) {
      return getPlanById(id);
    },
  },

  Mutation: {
    async subscribe(_: unknown, { input }: { input: { memberId: string; planId: string } }) {
      const db = getPool();
      const id = randomUUID();
      const now = new Date();
      const periodEnd = new Date(now);
      periodEnd.setMonth(periodEnd.getMonth() + 1);

      await db.query(
        'INSERT INTO subscriptions (id, member_id, plan_id, status, period_start, period_end) VALUES (?,?,?,?,?,?)',
        [id, input.memberId, input.planId, 'ACTIVE', now, periodEnd]
      );

      if (TOPIC_ARN) {
        await publish(TOPIC_ARN, {
          type: 'SubscriptionCreated',
          memberId: input.memberId,
          planId: input.planId,
          subscriptionId: id,
        });
      }

      const [rows] = await db.query<SubRow[]>('SELECT * FROM subscriptions WHERE id = ?', [id]);
      return { subscription: { ...mapSub(rows[0]), planId: input.planId } };
    },

    async cancelSubscription(_: unknown, { memberId }: { memberId: string }) {
      const db = getPool();
      const now = new Date();
      await db.query(
        'UPDATE subscriptions SET status = ?, cancelled_at = ? WHERE member_id = ? AND status = ?',
        ['CANCELLED', now, memberId, 'ACTIVE']
      );

      const [rows] = await db.query<SubRow[]>(
        'SELECT * FROM subscriptions WHERE member_id = ? ORDER BY period_start DESC LIMIT 1',
        [memberId]
      );

      if (TOPIC_ARN && rows[0]) {
        await publish(TOPIC_ARN, {
          type: 'SubscriptionCancelled',
          memberId,
          subscriptionId: rows[0].id,
        });
      }

      return { subscription: mapSub(rows[0]) };
    },

    async changePlan(_: unknown, { memberId, planId }: { memberId: string; planId: string }) {
      const db = getPool();
      await db.query(
        'UPDATE subscriptions SET plan_id = ? WHERE member_id = ? AND status = ?',
        [planId, memberId, 'ACTIVE']
      );

      const [rows] = await db.query<SubRow[]>(
        'SELECT * FROM subscriptions WHERE member_id = ? AND status = ?',
        [memberId, 'ACTIVE']
      );

      if (TOPIC_ARN && rows[0]) {
        await publish(TOPIC_ARN, {
          type: 'SubscriptionChanged',
          memberId,
          planId,
          subscriptionId: rows[0].id,
        });
      }

      return { subscription: mapSub(rows[0]) };
    },
  },

  Member: {
    __resolveReference(member: { id: string }) {
      return member;
    },
    subscription(member: { id: string }) {
      return getSubscriptionByMember(member.id);
    },
  },

  Subscription: {
    plan(sub: { planId: string }) {
      return getPlanById(sub.planId);
    },
  },
};
