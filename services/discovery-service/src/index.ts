import express from 'express';
import cors from 'cors';
import bodyParser from 'body-parser';
import { ApolloServer } from '@apollo/server';
import { expressMiddleware } from '@apollo/server/express4';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers } from './resolvers';

async function main() {
  const app = express();
  const port = parseInt(process.env.PORT ?? '8085', 10);

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
    console.log(`discovery-service ready at http://0.0.0.0:${port}`);
  });
}

main().catch((err) => {
  console.error('Failed to start discovery-service:', err);
  process.exit(1);
});
