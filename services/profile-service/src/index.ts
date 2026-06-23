import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers } from './resolvers';
import { poll } from './datasources/sqs';

async function main() {
  const server = new ApolloServer({
    schema: buildSubgraphSchema({ typeDefs, resolvers }),
  });

  const port = parseInt(process.env.PORT ?? '8083', 10);
  const { url } = await startStandaloneServer(server, { listen: { port, host: '0.0.0.0' } });
  console.log(`profile-service ready at ${url}`);

  const queueUrl = process.env.PROFILE_MEMBER_EVENTS_QUEUE_URL;
  if (queueUrl) {
    poll(queueUrl, async (event) => {
      const e = event as { type?: string; memberId?: string };
      if (e.type === 'MemberCancelled' && e.memberId) {
        console.log(`Soft-deleting profiles for cancelled member ${e.memberId}`);
      }
    }).catch((err) => console.error('SQS polling error', err));
  }
}

main().catch((err) => { console.error('Failed to start profile-service:', err); process.exit(1); });
