'use strict';
/*
 * Hálózati segédfüggvények. A letöltés a Node beépített https/http moduljával történik
 * (átirányítás-követéssel), és minden fájlt ellenőrzőösszeggel / mérettel ellenőrzünk,
 * mielőtt a helyére kerülne – sérült fájl így nem maradhat a lemezen.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const http = require('http');
const https = require('https');

const UA = 'NovaLauncher/1.0 (github.com/edems2008/r)';

function request(url, { redirects = 8, timeout = 30000 } = {}) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : http;
    const req = lib.get(url, { headers: { 'User-Agent': UA, Accept: '*/*', 'Accept-Encoding': 'identity' } }, (res) => {
      const code = res.statusCode || 0;
      if (code >= 300 && code < 400 && res.headers.location) {
        res.resume();
        if (redirects <= 0) return reject(new Error(`Túl sok átirányítás: ${url}`));
        const next = new URL(res.headers.location, url).toString();
        return resolve(request(next, { redirects: redirects - 1, timeout }));
      }
      if (code !== 200) {
        res.resume();
        return reject(new Error(`HTTP ${code}: ${url}`));
      }
      resolve(res);
    });
    req.setTimeout(timeout, () => req.destroy(new Error(`Időtúllépés: ${url}`)));
    req.on('error', reject);
  });
}

async function getText(url) {
  const res = await request(url);
  const chunks = [];
  for await (const c of res) chunks.push(c);
  return Buffer.concat(chunks).toString('utf8');
}

async function getJson(url) {
  return JSON.parse(await getText(url));
}

/**
 * Letöltés fájlba ellenőrzéssel. Előbb .part fájlba ír, a végén ellenőrzi
 * (SHA-1 / SHA-256 / méret, és zip/jar esetén a „PK” fejlécet), csak utána nevezi át.
 */
async function download(url, dest, { sha1, sha256, size, onProgress } = {}) {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  const tmp = dest + '.part';
  fs.rmSync(tmp, { force: true });
  const res = await request(url, { timeout: 60000 });
  const total = Number(res.headers['content-length']) || 0;
  const h1 = crypto.createHash('sha1');
  const h256 = crypto.createHash('sha256');
  let done = 0;
  let head = null;

  await new Promise((resolve, reject) => {
    const out = fs.createWriteStream(tmp);
    res.on('data', (c) => {
      if (!head) head = Buffer.from(c.subarray(0, 4));
      done += c.length;
      h1.update(c);
      h256.update(c);
      if (onProgress) onProgress(done, total);
    });
    res.on('error', (e) => { out.destroy(); reject(e); });
    res.on('aborted', () => { out.destroy(); reject(new Error(`A letöltés megszakadt: ${url}`)); });
    out.on('error', reject);
    out.on('close', resolve); // 'close' = minden adat a lemezen és a fájl lezárva
    res.pipe(out);
  });

  const problems = [];
  if (total && done !== total) problems.push(`hiányos (${done}/${total} bájt)`);
  if (size && done !== size) problems.push(`rossz méret (${done}/${size} bájt)`);
  if (sha1 && h1.digest('hex').toLowerCase() !== String(sha1).toLowerCase()) problems.push('SHA-1 eltér');
  if (sha256 && h256.digest('hex').toLowerCase() !== String(sha256).toLowerCase()) problems.push('SHA-256 eltér');
  if (/\.(zip|jar)$/i.test(dest) && !(head && head[0] === 0x50 && head[1] === 0x4b)) problems.push('nem zip/jar fájl érkezett');
  if (problems.length) {
    fs.rmSync(tmp, { force: true });
    throw new Error(`Hibás letöltés (${path.basename(dest)}): ${problems.join(', ')}`);
  }
  fs.rmSync(dest, { force: true });
  fs.renameSync(tmp, dest);
  return dest;
}

/** Letöltés legfeljebb 3 próbálkozással. */
async function downloadWithRetry(url, dest, opts = {}, log = () => {}) {
  for (let attempt = 1; ; attempt++) {
    try {
      return await download(url, dest, opts);
    } catch (e) {
      if (attempt >= 3) throw e;
      log(`${e.message} – újrapróbálom (${attempt + 1}/3)…`);
      await new Promise((r) => setTimeout(r, 1500 * attempt));
    }
  }
}

function sha1File(file) {
  return crypto.createHash('sha1').update(fs.readFileSync(file)).digest('hex');
}

/**
 * Maven fájl (pl. Forge telepítő) biztosítása: a szerveren lévő .sha1 alapján ellenőrzi
 * a meglévő fájlt is – ha sérült, törli és újra letölti.
 */
async function ensureMavenFile(url, dest, { log = () => {}, onProgress } = {}) {
  let sha1 = null;
  try { sha1 = (await getText(`${url}.sha1`)).trim().split(/\s+/)[0]; } catch { /* nincs .sha1 a szerveren */ }
  if (fs.existsSync(dest)) {
    if (sha1 ? sha1File(dest) === sha1 : isZipFile(dest)) return dest;
    log(`A korábban letöltött ${path.basename(dest)} sérült, újratöltöm…`);
    fs.rmSync(dest, { force: true });
  }
  return downloadWithRetry(url, dest, { sha1: sha1 || undefined, onProgress }, log);
}

function isZipFile(file) {
  try {
    const fd = fs.openSync(file, 'r');
    const b = Buffer.alloc(4);
    fs.readSync(fd, b, 0, 4, 0);
    fs.closeSync(fd);
    return b[0] === 0x50 && b[1] === 0x4b;
  } catch {
    return false;
  }
}

module.exports = { getJson, getText, download, downloadWithRetry, ensureMavenFile, sha1File, UA };
