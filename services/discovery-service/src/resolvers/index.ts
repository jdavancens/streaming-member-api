const HOME_COMPONENTS = [
  {
    __typename: 'HeroComponent',
    id: 'hero-1',
    title: 'Stranger Things Season 5',
    backgroundImageUrl: 'https://example.com/images/st5-bg.jpg',
    logoImageUrl: 'https://example.com/images/st5-logo.png',
    ctaLabel: 'Play Now',
    ctaContentId: 'content-st5',
    description: 'The final season of the hit series.',
  },
  {
    __typename: 'TabComponent',
    id: 'tabs-home',
    tabs: [
      { id: 'tab-home', label: 'Home', selected: true },
      { id: 'tab-movies', label: 'Movies', selected: false },
      { id: 'tab-series', label: 'TV Shows', selected: false },
      { id: 'tab-games', label: 'Games', selected: false },
    ],
  },
  {
    __typename: 'RowComponent',
    id: 'row-trending',
    label: 'Trending Now',
    items: [
      { id: 'c1', title: 'The Crown', thumbnailUrl: 'https://example.com/thumb/crown.jpg', type: 'SERIES', durationSeconds: null, matchScore: 97 },
      { id: 'c2', title: 'Oppenheimer', thumbnailUrl: 'https://example.com/thumb/opp.jpg', type: 'MOVIE', durationSeconds: 10800, matchScore: 94 },
      { id: 'c3', title: 'Wednesday', thumbnailUrl: 'https://example.com/thumb/wed.jpg', type: 'SERIES', durationSeconds: null, matchScore: 91 },
    ],
  },
  {
    __typename: 'BillboardComponent',
    id: 'billboard-plan',
    headline: 'Watch on any device',
    subtext: 'Stream on your phone, tablet, laptop, and TV without paying more.',
    actions: [
      { label: 'Get Started', contentId: 'plan-premium', style: 'PRIMARY' },
      { label: 'Learn More', contentId: 'info-plans', style: 'SECONDARY' },
    ],
  },
  {
    __typename: 'RowComponent',
    id: 'row-continue',
    label: 'Continue Watching',
    items: [
      { id: 'c4', title: 'Dark', thumbnailUrl: 'https://example.com/thumb/dark.jpg', type: 'SERIES', durationSeconds: null, matchScore: 88 },
      { id: 'c5', title: 'Squid Game', thumbnailUrl: 'https://example.com/thumb/sg.jpg', type: 'SERIES', durationSeconds: null, matchScore: 95 },
    ],
  },
];

const BROWSE_COMPONENTS = [
  {
    __typename: 'RowComponent',
    id: 'row-action',
    label: 'Action & Adventure',
    items: [
      { id: 'c6', title: 'Extraction 2', thumbnailUrl: 'https://example.com/thumb/ext2.jpg', type: 'MOVIE', durationSeconds: 7500, matchScore: 82 },
      { id: 'c7', title: 'Army of the Dead', thumbnailUrl: 'https://example.com/thumb/aotd.jpg', type: 'MOVIE', durationSeconds: 8880, matchScore: 79 },
    ],
  },
  {
    __typename: 'BillboardComponent',
    id: 'billboard-browse',
    headline: 'Explore thousands of titles',
    subtext: null,
    actions: [
      { label: 'Browse All', contentId: 'browse-all', style: 'PRIMARY' },
    ],
  },
];

export const resolvers = {
  Query: {
    homeScreen(_: unknown, { memberId, context }: { memberId: string; context: object }) {
      return { components: HOME_COMPONENTS, version: '2024.1' };
    },
    browseScreen(_: unknown, { memberId, genre, context }: { memberId: string; genre?: string; context: object }) {
      return { components: BROWSE_COMPONENTS, genre: genre ?? null };
    },
  },

  DiscoveryComponent: {
    __resolveType(obj: { __typename: string }) {
      return obj.__typename;
    },
  },
};
