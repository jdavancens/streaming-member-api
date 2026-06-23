import type { Config } from 'jest';

const config: Config = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testTimeout: 30000,
  globalSetup: './helpers/setup.ts',
};

export default config;
