// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'intro',
    'getting-started',
    'architecture',
    {
      type: 'category',
      label: 'Guide',
      collapsed: false,
      items: ['state-and-signals', 'app-shell', 'widgets', 'motion', 'mouse'],
    },
    'cookbook',
    'examples',
    'testing',
    'native-image',
    {
      type: 'category',
      label: 'Project',
      collapsed: false,
      items: ['contributing', 'versioning'],
    },
  ],
};

export default sidebars;
