#!/usr/bin/env node
// Gyors szintaxis-ellenőrzés (CI és fejlesztés): minden launcher JS fájl betölthető-e.
'use strict';
const { execFileSync } = require('child_process');
const path = require('path');
const files = ['main.js', 'preload.js', 'renderer/renderer.js', 'scripts/fetch-mods.js',
  'src/auth.js', 'src/companion.js', 'src/game.js', 'src/java.js', 'src/modrinth.js', 'src/net.js', 'src/pack.js', 'src/official.js', 'src/forge.js'];
for (const f of files) execFileSync(process.execPath, ['--check', path.join(__dirname, '..', f)], { stdio: 'inherit' });
console.log('OK:', files.length, 'fájl');
