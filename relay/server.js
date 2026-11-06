/*
 * Nova Relay – internetes híd a játék (Nova Companion mod) és a telefon között.
 * Nincs külső függősége, ingyenesen futtatható pl. Render.com-on (Web Service, start: `node relay/server.js`).
 *
 *  POST /api/push?code=XXXX   (mod -> relay)  {key, state, map:{epoch,rev,tiles}}
 *  GET  /api/state?code=XXXX  (telefon)       – ugyanaz a formátum, mint a mod helyi szerverén
 *  GET  /api/tiles?code=XXXX&since=N&epoch=E
 *  GET  /                     – a webes felület (companion-web)
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 8080);
const WEB_ROOT = path.resolve(__dirname, '..', 'companion-web');
const MAX_BODY = 4 * 1024 * 1024;
const MAX_TILES = 6000;
const SESSION_TTL_MS = 30 * 60 * 1000;
const MAX_SESSIONS = 500;

/** code -> { key, state, epoch, rev, tiles: Map("x,z" -> {x,z,d,rev}), seen } */
const sessions = new Map();

function json(res, status, obj) {
  const body = typeof obj === 'string' ? obj : JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) { reject(new Error('too large')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function validCode(code) {
  return typeof code === 'string' && /^[A-Za-z0-9]{4,16}$/.test(code);
}

async function handlePush(req, res, code) {
  let body;
  try { body = JSON.parse(await readBody(req)); } catch { return json(res, 400, { error: 'bad_body' }); }
  if (!body || typeof body.key !== 'string' || body.key.length < 8) return json(res, 400, { error: 'bad_key' });

  let s = sessions.get(code);
  if (s && s.key !== body.key) return json(res, 403, { error: 'code_taken' });
  let resync = false;
  if (!s) {
    if (sessions.size >= MAX_SESSIONS) return json(res, 503, { error: 'full' });
    s = { key: body.key, state: { online: false }, epoch: null, rev: 0, tiles: new Map(), seen: Date.now() };
    sessions.set(code, s);
    // új munkamenet: ha a mod nem az elejétől küld, kérjük a teljes térképet
    // (pl. a relay újraindult, a mod pedig csak a változásokat küldené)
    if (Number(body.from) > 0) resync = true;
  }
  s.seen = Date.now();
  if (body.state && typeof body.state === 'object') s.state = body.state;

  const map = resync ? null : body.map;
  if (map && Array.isArray(map.tiles)) {
    if (map.epoch !== s.epoch) { s.tiles.clear(); s.epoch = map.epoch; }
    for (const t of map.tiles) {
      if (!t || typeof t.d !== 'string' || !Number.isInteger(t.x) || !Number.isInteger(t.z)) continue;
      s.rev++;
      const k = t.x + ',' + t.z;
      s.tiles.delete(k); // beszúrási sorrend = rev sorrend
      s.tiles.set(k, { x: t.x, z: t.z, d: t.d, rev: s.rev });
    }
    while (s.tiles.size > MAX_TILES) s.tiles.delete(s.tiles.keys().next().value);
  }
  json(res, 200, { ok: true, resync });
}

function handleState(res, s) {
  const st = Object.assign({}, s.state);
  if (Date.now() - s.seen > 10000) st.online = false; // a játék már nem küld
  json(res, 200, st);
}

function handleTiles(res, s, url) {
  let since = Number(url.searchParams.get('since') || 0);
  const epoch = url.searchParams.get('epoch');
  if (epoch === null || String(s.epoch) !== epoch) since = 0;
  const out = [];
  let more = false;
  let maxRev = since;
  for (const t of s.tiles.values()) {
    if (t.rev <= since) continue;
    if (out.length >= 400) { more = true; break; }
    out.push({ x: t.x, z: t.z, d: t.d });
    maxRev = t.rev;
  }
  json(res, 200, { epoch: s.epoch, rev: maxRev, more, tiles: out });
}

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml' };

function serveStatic(res, pathname) {
  if (pathname === '/') pathname = '/index.html';
  const file = path.resolve(WEB_ROOT, '.' + pathname);
  if (!file.startsWith(WEB_ROOT + path.sep)) { res.writeHead(400); return res.end(); }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://relay');
  if (req.method === 'OPTIONS') return json(res, 204, '');
  try {
    if (url.pathname === '/healthz') return json(res, 200, { ok: true, sessions: sessions.size });
    if (url.pathname.startsWith('/api/')) {
      const code = (url.searchParams.get('code') || '').toUpperCase();
      if (!validCode(code)) return json(res, 400, { error: 'bad_code' });
      if (url.pathname === '/api/push' && req.method === 'POST') return await handlePush(req, res, code);
      const s = sessions.get(code);
      if (!s) return json(res, 404, { error: 'unknown_code' });
      if (url.pathname === '/api/state') return handleState(res, s);
      if (url.pathname === '/api/tiles') return handleTiles(res, s, url);
      if (url.pathname === '/api/ping') return json(res, 200, { ok: true, app: 'nova-relay' });
      return json(res, 404, { error: 'not_found' });
    }
    if (req.method === 'GET') return serveStatic(res, decodeURIComponent(url.pathname));
    json(res, 405, { error: 'method' });
  } catch (e) {
    json(res, 500, { error: String(e.message || e) });
  }
});

setInterval(() => {
  const now = Date.now();
  for (const [code, s] of sessions) if (now - s.seen > SESSION_TTL_MS) sessions.delete(code);
}, 60 * 1000).unref();

server.listen(PORT, () => console.log(`Nova Relay fut: http://0.0.0.0:${PORT}`));
