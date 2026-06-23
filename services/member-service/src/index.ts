import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema';
import { resolvers } from './resolvers';

const server = new ApolloServer({
  schema: buildSubgraphSchema({ typeDefs, resolvers }),
});

const port = parseInt(process.env.PORT ?? '8081', 10);

startStandaloneServer(server, { listen: { port, host: '0.0.0.0' } })
  .then(({ url }) => console.log(`member-service ready at ${url}`))
  .catch((err) => { console.error('Failed to start member-service:', err); process.exit(1); });
