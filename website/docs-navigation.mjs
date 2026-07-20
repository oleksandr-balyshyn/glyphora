/**
 * Canonical documentation navigation.
 *
 * Docusaurus consumes this directly and scripts/export-wiki.mjs uses the same
 * structure to build the GitHub Wiki sidebar. Keep page IDs aligned with files
 * in website/docs.
 */
export const docsNavigation = [
  'intro',
  'getting-started',
  'architecture',
  {
    type: 'category',
    label: 'Core guide',
    collapsed: false,
    items: ['state-and-signals', 'app-shell', 'widgets', 'motion', 'mouse'],
  },
  {
    type: 'category',
    label: 'Build and ship',
    collapsed: false,
    items: ['cookbook', 'examples', 'testing', 'native-image'],
  },
  {
    type: 'category',
    label: 'Help',
    collapsed: false,
    items: ['troubleshooting', 'faq'],
  },
  {
    type: 'category',
    label: 'Project',
    collapsed: false,
    items: ['contributing', 'versioning'],
  },
];

export default docsNavigation;
