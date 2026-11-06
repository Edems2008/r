'use strict';
/* Java 17 automatikus letöltése (Eclipse Temurin JRE) – a felhasználónak nem kell külön telepítenie. */
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const { promisify } = require('util');
const { download } = require('./net');

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

async function ensureJava(baseDir, log, progress) {
  const dir = path.join(baseDir, 'runtime', 'java17');
  const existing = fs.existsSync(dir) && findJavaBinary(dir);
  if (existing) return existing;

  const { os, arch } = platformInfo();
  log(`Java 17 letöltése (${os}/${arch})…`);
  const url = `https://api.adoptium.net/v3/binary/latest/17/ga/${os}/${arch}/jre/hotspot/normal/eclipse?project=jdk`;
  const archive = path.join(baseDir, 'runtime', os === 'windows' ? 'java17.zip' : 'java17.tar.gz');
  await download(url, archive, { onProgress: (d, t) => progress('Java 17', d, t) });

  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  log('Java kicsomagolása…');
  if (os === 'windows') {
    const AdmZip = require('adm-zip');
    new AdmZip(archive).extractAllTo(dir, true);
  } else {
    await run('tar', ['-xzf', archive, '-C', dir]);
  }
  fs.rmSync(archive, { force: true });
  const bin = findJavaBinary(dir);
  if (!bin) throw new Error('A Java kicsomagolása nem sikerült.');
  if (process.platform !== 'win32') fs.chmodSync(bin, 0o755);
  return bin;
}

module.exports = { ensureJava };
