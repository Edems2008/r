'use strict';
/*
 * Telepítés a hivatalos Minecraft Launcherbe.
 * A Nova Launcher előkészíti a modpackot (Forge, modok, shaderek, HUD, telefonos kapcsolat), majd a hivatalos
 * launcherben létrehoz egy „Nova Pack” telepítést. Azt kiválasztva a hivatalos launcher indítja a játékot
 * a benne MÁR BEJELENTKEZETT, saját (eredeti) fiókkal – a Nova Launcherben így nem kell bejelentkezni.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const { ensureJava } = require('./java');
const { syncMods, writeShaderConfig } = require('./pack');
const companion = require('./companion');
const { download } = require('./net');
const { parseServer } = require('./game');

const PROFILE_ID = 'nova-pack';

/** A hivatalos launcher adatmappája (.minecraft) */
function officialDir() {
  if (process.platform === 'win32') return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), '.minecraft');
  if (process.platform === 'darwin') return path.join(os.homedir(), 'Library', 'Application Support', 'minecraft');
  return path.join(os.homedir(), '.minecraft');
}

function forgeVersionId(manifest) {
  return `${manifest.minecraft}-forge-${manifest.forge}`;
}

function run(cmd, args, log) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { windowsHide: true });
    p.stdout.on('data', (d) => String(d).split(/\r?\n/).filter(Boolean).forEach((l) => log(l)));
    p.stderr.on('data', (d) => String(d).split(/\r?\n/).filter(Boolean).forEach((l) => log(l)));
    p.on('error', reject);
    p.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`A Forge telepítő hibával lépett ki (${code}).`))));
  });
}

/**
 * @returns {Promise<{mcDir: string, versionId: string}>}
 */
async function installIntoOfficial({ gameDir, dataDir, packDir, manifest, settings, log, progress }) {
  const mcDir = officialDir();
  fs.mkdirSync(mcDir, { recursive: true });
  const profilesFile = path.join(mcDir, 'launcher_profiles.json');
  // a Forge telepítő megköveteli, hogy létezzen a profilfájl
  if (!fs.existsSync(profilesFile)) fs.writeFileSync(profilesFile, JSON.stringify({ profiles: {}, version: 3 }, null, 2));

  // 1) Forge telepítése a hivatalos launcher mappájába (ha még nincs)
  const versionId = forgeVersionId(manifest);
  const versionJson = path.join(mcDir, 'versions', versionId, `${versionId}.json`);
  if (!fs.existsSync(versionJson)) {
    const javaPath = settings.javaPath && fs.existsSync(settings.javaPath) ? settings.javaPath : await ensureJava(dataDir, log, progress);
    const javaCli = javaPath.replace(/javaw\.exe$/i, 'java.exe');
    const name = `forge-${manifest.minecraft}-${manifest.forge}-installer.jar`;
    const installer = path.join(gameDir, 'installers', name);
    if (!fs.existsSync(installer)) {
      log(`Forge ${manifest.minecraft}-${manifest.forge} letöltése…`);
      await download(`https://maven.minecraftforge.net/net/minecraftforge/forge/${manifest.minecraft}-${manifest.forge}/${name}`,
        installer, { onProgress: (d, t) => progress('Forge', d, t) });
    }
    log('Forge telepítése a hivatalos Minecraft Launcherbe (ez 1-2 perc lehet)…');
    progress('Forge telepítése', 0, 0);
    await run(javaCli, ['-jar', installer, '--installClient', mcDir], log);
  } else {
    log(`Forge már telepítve: ${versionId}`);
  }

  // 2) modok, shader és companion beállítások a Nova játékmappába
  await syncMods(packDir, gameDir, manifest, log, progress);
  writeShaderConfig(gameDir, settings.shadersEnabled, settings.shaderPack);
  const server = parseServer(settings.serverAddress);
  companion.writeConfig(gameDir, {
    companionEnabled: settings.companionEnabled !== false,
    relayUrl: settings.relayUrl || '',
    // a hivatalos launcher nem tud Quick Play-t, ezért a mod lép be a főmenüből
    autoJoinServer: server && settings.autoJoin !== false ? `${server.host}:${server.port}` : '',
  });

  // 3) „Nova Pack” telepítés a hivatalos launcher profiljai közé (a többi profilhoz nem nyúlunk)
  let data;
  try { data = JSON.parse(fs.readFileSync(profilesFile, 'utf8')); } catch { data = {}; }
  data.profiles = data.profiles || {};
  const ram = Math.max(2, Number(settings.ramGb) || 4);
  const now = new Date().toISOString();
  data.profiles[PROFILE_ID] = {
    ...(data.profiles[PROFILE_ID] || {}),
    name: 'Nova Pack',
    type: 'custom',
    icon: 'Grass',
    lastVersionId: versionId,
    gameDir,
    javaArgs: `-Xmx${ram}G -Xms${Math.min(2, ram)}G -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M`,
    created: (data.profiles[PROFILE_ID] && data.profiles[PROFILE_ID].created) || now,
    lastUsed: now,
  };
  fs.writeFileSync(profilesFile, JSON.stringify(data, null, 2));
  log('Kész! A hivatalos Minecraft Launcherben válaszd a „Nova Pack” telepítést, és nyomd meg a Játék gombot.');
  return { mcDir, versionId };
}

module.exports = { installIntoOfficial, officialDir };
