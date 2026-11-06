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
      log(`Kicsomagolás (${name}) nem sikerült, másik módszer…`);
    }
  }
  throw lastErr;
}

async function ensureJava(baseDir, log, progress) {
  const dir = path.join(baseDir, 'runtime', 'java17');
  const existing = fs.existsSync(dir) && findJavaBinary(dir);
  if (existing) return existing;

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
  log('Java kicsomagolása…');
  await extract(archive, dir, log);
  fs.rmSync(archive, { force: true });
  const bin = findJavaBinary(dir);
  if (!bin) throw new Error('A Java kicsomagolása nem sikerült.');
  if (process.platform !== 'win32') fs.chmodSync(bin, 0o755);
  log(`Java kész: ${bin}`);
  return bin;
}

module.exports = { ensureJava };
