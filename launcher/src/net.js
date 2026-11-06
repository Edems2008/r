'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Readable } = require('stream');
const { pipeline } = require('stream/promises');

const UA = 'NovaLauncher/1.0 (github.com/edems2008/r)';

async function getJson(url) {
  const r = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${url}`);
  return r.json();
}

/** Letöltés fájlba (atomikusan), opcionális SHA1 ellenőrzéssel és haladás jelzéssel. */
async function download(url, dest, { sha1, sha256, size, onProgress } = {}) {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  const r = await fetch(url, { headers: { 'User-Agent': UA }, redirect: 'follow' });
  if (!r.ok || !r.body) throw new Error(`Letöltési hiba (HTTP ${r.status}): ${url}`);
  const total = Number(r.headers.get('content-length')) || 0;
  let done = 0;
  const hash = crypto.createHash('sha1');
  const hash256 = crypto.createHash('sha256');
  const tmp = dest + '.part';
  const stream = Readable.fromWeb(r.body);
  stream.on('data', (c) => {
    done += c.length;
    hash.update(c);
    hash256.update(c);
    if (onProgress) onProgress(done, total);
  });
  await pipeline(stream, fs.createWriteStream(tmp));
  const got = hash.digest('hex');
  const got256 = hash256.digest('hex');
  const bad = (sha1 && got.toLowerCase() !== sha1.toLowerCase())
    || (sha256 && got256.toLowerCase() !== sha256.toLowerCase())
    || (size && done !== size);
  if (bad) {
    fs.rmSync(tmp, { force: true });
    throw new Error(`Hibás vagy hiányos letöltés: ${path.basename(dest)} (${done} bájt)`);
  }
  fs.renameSync(tmp, dest);
  return dest;
}

function sha1File(file) {
  return crypto.createHash('sha1').update(fs.readFileSync(file)).digest('hex');
}

module.exports = { getJson, download, sha1File, UA };
