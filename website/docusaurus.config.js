// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'glyphora',
  tagline: 'Build swaggy terminal UIs in Scala 3',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  url: 'https://oleksandr-balyshyn.github.io',
  baseUrl: '/glyphora/',

  organizationName: 'oleksandr-balyshyn',
  projectName: 'glyphora',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/oleksandr-balyshyn/glyphora/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      colorMode: {
        defaultMode: 'dark',
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'glyphora',
        logo: {
          alt: 'glyphora logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Docs',
          },
          {
            href: 'pathname:///api/',
            label: 'API',
            position: 'left',
          },
          {
            href: 'https://github.com/oleksandr-balyshyn/glyphora',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {label: 'Getting started', to: '/getting-started'},
              {label: 'Cookbook', to: '/cookbook'},
              {label: 'Widget catalog', to: '/widgets'},
              {label: 'API reference', href: 'pathname:///api/'},
            ],
          },
          {
            title: 'Project',
            items: [
              {label: 'Examples', to: '/examples'},
              {label: 'Architecture', to: '/architecture'},
              {label: 'Contributing', to: '/contributing'},
              {label: 'Versioning', to: '/versioning'},
            ],
          },
          {
            title: 'More',
            items: [
              {label: 'GitHub', href: 'https://github.com/oleksandr-balyshyn/glyphora'},
              {label: 'Releases', href: 'https://github.com/oleksandr-balyshyn/glyphora/tags'},
              {label: 'Maven Central', href: 'https://search.maven.org/search?q=g:io.worxbend'},
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} glyphora contributors. MIT licensed.`,
      },
      prism: {
        theme: prismThemes.oneLight,
        darkTheme: prismThemes.oneDark,
        additionalLanguages: ['java', 'scala', 'bash', 'json'],
      },
    }),
};

export default config;
