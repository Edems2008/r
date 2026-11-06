'use strict';
/* Modrinth API – a build script és a launcher is ezt használja. */
const { getJson } = require('./net');

const API = 'https://api.modrinth.com/v2';

/**
 * A projekt legfrissebb, adott loaderre és MC verzióra készült fájlja.
 * @returns {Promise<{slug, version, filename, url, sha1}>}
 */
async function resolveLatest(slug, { loader = 'forge', gameVersion = '1.20.1', type = 'mod' } = {}) {
  const q = new URLSearchParams({ game_versions: JSON.stringify([gameVersion]) });
  if (type === 'mod') q.set('loaders', JSON.stringify([loader]));
  const versions = await getJson(`${API}/project/${encodeURIComponent(slug)}/version?${q}`);
  if (!versions.length) throw new Error(`Nincs ${loader} ${gameVersion} verzió: ${slug}`);
  const order = { release: 0, beta: 1, alpha: 2 };
  versions.sort((a, b) =>
    (order[a.version_type] - order[b.version_type]) || (new Date(b.date_published) - new Date(a.date_published)));
  const v = versions[0];
  const file = v.files.find((f) => f.primary) || v.files[0];
  return { slug, version: v.version_number, filename: file.filename, url: file.url, sha1: file.hashes.sha1 };
}

module.exports = { resolveLatest };
