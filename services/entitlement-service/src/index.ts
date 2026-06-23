import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers, updateMaxStreams } from './resolvers';
import { poll } from './datasources/sqs';

async function main() {
  const server = new ApolloServer({
    schema: buildSubgraphSchema({ typeDefs, resolvers }),
  });

  const port = parseInt(process.env.PORT ?? '8084', 10);
  const { url } = await startStandaloneServer(server, { listen: { port, host: '0.0.0.0' } });
  console.log(`entitlement-service ready at ${url}`);

  const queueUrl = process.env.ENT_SUBSCRIPTION_EVENTS_QUEUE_URL;
  if (queueUrl) {
    poll(queueUrl, async (event) => {
      const e = event as { type?: string; memberId?: string; maxStreams?: number };
      if (e.type === 'SubscriptionChanged' && e.memberId && e.maxStreams !== undefined) {
        updateMaxStreams(e.memberId, e.maxStreams);
      }
    }).catch((err) => console.error('SQS polling error', err));
  }
}

main().catch((err) => { console.error('Failed to start entitlement-service:', err); process.exit(1); });
