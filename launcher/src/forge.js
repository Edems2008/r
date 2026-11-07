'use strict';
/*
 * Forge verzió feloldása: a modpack.json-ban "latest" / "recommended" is megadható – ilyenkor a Forge hivatalos
 * promóciós listájából kérdezzük le az aktuális 1.20.1-es buildet (az újabb modok gyakran újabb Forge-ot igényelnek).
 * Hálózati hiba esetén a legutóbb használt, végül a tartalék verzió marad.
 */
const fs = require('fs');
const path = require('path');
const { getJson } = require('./net');

const PROMOTIONS = 'https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json';

async function resolveForge(manifest, dataDir, log = () => {}) {
  const wanted = String(manifest.forge || '').trim();
  if (/^\d/.test(wanted)) return wanted; // pontos verzió megadva
  const kind = wanted === 'recommended' ? 'recommended' : 'latest';
  const cacheFile = path.join(dataDir, 'forge-version.json');
  try {
    const promos = (await getJson(PROMOTIONS)).promos || {};
    const v = promos[`${manifest.minecraft}-${kind}`] || promos[`${manifest.minecraft}-latest`];
    if (!v) throw new Error('nincs ilyen Forge verzió a listában');
    fs.mkdirSync(dataDir, { recursive: true });
    fs.writeFileSync(cacheFile, JSON.stringify({ minecraft: manifest.minecraft, forge: v }));
    log(`Forge verzió: ${manifest.minecraft}-${v} (${kind === 'latest' ? 'legújabb' : 'ajánlott'})`);
    return v;
  } catch (e) {
    try {
      const c = JSON.parse(fs.readFileSync(cacheFile, 'utf8'));
      if (c.minecraft === manifest.minecraft && c.forge) {
        log(`Forge lista nem elérhető (${e.message}), a legutóbbi verzió marad: ${c.forge}`);
        return c.forge;
      }
    } catch { /* nincs mentett verzió */ }
    const fb = manifest.forgeFallback || '47.3.0';
    log(`Forge lista nem elérhető (${e.message}), tartalék verzió: ${fb}`);
    return fb;
  }
}

module.exports = { resolveForge };
