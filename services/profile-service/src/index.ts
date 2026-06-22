import express from 'express';
import cors from 'cors';
import bodyParser from 'body-parser';
import { ApolloServer } from '@apollo/server';
import { expressMiddleware } from '@apollo/server/express4';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers } from './resolvers';
import { poll } from './datasources/sqs';

async function main() {
  const app = express();
  const port = parseInt(process.env.PORT ?? '8083', 10);

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
    console.log(`profile-service ready at http://0.0.0.0:${port}`);
  });

  // SQS consumer runs in background
  const queueUrl = process.env.PROFILE_MEMBER_EVENTS_QUEUE_URL;
  if (queueUrl) {
    poll(queueUrl, async (event) => {
      const e = event as { type?: string; memberId?: string };
      if (e.type === 'MemberCancelled' && e.memberId) {
        console.log(`Soft-deleting profiles for cancelled member ${e.memberId}`);
        // In prod: query profiles by memberId and mark deleted
      }
    }).catch((err) => console.error('SQS polling error', err));
  }
}

main().catch((err) => {
  console.error('Failed to start profile-service:', err);
  process.exit(1);
});
