/**
 * Canonical documentation navigation.
 *
 * Docusaurus consumes this directly and scripts/export-wiki.mjs uses the same
 * structure to build the GitHub Wiki sidebar. Keep page IDs aligned with files
 * in website/docs.
 */
export const docsNavigation = [
  {
    type: 'category',
    label: 'Start here',
    collapsed: false,
    items: ['intro', 'getting-started', 'examples'],
  },
  {
    type: 'category',
    label: 'Fundamentals',
    collapsed: false,
    items: [
      'state-and-signals',
      'layout-and-style',
      'widgets',
      'mouse',
      'unicode-and-accessibility',
      'architecture',
    ],
  },
  {
    type: 'category',
    label: 'Build applications',
    collapsed: false,
    items: [
      'app-shell',
      'forms-and-validation',
      'async-and-timers',
      'motion',
      'cookbook',
    ],
  },
  {
    type: 'category',
    label: 'Ship with confidence',
    collapsed: false,
    items: ['testing', 'native-image', 'troubleshooting', 'faq'],
  },
  {
    type: 'category',
    label: 'Project',
    collapsed: false,
    items: ['contributing', 'versioning'],
  },
];

export default docsNavigation;
