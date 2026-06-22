import express from 'express';
import cors from 'cors';
import bodyParser from 'body-parser';
import { ApolloServer } from '@apollo/server';
import { expressMiddleware } from '@apollo/server/express4';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers, updateMaxStreams } from './resolvers';
import { poll } from './datasources/sqs';

async function main() {
  const app = express();
  const port = parseInt(process.env.PORT ?? '8084', 10);

  const server = new ApolloServer({
    schema: buildSubgraphSchema({ typeDefs, resolvers }),
  });

  await server.start();

  app.use(
    '/',
    cors<cors.CorsRequest>(),
    bodyParser.json(),
    expressMiddleware(server),
  );

  app.listen(port, '0.0.0.0', () => {
    console.log(`entitlement-service ready at http://0.0.0.0:${port}`);
  });

  // SQS consumer: update in-memory maxStreams cache on subscription changes
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

main().catch((err) => {
  console.error('Failed to start entitlement-service:', err);
  process.exit(1);
});
