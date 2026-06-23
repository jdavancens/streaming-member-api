import { GraphQLClient } from 'graphql-request';

export const TARGET_URL = process.env.TARGET_URL ?? 'http://localhost:4000';

export const client = new GraphQLClient(TARGET_URL);

export const serviceUrl = (port: number) =>
  process.env[`SERVICE_URL_${port}`] ?? `http://localhost:${port}`;

export const memberClient      = new GraphQLClient(serviceUrl(8081));
export const billingClient     = new GraphQLClient(serviceUrl(8082));
export const profileClient     = new GraphQLClient(serviceUrl(8083));
export const entitlementClient = new GraphQLClient(serviceUrl(8084));
export const discoveryClient   = new GraphQLClient(serviceUrl(8085));
