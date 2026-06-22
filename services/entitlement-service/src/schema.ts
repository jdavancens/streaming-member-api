import gql from 'graphql-tag';

export const typeDefs = gql`
  extend schema
    @link(
      url: "https://specs.apollo.dev/federation/v2.6"
      import: ["@key", "@external"]
    )

  type Member @key(fields: "id") {
    id: ID! @external
    canStream: StreamEntitlement!
  }

  type Mutation {
    acquireStream(memberId: ID!, deviceId: String!): StreamSession!
    releaseStream(memberId: ID!, streamId: ID!): Boolean!
    heartbeatStream(memberId: ID!, streamId: ID!): Boolean!
  }

  type StreamEntitlement {
    allowed: Boolean!
    reason: String
    concurrentStreams: Int!
    maxStreams: Int!
  }

  type StreamSession {
    streamId: ID!
    expiresAt: String!
  }
`;
