import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers } from './resolvers';
import { initSchema } from './datasources/mysql';

async function main() {
  await initSchema();

  const server = new ApolloServer({
    schema: buildSubgraphSchema({ typeDefs, resolvers }),
  });

  const port = parseInt(process.env.PORT ?? '8082', 10);
  const { url } = await startStandaloneServer(server, { listen: { port, host: '0.0.0.0' } });
  console.log(`billing-service ready at ${url}`);
}

main().catch((err) => { console.error('Failed to start billing-service:', err); process.exit(1); });
