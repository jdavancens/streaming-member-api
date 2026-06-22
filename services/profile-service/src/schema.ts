import gql from 'graphql-tag';

export const typeDefs = gql`
  extend schema
    @link(
      url: "https://specs.apollo.dev/federation/v2.6"
      import: ["@key", "@external"]
    )

  type Member @key(fields: "id") {
    id: ID! @external
    profiles: [Profile!]!
  }

  type Mutation {
    createProfile(memberId: ID!, input: CreateProfileInput!): Profile!
    updateProfile(profileId: ID!, input: UpdateProfileInput!): Profile!
    deleteProfile(profileId: ID!): Boolean!
    verifyProfilePin(profileId: ID!, pin: String!): Boolean!
  }

  type Profile @key(fields: "id") {
    id: ID!
    name: String!
    avatarUrl: String
    isKids: Boolean!
    language: String!
    hasPinLock: Boolean!
  }

  input CreateProfileInput {
    name: String!
    avatarUrl: String
    isKids: Boolean
    language: String
    pin: String
  }

  input UpdateProfileInput {
    name: String
    avatarUrl: String
    language: String
  }
`;
