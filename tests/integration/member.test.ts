import { gql } from 'graphql-request';
import { memberClient } from '../helpers/client';

const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) {
      member { id email fullName country status createdAt }
    }
  }
`;

const GET_MEMBER = gql`
  query GetMember($id: ID!) {
    member(id: $id) { id email fullName country status }
  }
`;

describe('member-service', () => {
  let memberId: string;
  const email = `test-${Date.now()}@example.com`;

  test('register creates a member and returns it', async () => {
    const res = await memberClient.request<{ register: { member: { id: string; email: string; status: string } } }>(
      REGISTER,
      { input: { email, password: 'secret123', fullName: 'Integration Test', country: 'US' } }
    );
    expect(res.register.member.email).toBe(email);
    expect(res.register.member.status).toBe('ACTIVE');
    expect(res.register.member.id).toBeTruthy();
    memberId = res.register.member.id;
  });

  test('member query returns the registered member', async () => {
    const res = await memberClient.request<{ member: { id: string; email: string } }>(
      GET_MEMBER, { id: memberId }
    );
    expect(res.member.id).toBe(memberId);
    expect(res.member.email).toBe(email);
  });

  test('member query returns null for unknown id', async () => {
    const res = await memberClient.request<{ member: null }>(
      GET_MEMBER, { id: 'does-not-exist' }
    );
    expect(res.member).toBeNull();
  });
});
