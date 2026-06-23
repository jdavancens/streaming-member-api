import { TARGET_URL } from './client';

export default async function globalSetup() {
  console.log(`\nRunning tests against: ${TARGET_URL}`);
}
