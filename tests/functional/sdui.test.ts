import { gql } from 'graphql-request';
import { client } from '../helpers/client';

const HOME_SCREEN = gql`
  query HomeScreen($memberId: ID!, $context: ScreenContext!) {
    homeScreen(memberId: $memberId, context: $context) {
      version
      components {
        ... on HeroComponent    { id title backgroundImageUrl ctaLabel }
        ... on RowComponent     { id label items { title type } }
        ... on BillboardComponent { id headline }
        ... on TabComponent     { id tabs { label selected } }
      }
    }
  }
`;

const BROWSE_SCREEN = gql`
  query BrowseScreen($memberId: ID!, $context: ScreenContext!) {
    browseScreen(memberId: $memberId, genre: "Action", context: $context) {
      genre
      components {
        ... on RowComponent     { label items { title } }
        ... on BillboardComponent { headline }
      }
    }
  }
`;

const CONTEXT = { platform: 'WEB', locale: 'en-US' };
const MEMBER_ID = 'test-member';

describe('discovery-service: SDUI', () => {
  test('homeScreen returns a version and all 4 component types', async () => {
    const res = await client.request<{
      homeScreen: { version: string; components: Array<Record<string, unknown>> }
    }>(HOME_SCREEN, { memberId: MEMBER_ID, context: CONTEXT });

    expect(res.homeScreen.version).toBeTruthy();
    expect(res.homeScreen.components.length).toBeGreaterThan(0);
    expect(res.homeScreen.components.some(c => 'backgroundImageUrl' in c)).toBe(true);
    expect(res.homeScreen.components.some(c => 'tabs' in c)).toBe(true);
    expect(res.homeScreen.components.some(c => 'items' in c)).toBe(true);
    expect(res.homeScreen.components.some(c => 'headline' in c)).toBe(true);
  });

  test('homeScreen HeroComponent has required fields', async () => {
    const res = await client.request<{
      homeScreen: { components: Array<Record<string, unknown>> }
    }>(HOME_SCREEN, { memberId: MEMBER_ID, context: CONTEXT });

    const hero = res.homeScreen.components.find(c => 'backgroundImageUrl' in c) as {
      title: string; backgroundImageUrl: string; ctaLabel: string;
    };
    expect(hero.title).toBeTruthy();
    expect(hero.backgroundImageUrl).toMatch(/^https?:\/\//);
    expect(hero.ctaLabel).toBeTruthy();
  });

  test('browseScreen returns genre and components', async () => {
    const res = await client.request<{
      browseScreen: { genre: string; components: unknown[] }
    }>(BROWSE_SCREEN, { memberId: MEMBER_ID, context: CONTEXT });

    expect(res.browseScreen.genre).toBe('Action');
    expect(res.browseScreen.components.length).toBeGreaterThan(0);
  });
});
