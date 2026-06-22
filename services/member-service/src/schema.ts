import gql from 'graphql-tag';

export const typeDefs = gql`
  extend schema
    @link(
      url: "https://specs.apollo.dev/federation/v2.6"
      import: ["@key", "@shareable"]
    )

  scalar DateTime

  type Query {
    member(id: ID!): Member
  }

  type Mutation {
    register(input: RegisterInput!): RegisterPayload!
  }

  type Member @key(fields: "id") {
    id: ID!
    email: String!
    fullName: String!
    country: String!
    status: MemberStatus!
    createdAt: DateTime!
  }

  enum MemberStatus {
    ACTIVE
    SUSPENDED
    CANCELLED
  }

  input RegisterInput {
    email: String!
    password: String!
    fullName: String!
    country: String!
  }

  type RegisterPayload {
    member: Member!
  }
`;
