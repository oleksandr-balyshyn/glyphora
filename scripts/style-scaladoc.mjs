#!/usr/bin/env node

import {readFile, readdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const apiDir = resolve(root, process.argv[2] ?? 'website/build/api');
const stylesheet = '/glyphora/api/glyphora-api.css';
const marker = `<!-- glyphora-api-theme -->`;

async function htmlFiles(directory) {
  const entries = await readdir(directory, {withFileTypes: true});
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) return htmlFiles(path);
    return entry.isFile() && entry.name.endsWith('.html') ? [path] : [];
  }));
  return nested.flat();
}

// Two halves of the site remember the theme under different keys: Docusaurus
// writes "theme" ('light' | 'dark'), Scaladoc writes "use-dark-theme"
// ('true' | 'false'), and neither knows about the other, so a choice made in
// the guide was lost on the way into the API reference. This bridges them.
// It is a blocking script in <head>, so it still lands before first paint even
// though it runs after Scaladoc's own theme.js.
const themeBridge = `<script>
    (function () {
      var apiKey = 'use-dark-theme';
      var siteKey = 'theme';
      function read(key) { try { return localStorage.getItem(key); } catch (error) { return null; } }
      function write(key, value) { try { localStorage.setItem(key, value); } catch (error) { /* storage unavailable */ } }

      // Scaladoc's theme.js persists its resolved value on every load, so
      // "use-dark-theme is unset" is never a usable signal. Track the last
      // guide value we adopted instead, and only follow the guide when that
      // value actually changed — which leaves Scaladoc's own toggle in charge
      // the rest of the time.
      var syncKey = 'glyphora-theme-synced-from-site';
      var site = read(siteKey);
      if (site !== null && site !== read(syncKey)) {
        var dark = site === 'dark';
        write(apiKey, String(dark));
        write(syncKey, site);
        document.documentElement.classList.toggle('theme-dark', dark);
      }

      document.addEventListener('DOMContentLoaded', function () {
        ['theme-toggle', 'mobile-theme-toggle'].forEach(function (id) {
          var button = document.getElementById(id);
          if (!button) return;
          button.addEventListener('click', function () {
            setTimeout(function () {
              var value = document.documentElement.classList.contains('theme-dark') ? 'dark' : 'light';
              write(siteKey, value);
              write(syncKey, value);
            }, 0);
          });
        });
      });
    })();
  </script>`;

const files = await htmlFiles(apiDir);
let styled = 0;

for (const file of files) {
  const html = await readFile(file, 'utf8');
  if (html.includes(marker) || !html.includes('</head>')) continue;
  const next = html.replace(
    '</head>',
    `  ${marker}\n  <link rel="stylesheet" href="${stylesheet}">\n  ${themeBridge}\n</head>`,
  );
  await writeFile(file, next);
  styled += 1;
}

console.log(`Applied glyphora API theme to ${styled} Scaladoc pages in ${apiDir}`);
