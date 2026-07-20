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

const files = await htmlFiles(apiDir);
let styled = 0;

for (const file of files) {
  const html = await readFile(file, 'utf8');
  if (html.includes(marker) || !html.includes('</head>')) continue;
  const next = html.replace(
    '</head>',
    `  ${marker}\n  <link rel="stylesheet" href="${stylesheet}">\n</head>`,
  );
  await writeFile(file, next);
  styled += 1;
}

console.log(`Applied glyphora API theme to ${styled} Scaladoc pages in ${apiDir}`);
