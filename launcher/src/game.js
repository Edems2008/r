'use strict';
/* A játék előkészítése és indítása: Java -> Forge -> modok -> shader/companion config -> MCLC. */
const fs = require('fs');
const path = require('path');
const { Client } = require('minecraft-launcher-core');
const { ensureJava } = require('./java');
const { ensureMavenFile } = require('./net');
const { syncMods, writeShaderConfig } = require('./pack');
const companion = require('./companion');
const { resolveForge } = require('./forge');

function parseServer(input) {
  const s = (input || '').trim();
  if (!s) return null;
  // IPv6 [::1]:25565, host:port, vagy csak host
  const m = s.match(/^\[(.+)\](?::(\d+))?$/) || s.match(/^([^:]+)(?::(\d+))?$/);
  if (!m) return { host: s, port: 25565, id: s };
  const port = m[2] ? Number(m[2]) : 25565;
  return { host: m[1], port, id: m[2] ? s : m[1] };
}

async function ensureForge(gameDir, mc, forge, log, progress) {
  const name = `forge-${mc}-${forge}-installer.jar`;
  const file = path.join(gameDir, 'installers', name);
  log(`Forge ${mc}-${forge} ellenőrzése…`);
  const url = `https://maven.minecraftforge.net/net/minecraftforge/forge/${mc}-${forge}/${name}`;
  await ensureMavenFile(url, file, { log, onProgress: (d, t) => progress('Forge', d, t) });
  return file;
}

/**
 * @param {object} o
 * @param {string} o.gameDir
 * @param {string} o.dataDir
 * @param {string} o.packDir
 * @param {object} o.manifest
 * @param {object} o.settings    felhasználói beállítások
 * @param {Function} o.getAuth   async () => MCLC authorization
 * @param {Function} o.log
 * @param {Function} o.progress  (label, done, total)
 * @param {Function} o.onClose   (exitCode)
 */
async function launch(o) {
  const { gameDir, dataDir, packDir, settings, log, progress } = o;
  const manifest = { ...o.manifest, forge: await resolveForge(o.manifest, dataDir, log) };
  fs.mkdirSync(gameDir, { recursive: true });

  const javaPath = settings.javaPath && fs.existsSync(settings.javaPath)
    ? settings.javaPath
    : await ensureJava(dataDir, log, progress);
  log(`Java: ${javaPath}`);

  const forgeInstaller = await ensureForge(gameDir, manifest.minecraft, manifest.forge, log, progress);
  await syncMods(packDir, gameDir, manifest, log, progress);

  writeShaderConfig(gameDir, settings.shadersEnabled, settings.shaderPack);
  companion.writeConfig(gameDir, {
    companionEnabled: settings.companionEnabled !== false,
    relayUrl: settings.relayUrl || '',
    autoJoinServer: '', // a Nova Launcher Quick Play-jel lép be, a mod ne lépjen be még egyszer
  });

  const authorization = await o.getAuth();
  const server = parseServer(settings.serverAddress);
  const ramMax = Math.max(2, Number(settings.ramGb) || 4);

  const opts = {
    authorization,
    root: gameDir,
    version: { number: manifest.minecraft, type: 'release' },
    forge: forgeInstaller,
    javaPath,
    memory: { max: `${ramMax}G`, min: `${Math.min(2, ramMax)}G` },
    window: settings.fullscreen ? { fullscreen: true } : { width: 1280, height: 720 },
    overrides: { detached: false, maxSockets: 8 },
    customArgs: [
      '-XX:+UseG1GC',
      '-XX:+UnlockExperimentalVMOptions',
      '-XX:G1NewSizePercent=20',
      '-XX:MaxGCPauseMillis=50',
      '-XX:G1HeapRegionSize=32M',
    ],
  };
  // Automatikus belépés a szerverre (Quick Play, 1.20+)
  if (server && settings.autoJoin !== false) {
    opts.quickPlay = { type: 'multiplayer', identifier: `${server.host}:${server.port}` };
    log(`Automatikus csatlakozás: ${server.host}:${server.port}`);
  }

  const client = new Client();
  client.on('debug', (e) => log(String(e)));
  client.on('data', (e) => log(String(e).trimEnd()));
  client.on('progress', (e) => progress(`Letöltés: ${e.type}`, e.task, e.total));
  client.on('close', (code) => o.onClose && o.onClose(code));

  log('Minecraft indítása…');
  const proc = await client.launch(opts);
  if (!proc) throw new Error('A játék nem indult el (részletek a naplóban).');
  return proc;
}

module.exports = { launch, parseServer };
