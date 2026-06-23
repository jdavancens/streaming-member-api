import { gql } from 'graphql-request';
import { memberClient, profileClient } from '../helpers/client';

const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) { member { id } }
  }
`;

const CREATE_PROFILE = gql`
  mutation CreateProfile($memberId: ID!, $input: CreateProfileInput!) {
    createProfile(memberId: $memberId, input: $input) {
      id name isKids language hasPinLock
    }
  }
`;

const DELETE_PROFILE = gql`
  mutation DeleteProfile($profileId: ID!) {
    deleteProfile(profileId: $profileId)
  }
`;

describe('profile-service', () => {
  let memberId: string;
  let profileId: string;

  beforeAll(async () => {
    const res = await memberClient.request<{ register: { member: { id: string } } }>(REGISTER, {
      input: { email: `profile-test-${Date.now()}@example.com`, password: 'secret', fullName: 'Profile Test', country: 'US' },
    });
    memberId = res.register.member.id;
  });

  test('createProfile creates a profile for the member', async () => {
    const res = await profileClient.request<{ createProfile: { id: string; name: string; isKids: boolean } }>(
      CREATE_PROFILE,
      { memberId, input: { name: 'Main', isKids: false, language: 'en' } }
    );
    expect(res.createProfile.name).toBe('Main');
    expect(res.createProfile.isKids).toBe(false);
    expect(res.createProfile.id).toBeTruthy();
    profileId = res.createProfile.id;
  });

  test('createProfile with pin sets hasPinLock true', async () => {
    const res = await profileClient.request<{ createProfile: { hasPinLock: boolean } }>(
      CREATE_PROFILE,
      { memberId, input: { name: 'Kids', isKids: true, pin: '1234' } }
    );
    expect(res.createProfile.hasPinLock).toBe(true);
  });

  test('deleteProfile soft-deletes it', async () => {
    const res = await profileClient.request<{ deleteProfile: boolean }>(
      DELETE_PROFILE, { profileId }
    );
    expect(res.deleteProfile).toBe(true);
  });
});
