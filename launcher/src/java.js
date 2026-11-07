'use strict';
/* Java 17 automatikus letöltése (Eclipse Temurin JRE) – a felhasználónak nem kell külön telepítenie. */
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const { promisify } = require('util');
const { download, getJson } = require('./net');

const run = promisify(execFile);

function platformInfo() {
  const os = { win32: 'windows', darwin: 'mac', linux: 'linux' }[process.platform];
  const arch = { x64: 'x64', arm64: 'aarch64' }[process.arch];
  if (!os || !arch) throw new Error(`Nem támogatott rendszer: ${process.platform}/${process.arch}`);
  return { os, arch };
}

function findJavaBinary(dir) {
  const exe = process.platform === 'win32' ? 'javaw.exe' : 'java';
  const stack = [dir];
  while (stack.length) {
    const d = stack.pop();
    let entries;
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { continue; }
    for (const e of entries) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) stack.push(p);
      else if (e.name === exe && path.basename(path.dirname(p)) === 'bin') return p;
    }
  }
  return null;
}

function javaCli(bin) {
  return bin.replace(/javaw\.exe$/i, 'java.exe');
}

/** A Java fő verziószáma (pl. 17), vagy 0 ha nem futtatható. */
async function javaMajor(bin) {
  try {
    const r = await run(javaCli(bin), ['-version'], { windowsHide: true, timeout: 15000 });
    const out = `${r.stderr || ''}${r.stdout || ''}`;
    const m = out.match(/version "(\d+)(?:\.(\d+))?/);
    if (!m) return 0;
    return m[1] === '1' ? Number(m[2]) : Number(m[1]);
  } catch {
    return 0;
  }
}

function listDirs(dir) {
  try { return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => path.join(dir, e.name)); } catch { return []; }
}

/**
 * Már telepített Java keresése, hogy ne kelljen letölteni:
 * a hivatalos Minecraft Launcher saját Java-ja (java-runtime-gamma = Java 17), JAVA_HOME, és a szokásos telepítési helyek.
 */
function candidateJavas() {
  const exe = process.platform === 'win32' ? 'javaw.exe' : 'java';
  const out = [];
  const add = (p) => { if (p && fs.existsSync(p) && !out.includes(p)) out.push(p); };
  const runtimeRoots = [];
  if (process.platform === 'win32') {
    const appdata = process.env.APPDATA || '';
    const local = process.env.LOCALAPPDATA || '';
    runtimeRoots.push(
      path.join(appdata, '.minecraft', 'runtime'),
      path.join(local, 'Packages', 'Microsoft.4297127D64EC6_8wekyb3d8bbwe', 'LocalCache', 'Local', 'runtime'),
      'C:\\Program Files (x86)\\Minecraft Launcher\\runtime',
      'C:\\Program Files\\Minecraft Launcher\\runtime');
  } else if (process.platform === 'darwin') {
    runtimeRoots.push(path.join(process.env.HOME || '', 'Library', 'Application Support', 'minecraft', 'runtime'));
  } else {
    runtimeRoots.push(path.join(process.env.HOME || '', '.minecraft', 'runtime'));
  }
  for (const root of runtimeRoots) {
    for (const name of ['java-runtime-gamma', 'java-runtime-gamma-snapshot', 'java-runtime-beta', 'java-runtime-delta']) {
      for (const plat of listDirs(path.join(root, name))) {
        add(findJavaBinary(plat));
      }
    }
  }
  if (process.env.JAVA_HOME) add(path.join(process.env.JAVA_HOME, 'bin', exe));
  if (process.platform === 'win32') {
    for (const base of ['C:\\Program Files\\Eclipse Adoptium', 'C:\\Program Files\\Java', 'C:\\Program Files\\Microsoft',
      'C:\\Program Files\\Zulu', 'C:\\Program Files\\BellSoft', 'C:\\Program Files\\Amazon Corretto']) {
      for (const d of listDirs(base)) add(path.join(d, 'bin', exe));
    }
  }
  return out;
}

/** Telepített Java a megadott verziótartományban (alapból pontosan 17). */
async function findInstalledJava(minMajor = 17, maxMajor = 17, log = () => {}) {
  for (const bin of candidateJavas()) {
    const major = await javaMajor(bin);
    if (major >= minMajor && major <= maxMajor) {
      log(`Telepített Java ${major} használata: ${bin}`);
      return bin;
    }
  }
  return null;
}

/** Az Adoptium API megadja a pontos letöltési linket, méretet és SHA-256 ellenőrzőösszeget. */
async function resolvePackage(os, arch) {
  const q = new URLSearchParams({ architecture: arch, image_type: 'jre', os, vendor: 'eclipse' });
  const list = await getJson(`https://api.adoptium.net/v3/assets/latest/17/hotspot?${q}`);
  const pkg = list && list[0] && list[0].binary && list[0].binary.package;
  if (!pkg || !pkg.link) throw new Error('Nem található Java 17 letöltés ehhez a rendszerhez.');
  return { url: pkg.link, sha256: pkg.checksum, size: pkg.size, name: pkg.name };
}

/** Kicsomagolás: Windowson a beépített tar.exe, utána PowerShell, végső esetben adm-zip. */
async function extract(archive, dir, log) {
  if (process.platform !== 'win32') {
    await run('tar', ['-xzf', archive, '-C', dir]);
    return;
  }
  const sysTar = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'tar.exe');
  const attempts = [
    ['tar.exe', () => run(sysTar, ['-xf', archive, '-C', dir], { windowsHide: true })],
    ['PowerShell', () => run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      `Expand-Archive -LiteralPath '${archive.replace(/'/g, "''")}' -DestinationPath '${dir.replace(/'/g, "''")}' -Force`],
    { windowsHide: true, maxBuffer: 1 << 24 })],
    ['adm-zip', async () => { const AdmZip = require('adm-zip'); new AdmZip(archive).extractAllTo(dir, true); }],
  ];
  let lastErr;
  for (const [name, fn] of attempts) {
    try {
      await fn();
      if (findJavaBinary(dir)) return;
      lastErr = new Error(`${name}: nem található a java a kicsomagolt fájlok között`);
    } catch (e) {
      lastErr = e;
      log(`Kicsomagolás (${name}) nem sikerült: ${String((e && (e.stderr || e.message)) || e).trim().slice(0, 300)}`);
    }
  }
  throw lastErr;
}

async function ensureJava(baseDir, log, progress, { maxMajor = 17 } = {}) {
  const dir = path.join(baseDir, 'runtime', 'java17');
  const existing = fs.existsSync(dir) && findJavaBinary(dir);
  if (existing && await javaMajor(existing) >= 17) return existing;

  // 1) már telepített Java (pl. a hivatalos Minecraft Launcheré) – nem kell letölteni
  const installed = await findInstalledJava(17, maxMajor, log);
  if (installed) return installed;

  const { os, arch } = platformInfo();
  const pkg = await resolvePackage(os, arch);
  const archive = path.join(baseDir, 'runtime', pkg.name || (os === 'windows' ? 'java17.zip' : 'java17.tar.gz'));

  // letöltés ellenőrzéssel; hiba esetén még kétszer próbálkozunk
  for (let attempt = 1; ; attempt++) {
    try {
      log(`Java 17 letöltése (${os}/${arch})${attempt > 1 ? ` – ${attempt}. próbálkozás` : ''}…`);
      await download(pkg.url, archive, { sha256: pkg.sha256, size: pkg.size, onProgress: (d, t) => progress('Java 17', d, t) });
      break;
    } catch (e) {
      fs.rmSync(archive, { force: true });
      if (attempt >= 3) throw e;
      log(`A letöltés hibás volt (${e.message}), újrapróbálom…`);
    }
  }

  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  log(`Java kicsomagolása (${path.basename(archive)}, ${fs.statSync(archive).size} bájt, ellenőrzőösszeg rendben)…`);
  await extract(archive, dir, log);
  fs.rmSync(archive, { force: true });
  const bin = findJavaBinary(dir);
  if (!bin) throw new Error('A Java kicsomagolása nem sikerült.');
  if (process.platform !== 'win32') fs.chmodSync(bin, 0o755);
  log(`Java kész: ${bin}`);
  return bin;
}

module.exports = { ensureJava, findInstalledJava, javaMajor };
