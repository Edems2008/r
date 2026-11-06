'use strict';
/* A Nova Companion mod configjának kezelése (párosító kód, port, relay) + QR-kód a telefonhoz. */
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const QRCode = require('qrcode');

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function randomCode(len) {
  let s = '';
  const bytes = crypto.randomBytes(len);
  for (let i = 0; i < len; i++) s += ALPHABET[bytes[i] % ALPHABET.length];
  return s;
}

function configPath(gameDir) {
  return path.join(gameDir, 'config', 'novacompanion.json');
}

function readConfig(gameDir) {
  let cfg = {};
  try { cfg = JSON.parse(fs.readFileSync(configPath(gameDir), 'utf8')); } catch { /* még nincs */ }
  if (!cfg.code) cfg.code = randomCode(6);
  if (!cfg.relayKey) cfg.relayKey = randomCode(24);
  if (!cfg.port) cfg.port = 25599;
  if (cfg.relayUrl === undefined) cfg.relayUrl = '';
  if (cfg.companionEnabled === undefined) cfg.companionEnabled = true;
  return cfg;
}

function writeConfig(gameDir, patch) {
  const cfg = Object.assign(readConfig(gameDir), patch);
  fs.mkdirSync(path.dirname(configPath(gameDir)), { recursive: true });
  fs.writeFileSync(configPath(gameDir), JSON.stringify(cfg, null, 2));
  return cfg;
}

function lanAddresses() {
  const out = [];
  for (const [name, list] of Object.entries(os.networkInterfaces())) {
    for (const a of list || []) {
      if (a.family === 'IPv4' && !a.internal && !/^169\.254\./.test(a.address)) out.push({ name, address: a.address });
    }
  }
  // a tipikus otthoni hálózatok előre
  out.sort((a, b) => Number(/^192\.168\./.test(b.address)) - Number(/^192\.168\./.test(a.address)));
  return out;
}

async function pairingInfo(gameDir) {
  const cfg = writeConfig(gameDir, {});
  const lan = lanAddresses();
  const lanUrl = lan.length ? `http://${lan[0].address}:${cfg.port}/?code=${cfg.code}` : null;
  const relayBase = (cfg.relayUrl || '').replace(/\/+$/, '');
  const relayUrl = relayBase ? `${relayBase}/?code=${cfg.code}` : null;
  const qr = (text) => (text ? QRCode.toDataURL(text, { margin: 1, width: 220 }) : null);
  return {
    code: cfg.code,
    port: cfg.port,
    relay: relayBase,
    lan,
    lanUrl,
    relayUrl,
    lanQr: await qr(lanUrl),
    relayQr: await qr(relayUrl),
  };
}

const FX_KEYS = ['waterDrops', 'rainDrops', 'fireEffect', 'underwaterTint', 'pauseClock',
  'poisonDizzy', 'hallucinations', 'villagerParty', 'speedStretch', 'jumpFloat'];
const HUD_DEFAULTS = { vitals: [0.5, 1.0], clock: [1.0, 0.0], realclock: [1.0, 0.09], weather: [1.0, 0.075], nightvision: [1.0, 0.2] };

/** Képernyő-effektek és óra beállításai (a mod configjában tárolva, így a játékon belüli szerkesztővel is szinkronban van). */
function readEffects(gameDir) {
  const cfg = readConfig(gameDir);
  const out = {};
  for (const k of FX_KEYS) out[k] = cfg[k] !== false;
  const hud = cfg.hud || {};
  out.vitals = !hud.vitals || hud.vitals.enabled !== false;
  out.clock = !hud.clock || hud.clock.enabled !== false;
  out.realclock = !!(hud.realclock && hud.realclock.enabled);
  out.weather = !hud.weather || hud.weather.enabled !== false;
  out.nightvision = !hud.nightvision || hud.nightvision.enabled !== false;
  return out;
}

function writeEffects(gameDir, patch) {
  const cfg = readConfig(gameDir);
  for (const k of FX_KEYS) if (k in patch) cfg[k] = !!patch[k];
  cfg.hud = cfg.hud || {};
  for (const k of Object.keys(HUD_DEFAULTS)) {
    if (!(k in patch)) continue;
    if (cfg.hud[k]) cfg.hud[k].enabled = !!patch[k];
    else if (patch[k] === false || k === 'realclock') {
      // még nincs pozíció -> a mod alapértelmezett helyére kerül
      const defaults = HUD_DEFAULTS[k];
      cfg.hud[k] = { enabled: !!patch[k], x: defaults[0], y: defaults[1], scale: 1.0 };
    }
  }
  writeConfig(gameDir, cfg);
  return readEffects(gameDir);
}

module.exports = { readConfig, writeConfig, pairingInfo, randomCode, readEffects, writeEffects };
