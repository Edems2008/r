#!/usr/bin/env node
/*
 * Build előtt lefuttatva letölti a modpack.json-ban felsorolt Modrinth modokat a
 * build-resources/mods mappába, így azok már a telepítőben benne lesznek
 * ("letöltöm és a modok már benne vannak").
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { resolveLatest } = require('../src/modrinth');
const { download, sha1File } = require('../src/net');

(async () => {
  const root = path.join(__dirname, '..');
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'modpack.json'), 'utf8'));
  const outDir = path.join(root, 'build-resources', 'mods');
  fs.mkdirSync(outDir, { recursive: true });
  const lock = [];
  for (const entry of manifest.modrinth || []) {
    const r = await resolveLatest(entry.slug, { gameVersion: manifest.minecraft });
    const dest = path.join(outDir, r.filename);
    if (!fs.existsSync(dest) || sha1File(dest) !== r.sha1) {
      process.stdout.write(`⬇ ${entry.slug} ${r.version} -> ${r.filename}\n`);
      await download(r.url, dest, { sha1: r.sha1 });
    } else {
      process.stdout.write(`✓ ${r.filename}\n`);
    }
    lock.push(r);
  }
  fs.writeFileSync(path.join(root, 'build-resources', 'mods-lock.json'), JSON.stringify(lock, null, 2));
  console.log(`Kész: ${lock.length} mod a ${path.relative(root, outDir)} mappában.`);
})().catch((e) => { console.error(e); process.exit(1); });
