'use strict';
/* Modpack szinkron: a telepítőbe csomagolt modok bemásolása, hiány esetén letöltése a Modrinth-ról. */
const fs = require('fs');
const path = require('path');
const { download, sha1File } = require('./net');
const { resolveLatest } = require('./modrinth');

const MANAGED_FILE = '.nova-managed.json';

/**
 * @param {string} packDir    a launcherrel szállított pack mappa (resources/pack vagy build-resources)
 * @param {string} gameDir    .novalauncher
 * @param {object} manifest   modpack.json
 */
async function syncMods(packDir, gameDir, manifest, log, progress) {
  const modsDir = path.join(gameDir, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });
  const bundledDir = path.join(packDir, 'mods');
  const wanted = new Map(); // fájlnév -> forrás

  // 1) a telepítőbe csomagolt jar-ok (CI tölti le build közben + saját modok + Nova Companion)
  if (fs.existsSync(bundledDir)) {
    for (const f of fs.readdirSync(bundledDir)) {
      if (f.endsWith('.jar')) wanted.set(f, { type: 'file', src: path.join(bundledDir, f) });
    }
  }

  // 2) ha a build nem tartalmazta a Modrinth modokat (pl. fejlesztői futtatás), letöltjük őket
  const lockFile = path.join(packDir, 'mods-lock.json');
  let lock = null;
  try { lock = JSON.parse(fs.readFileSync(lockFile, 'utf8')); } catch { /* nincs lock */ }
  const lockedSlugs = new Set((lock || []).map((e) => e.slug));
  for (const entry of manifest.modrinth || []) {
    if (lockedSlugs.has(entry.slug)) continue;
    try {
      log(`Mod keresése a Modrinth-on: ${entry.slug}`);
      const r = await resolveLatest(entry.slug, { gameVersion: manifest.minecraft });
      wanted.set(r.filename, { type: 'url', url: r.url, sha1: r.sha1 });
    } catch (e) {
      log(`⚠ ${entry.slug}: ${e.message}`);
    }
  }

  // 3) régi, általunk telepített modok törlése (a felhasználó saját modjaihoz nem nyúlunk)
  const managedPath = path.join(modsDir, MANAGED_FILE);
  let managed = [];
  try { managed = JSON.parse(fs.readFileSync(managedPath, 'utf8')); } catch { /* első futás */ }
  for (const f of managed) {
    if (!wanted.has(f)) {
      fs.rmSync(path.join(modsDir, f), { force: true });
      log(`Régi mod eltávolítva: ${f}`);
    }
  }

  // 4) másolás / letöltés
  let i = 0;
  for (const [name, src] of wanted) {
    i++;
    progress('Modok', i, wanted.size);
    const dest = path.join(modsDir, name);
    if (src.type === 'file') {
      if (!fs.existsSync(dest) || fs.statSync(dest).size !== fs.statSync(src.src).size || sha1File(dest) !== sha1File(src.src)) {
        fs.copyFileSync(src.src, dest);
        log(`Mod telepítve: ${name}`);
      }
    } else if (!fs.existsSync(dest) || sha1File(dest) !== src.sha1) {
      log(`Mod letöltése: ${name}`);
      await download(src.url, dest, { sha1: src.sha1 });
    }
  }
  fs.writeFileSync(managedPath, JSON.stringify([...wanted.keys()], null, 2));

  // 5) alapértelmezett configok (csak ha még nincsenek)
  const cfgSrc = path.join(packDir, 'config');
  if (fs.existsSync(cfgSrc)) copyMissing(cfgSrc, path.join(gameDir, 'config'));
  return [...wanted.keys()];
}

function copyMissing(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const e of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, e.name);
    const d = path.join(dest, e.name);
    if (e.isDirectory()) copyMissing(s, d);
    else if (!fs.existsSync(d) && e.name !== '.gitkeep') fs.copyFileSync(s, d);
  }
}

/** Shaderpack letöltése Modrinth-ról a shaderpacks mappába. */
async function downloadShaderpack(slug, gameDir, gameVersion, log, progress) {
  const r = await resolveLatest(slug, { gameVersion, type: 'shader' });
  const dest = path.join(gameDir, 'shaderpacks', r.filename);
  if (fs.existsSync(dest)) return r.filename;
  log(`Shader letöltése: ${r.filename}`);
  await download(r.url, dest, { sha1: r.sha1, onProgress: (d, t) => progress('Shader', d, t) });
  return r.filename;
}

function listShaderpacks(gameDir) {
  const dir = path.join(gameDir, 'shaderpacks');
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((f) => f.endsWith('.zip') || fs.statSync(path.join(dir, f)).isDirectory());
}

/** Oculus (Iris) beállítás: config/oculus.properties */
function writeShaderConfig(gameDir, enabled, pack) {
  const file = path.join(gameDir, 'config', 'oculus.properties');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const props = new Map();
  try {
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^([^#=]+)=(.*)$/);
      if (m) props.set(m[1].trim(), m[2]);
    }
  } catch { /* új fájl */ }
  props.set('enableShaders', String(!!(enabled && pack)));
  if (pack) props.set('shaderPack', pack);
  const out = ['# Nova Launcher által kezelt Oculus beállítások'];
  for (const [k, v] of props) out.push(`${k}=${v}`);
  fs.writeFileSync(file, out.join('\n') + '\n');
}

module.exports = { syncMods, downloadShaderpack, listShaderpacks, writeShaderConfig };
