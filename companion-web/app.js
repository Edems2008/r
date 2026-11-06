/* Nova Companion – telefonos felület (Android app WebView-ban, böngészőben, mod/relay által kiszolgálva) */
(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const store = {
    get(k, d) { try { const v = localStorage.getItem('nova.' + k); return v === null ? d : v; } catch { return d; } },
    set(k, v) { try { localStorage.setItem('nova.' + k, v); } catch { /* privát mód */ } },
  };

  // ---------- kapcsolat ----------
  let base = '';
  let code = '';
  let connected = false;
  let state = { online: false };
  let lastStateAt = 0;
  let stateTimer = null;
  let tilesTimer = null;
  let tilesSince = 0;
  let tilesEpoch = null;
  let session = 0;
  let onlineConn = null;
  let onlineMode = false;
  let statusText = '';
  let statusTimer = null;

  function normalizeBase(addr) {
    addr = (addr || '').trim();
    if (!addr) return '';
    if (!/^https?:\/\//i.test(addr)) addr = 'http://' + addr;
    return addr.replace(/\/+$/, '');
  }

  function api(path, params = {}) {
    const q = new URLSearchParams({ code, ...params });
    return fetch(`${base}${path}?${q}`, { cache: 'no-store' }).then(async (r) => {
      if (r.status === 401) throw new Error('Hibás párosító kód');
      if (r.status === 404) throw new Error('Nincs adat (fut a játék / jó a kód?)');
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }

  async function connect(addr, c) {
    base = normalizeBase(addr);
    code = (c || '').trim().toUpperCase();
    $('connectError').textContent = '';
    if (!base || !code) { $('connectError').textContent = 'Add meg a címet és a kódot.'; return; }
    $('btnConnect').disabled = true;
    $('btnConnect').textContent = 'Csatlakozás…';
    try {
      await api('/api/state');
      store.set('addr', addr.trim());
      store.set('code', code);
      store.set('mode', 'lan');
      onlineMode = false;
      startSession();
    } catch (e) {
      $('connectError').textContent = (e && e.message === 'Failed to fetch')
        ? 'Nem érem el a címet. Ugyanazon a WiFi-n vagy? Fut a játék? (Windows tűzfal!)'
        : String(e.message || e);
    } finally {
      $('btnConnect').disabled = false;
      $('btnConnect').textContent = 'Csatlakozás';
    }
  }

  async function connectOnline(raw) {
    const c = NovaOnline.normalize(raw);
    $('connectError').textContent = '';
    if (c.length < 10) { $('connectError').textContent = 'Az online kód 10 karakter (pl. ABCDE-12345). A launcher „Telefon” fülén találod.'; return; }
    store.set('online', c);
    store.set('mode', 'online');
    onlineMode = true;
    startSession();
    statusText = 'Csatlakozás…';
    try {
      const s = session;
      const conn = await NovaOnline.start({
        code: c,
        onState: (st) => { if (s !== session) return; lastStateAt = Date.now(); onState(st); updateStatus(); },
        onTiles: (epoch, list) => {
          if (s !== session) return;
          if (epoch !== tilesEpoch) { tiles.clear(); tilesEpoch = epoch; }
          for (const t of list) putTileRGB(t.x, t.z, t.rgb);
          dirty = true;
        },
        onStatus: (text) => { if (s !== session) return; statusText = text; if (!state.online) $('subtitle').textContent = text; },
      });
      if (s !== session) { conn.stop(); return; }
      onlineConn = conn;
    } catch (e) {
      disconnect();
      $('connectError').textContent = String(e.message || e);
    }
  }

  function startSession() {
    session++;
    connected = true;
    tiles.clear();
    tilesSince = 0;
    tilesEpoch = null;
    $('connect').classList.add('hidden');
    $('main').classList.remove('hidden');
    resizeCanvas();
    $('subtitle').textContent = 'Kapcsolódás…';
    clearInterval(statusTimer);
    statusTimer = setInterval(() => {
      updateStatus();
      if (onlineMode && Date.now() - lastStateAt > 10000 && state.online) { state = { online: false }; onState(state); }
      if (onlineMode && !state.online && statusText) $('subtitle').textContent = statusText;
    }, 1000);
    if (onlineMode) return; // az online kapcsolat maga küldi az adatokat
    pollState(session);
    pollTiles(session);
  }

  function disconnect() {
    session++;
    connected = false;
    clearTimeout(stateTimer);
    clearTimeout(tilesTimer);
    clearInterval(statusTimer);
    if (onlineConn) { onlineConn.stop(); onlineConn = null; }
    onlineMode = false;
    state = { online: false };
    $('main').classList.add('hidden');
    $('connect').classList.remove('hidden');
  }

  async function pollState(s) {
    if (s !== session) return;
    try {
      const st = await api('/api/state');
      if (s !== session) return;
      onState(st);
      lastStateAt = Date.now();
    } catch { /* újrapróbáljuk */ }
    updateStatus();
    stateTimer = setTimeout(() => pollState(s), 300);
  }

  async function pollTiles(s) {
    if (s !== session) return;
    let delay = 1000;
    try {
      const params = { since: tilesSince };
      if (tilesEpoch !== null) params.epoch = tilesEpoch;
      const res = await api('/api/tiles', params);
      if (s !== session) return;
      if (res.epoch !== tilesEpoch) { tiles.clear(); tilesEpoch = res.epoch; }
      for (const t of res.tiles) putTile(t.x, t.z, t.d);
      tilesSince = res.rev;
      if (res.more) delay = 50;
      dirty = true;
    } catch { delay = 2000; }
    tilesTimer = setTimeout(() => pollTiles(s), delay);
  }

  function updateStatus() {
    const el = $('status');
    const fresh = Date.now() - lastStateAt < 3000;
    el.className = 'dot ' + (fresh ? (state.online ? 'on' : 'wait') : 'off');
  }

  // ---------- állapot ----------
  let lastSkinUuid = null;
  const DIMS = { 'minecraft:overworld': 'Felszín', 'minecraft:the_nether': 'Nether', 'minecraft:the_end': 'End' };

  function onState(st) {
    const prev = state;
    state = st || { online: false };
    $('offline').classList.toggle('hidden', !!state.online);
    if (!state.online) { dirty = true; return; }

    $('title').textContent = state.name;
    $('subtitle').textContent = (state.server === 'singleplayer' ? 'Egyjátékos világ' : state.server) +
      (state.weather ? ` • ${state.weather}` : '') + (typeof state.temp === 'number' ? ` • ${state.temp}°C` : '');
    if (state.uuid && $('headAvatar').dataset.uuid !== state.uuid) {
      $('headAvatar').dataset.uuid = state.uuid;
      $('headAvatar').src = `https://mc-heads.net/avatar/${state.uuid.replace(/-/g, '')}/64`;
    }
    if (follow) { viewX = state.x; viewZ = state.z; }
    if (prev.x !== state.x || prev.z !== state.z || prev.yaw !== state.yaw) dirty = true;

    const heading = compassHeading(state.yaw);
    $('mapInfo').innerHTML =
      `<div class="coords"><span class="cx"><i>X</i>${Math.floor(state.x)}</span><span class="cy"><i>Y</i>${Math.floor(state.y)}</span><span class="cz"><i>Z</i>${Math.floor(state.z)}</span></div>` +
      `<div class="info-line">${DIMS[state.dim] || escapeHtml(String(state.dim))} • ${escapeHtml(prettyBiome(state.biome))}</div>` +
      `<div class="info-line">${dirName(heading)} ${Math.round(heading)}° • ${state.fps} FPS</div>`;
    $('mapCompass').style.transform = `rotate(${rotateMap ? -heading : 0}deg)`;

    if (state.uuid && state.uuid !== lastSkinUuid) { lastSkinUuid = state.uuid; loadSkin(state.uuid, state.name); }
    renderStats();
  }

  function compassHeading(yaw) { return (((yaw + 180) % 360) + 360) % 360; }
  function dirName(h) { return ['É', 'ÉK', 'K', 'DK', 'D', 'DNY', 'NY', 'ÉNY'][Math.round(h / 45) % 8]; }
  function prettyBiome(b) {
    if (!b) return '';
    const s = b.split(':').pop().replace(/_/g, ' ');
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
  function timeOfDay(t) {
    const h = Math.floor(((t / 1000) + 6) % 24);
    const m = Math.floor(((t % 1000) / 1000) * 60);
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  const WEATHER_SVG = {
    sun: '<circle cx="24" cy="24" r="9" fill="#ffd34d"/><g stroke="#ffb020" stroke-width="3" stroke-linecap="round"><path d="M24 5v5M24 38v5M5 24h5M38 24h5M10.5 10.5l3.5 3.5M34 34l3.5 3.5M10.5 37.5l3.5-3.5M34 14l3.5-3.5"/></g>',
    moon: '<path d="M30 8a16 16 0 1 0 10 26A13 13 0 0 1 30 8z" fill="#e8e4c8"/>',
    cloud: '<path d="M14 36h22a8 8 0 0 0 0-16 11 11 0 0 0-21-2 8 8 0 0 0-1 18z" fill="#dfe5ee"/>',
    rain: '<path d="M14 28h22a8 8 0 0 0 0-16 11 11 0 0 0-21-2 8 8 0 0 0-1 18z" fill="#b8c2cf"/><g stroke="#6cb6ff" stroke-width="3" stroke-linecap="round"><path d="M17 33l-3 7M25 33l-3 7M33 33l-3 7"/></g>',
    snow: '<path d="M14 28h22a8 8 0 0 0 0-16 11 11 0 0 0-21-2 8 8 0 0 0-1 18z" fill="#c9d2de"/><g fill="#fff"><circle cx="16" cy="36" r="2.4"/><circle cx="24" cy="40" r="2.4"/><circle cx="32" cy="36" r="2.4"/></g>',
    storm: '<path d="M14 28h22a8 8 0 0 0 0-16 11 11 0 0 0-21-2 8 8 0 0 0-1 18z" fill="#6b7280"/><path d="M26 26l-7 10h5l-3 9 9-12h-5l3-7z" fill="#ffd34d"/>',
  };
  function weatherIcon(label) {
    const l = (label || '').toLowerCase();
    const k = l.includes('vihar') ? 'storm' : l.includes('hav') ? 'snow' : l.includes('eső') ? 'rain'
      : l.includes('felhő') ? 'cloud' : l.includes('éjszaka') ? 'moon' : 'sun';
    return `<svg viewBox="0 0 48 48">${WEATHER_SVG[k]}</svg>`;
  }

  function vitalRow(icon, value, max, from, to, text) {
    const pct = Math.max(0, Math.min(100, (value / max) * 100));
    return `<div class="vital"><div class="ic">${icon}</div><div class="track"><div class="fill" style="width:${pct}%;background:linear-gradient(180deg,${from},${to})"></div></div><b>${text}</b></div>`;
  }

  function renderStats() {
    if (!state.online) return;
    $('weatherCard').innerHTML = `${weatherIcon(state.weather)}<div><b>${escapeHtml(state.weather || '-')}${typeof state.temp === 'number' ? ` • ${state.temp}°C` : ''}</b>` +
      `<span>${timeOfDay(state.time || 0)} • ${state.day ? state.day + '. nap' : ''} • ${escapeHtml(prettyBiome(state.biome))}</span></div>`;
    $('vitals').innerHTML =
      vitalRow('❤️', state.health, state.maxHealth || 20, '#ff6b5a', '#a81414', `${(state.health / 2).toFixed(1)} / ${((state.maxHealth || 20) / 2).toFixed(0)}`) +
      vitalRow('🍗', state.food, 20, '#f7be6a', '#a4570e', `${state.food} / 20`);
    $('stats').innerHTML = [
      ['🌍 Dimenzió', DIMS[state.dim] || state.dim],
      ['🌲 Biom', prettyBiome(state.biome)],
      ['🕒 Játékidő', timeOfDay(state.time || 0)],
      ['📅 Nap', state.day ? `${state.day}. nap (eltelt: ${state.day - 1})` : '-'],
      ['⚡ FPS', state.fps],
      ['👥 Játékosok a közelben', (state.players || []).length],
      ['🖥️ Szerver', state.server === 'singleplayer' ? 'Egyjátékos' : state.server],
    ].map(([k, v]) => `<div>${k}<b>${escapeHtml(String(v))}</b></div>`).join('');
  }

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  // ---------- térkép ----------
  const canvas = $('map');
  const ctx = canvas.getContext('2d');
  const tiles = new Map(); // "x,z" -> canvas 16x16
  let viewX = 0, viewZ = 0;
  let zoom = Number(store.get('zoom', 3)); // css pixel / blokk
  let follow = true;
  let rotateMap = store.get('rotate', '0') === '1';
  let dirty = true;
  const heads = new Map();

  function putTile(x, z, b64) {
    const bin = atob(b64);
    const rgb = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) rgb[i] = bin.charCodeAt(i);
    putTileRGB(x, z, rgb);
  }

  function putTileRGB(x, z, rgb) {
    let c = tiles.get(x + ',' + z);
    if (!c) {
      c = document.createElement('canvas');
      c.width = 16; c.height = 16;
      tiles.set(x + ',' + z, c);
    }
    const tctx = c.getContext('2d');
    const img = tctx.createImageData(16, 16);
    for (let i = 0, j = 0; i < 256; i++, j += 3) {
      const r = rgb[j], g = rgb[j + 1], bl = rgb[j + 2];
      img.data[i * 4] = r; img.data[i * 4 + 1] = g; img.data[i * 4 + 2] = bl;
      img.data[i * 4 + 3] = (r | g | bl) === 0 ? 0 : 255;
    }
    tctx.putImageData(img, 0, 0);
  }

  function resizeCanvas() {
    const dpr = window.devicePixelRatio || 1;
    const r = canvas.getBoundingClientRect();
    canvas.width = Math.max(1, Math.round(r.width * dpr));
    canvas.height = Math.max(1, Math.round(r.height * dpr));
    dirty = true;
  }

  function headImage(uuid) {
    let img = heads.get(uuid);
    if (!img) {
      img = new Image();
      img.onload = () => { dirty = true; };
      img.src = `https://mc-heads.net/avatar/${uuid}/32`;
      heads.set(uuid, img);
    }
    return img;
  }

  function draw() {
    requestAnimationFrame(draw);
    if (!connected || !dirty || $('tabMap').classList.contains('hidden')) return;
    dirty = false;

    const dpr = window.devicePixelRatio || 1;
    const W = canvas.width, H = canvas.height;
    const scale = zoom * dpr; // eszköz-pixel / blokk
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.fillStyle = '#0b0e13';
    ctx.fillRect(0, 0, W, H);
    ctx.imageSmoothingEnabled = false;

    const heading = state.online ? compassHeading(state.yaw) : 0;
    ctx.translate(W / 2, H / 2);
    if (rotateMap && state.online) ctx.rotate(-heading * Math.PI / 180);
    ctx.scale(scale, scale);
    ctx.translate(-viewX, -viewZ);

    // természetesebb színek: a vanilla térképszínek kissé tompítva, több kontraszttal
    ctx.filter = 'saturate(0.82) contrast(1.08) brightness(1.02)';
    const radius = Math.hypot(W, H) / 2 / scale + 16;
    const minCx = Math.floor((viewX - radius) / 16), maxCx = Math.floor((viewX + radius) / 16);
    const minCz = Math.floor((viewZ - radius) / 16), maxCz = Math.floor((viewZ + radius) / 16);
    for (let cx = minCx; cx <= maxCx; cx++) {
      for (let cz = minCz; cz <= maxCz; cz++) {
        const t = tiles.get(cx + ',' + cz);
        // +0.02 átfedés, hogy ne legyenek hézagok a chunkok között
        if (t) ctx.drawImage(t, cx * 16, cz * 16, 16.02, 16.02);
      }
    }
    ctx.filter = 'none';

    // halvány chunk-rács nagy nagyításnál
    if (zoom >= 6) {
      ctx.strokeStyle = 'rgba(0,0,0,0.18)';
      ctx.lineWidth = 1 / scale;
      ctx.beginPath();
      for (let cx = minCx; cx <= maxCx + 1; cx++) { ctx.moveTo(cx * 16, minCz * 16); ctx.lineTo(cx * 16, (maxCz + 1) * 16); }
      for (let cz = minCz; cz <= maxCz + 1; cz++) { ctx.moveTo(minCx * 16, cz * 16); ctx.lineTo((maxCx + 1) * 16, cz * 16); }
      ctx.stroke();
    }

    if (state.online) {
      for (const p of state.players || []) drawPlayer(p, false, scale);
      drawPlayer(state, true, scale);
    }
  }

  function drawPlayer(p, self, scale) {
    const dpr = window.devicePixelRatio || 1;
    const size = (self ? 26 : 20) * dpr / scale;
    const upright = rotateMap && state.online ? compassHeading(state.yaw) * Math.PI / 180 : 0;
    ctx.save();
    ctx.translate(p.x, p.z);
    // látómező-kúp (MC yaw 0 = +Z / dél)
    const facing = Math.atan2(Math.cos(p.yaw * Math.PI / 180), -Math.sin(p.yaw * Math.PI / 180));
    const coneR = size * (self ? 3.6 : 2.4);
    const grad = ctx.createRadialGradient(0, 0, size * 0.4, 0, 0, coneR);
    grad.addColorStop(0, self ? 'rgba(74,222,128,0.55)' : 'rgba(255,255,255,0.35)');
    grad.addColorStop(1, self ? 'rgba(74,222,128,0)' : 'rgba(255,255,255,0)');
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.arc(0, 0, coneR, facing - 0.55, facing + 0.55);
    ctx.closePath();
    ctx.fill();
    ctx.rotate(upright);
    // árnyék + keret + fej
    ctx.shadowColor = 'rgba(0,0,0,0.6)';
    ctx.shadowBlur = 6 * dpr;
    ctx.fillStyle = self ? '#4ade80' : '#f1f5f9';
    roundRect(-size / 2 - size * 0.12, -size / 2 - size * 0.12, size * 1.24, size * 1.24, size * 0.22);
    ctx.fill();
    ctx.shadowBlur = 0;
    const img = headImage(p.uuid);
    if (img.complete && img.naturalWidth) ctx.drawImage(img, -size / 2, -size / 2, size, size);
    else { ctx.fillStyle = '#60a5fa'; ctx.fillRect(-size / 2, -size / 2, size, size); }
    if (!self) {
      // név „pirula” a fej fölött
      ctx.font = `600 ${size * 0.55}px system-ui, sans-serif`;
      const tw = ctx.measureText(p.name).width;
      const ph = size * 0.8, py = -size * 1.45;
      ctx.fillStyle = 'rgba(10,14,20,0.8)';
      roundRect(-tw / 2 - size * 0.3, py - ph / 2, tw + size * 0.6, ph, ph / 2);
      ctx.fill();
      ctx.fillStyle = '#fff';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(p.name, 0, py + size * 0.03);
    }
    ctx.restore();
  }

  function roundRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function setZoom(z) {
    zoom = Math.min(24, Math.max(0.5, z));
    store.set('zoom', String(zoom));
    dirty = true;
  }

  function setFollow(f) {
    follow = f;
    $('btnFollow').classList.toggle('active', f);
    if (f && state.online) { viewX = state.x; viewZ = state.z; }
    dirty = true;
  }

  // érintés: egy ujj = mozgatás, két ujj = zoom
  const pointers = new Map();
  let pinchStart = null;
  canvas.addEventListener('pointerdown', (e) => {
    canvas.setPointerCapture(e.pointerId);
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      pinchStart = { d: Math.hypot(a.x - b.x, a.y - b.y), zoom };
    }
  });
  canvas.addEventListener('pointermove', (e) => {
    const prev = pointers.get(e.pointerId);
    if (!prev) return;
    const cur = { x: e.clientX, y: e.clientY };
    pointers.set(e.pointerId, cur);
    if (pointers.size === 2 && pinchStart) {
      const [a, b] = [...pointers.values()];
      setZoom(pinchStart.zoom * Math.hypot(a.x - b.x, a.y - b.y) / pinchStart.d);
    } else if (pointers.size === 1) {
      let dx = (cur.x - prev.x) / zoom, dz = (cur.y - prev.y) / zoom;
      if (Math.abs(dx) + Math.abs(dz) > 0.01 && follow) setFollow(false);
      if (rotateMap && state.online) {
        const r = compassHeading(state.yaw) * Math.PI / 180;
        [dx, dz] = [dx * Math.cos(r) - dz * Math.sin(r), dx * Math.sin(r) + dz * Math.cos(r)];
      }
      viewX -= dx; viewZ -= dz;
      dirty = true;
    }
  });
  const endPointer = (e) => { pointers.delete(e.pointerId); if (pointers.size < 2) pinchStart = null; };
  canvas.addEventListener('pointerup', endPointer);
  canvas.addEventListener('pointercancel', endPointer);
  canvas.addEventListener('wheel', (e) => { e.preventDefault(); setZoom(zoom * (e.deltaY < 0 ? 1.15 : 1 / 1.15)); }, { passive: false });

  // ---------- skin ----------
  let skinViewer = null;
  const SKIN_SOURCES = (u) => [
    `https://crafatar.com/skins/${u}`,
    `https://mc-heads.net/skin/${u}`,
    `https://minotar.net/skin/${u}`,
  ];

  async function loadSkin(uuid, name) {
    $('skinName').textContent = name || '-';
    const u = uuid.replace(/-/g, '');
    if (window.skinview3d) {
      try {
        if (!skinViewer) {
          const wrap = $('skinWrap');
          skinViewer = new skinview3d.SkinViewer({
            canvas: $('skin3d'),
            width: wrap.clientWidth || 300,
            height: wrap.clientHeight || 400,
          });
          skinViewer.zoom = 0.85;
          skinViewer.autoRotate = true;
          skinViewer.autoRotateSpeed = 0.6;
          skinViewer.animation = new skinview3d.IdleAnimation();
        }
        for (const url of SKIN_SOURCES(u)) {
          try { await skinViewer.loadSkin(url); show3d(true); return; } catch { /* következő forrás */ }
        }
      } catch { /* WebGL nem elérhető */ }
    }
    show3d(false);
    $('skin2d').src = `https://mc-heads.net/body/${u}/300`;
  }

  function show3d(on) {
    $('skin3d').classList.toggle('hidden', !on);
    $('skin2d').classList.toggle('hidden', on);
  }

  function resizeSkin() {
    if (!skinViewer) return;
    const wrap = $('skinWrap');
    if (wrap.clientWidth) skinViewer.setSize(wrap.clientWidth, wrap.clientHeight);
  }

  // ---------- UI ----------
  ['headAvatar', 'skin2d'].forEach((id) => {
    $(id).addEventListener('error', () => { $(id).style.visibility = 'hidden'; });
    $(id).addEventListener('load', () => { $(id).style.visibility = ''; });
  });
  $('btnConnect').addEventListener('click', () => {
    if ($('btnConnect').dataset.mode === 'lan') connect($('inAddr').value, $('inCode').value);
    else connectOnline($('inOnline').value);
  });
  $('inCode').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('btnConnect').click(); });
  $('btnDisconnect').addEventListener('click', disconnect);
  $('btnZoomIn').addEventListener('click', () => setZoom(zoom * 1.4));
  $('btnZoomOut').addEventListener('click', () => setZoom(zoom / 1.4));
  $('btnFollow').addEventListener('click', () => setFollow(!follow));
  $('btnRotate').addEventListener('click', () => {
    rotateMap = !rotateMap;
    store.set('rotate', rotateMap ? '1' : '0');
    $('btnRotate').classList.toggle('active', rotateMap);
    dirty = true;
  });
  $('btnRotate').classList.toggle('active', rotateMap);
  $('btnWalk').addEventListener('click', () => {
    if (!skinViewer) return;
    skinViewer.animation = skinViewer.animation instanceof skinview3d.WalkingAnimation
      ? new skinview3d.IdleAnimation() : new skinview3d.WalkingAnimation();
  });
  $('btnSpin').addEventListener('click', () => { if (skinViewer) skinViewer.autoRotate = !skinViewer.autoRotate; });

  document.querySelectorAll('nav button').forEach((b) => b.addEventListener('click', () => {
    document.querySelectorAll('nav button').forEach((x) => x.classList.toggle('active', x === b));
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('hidden', t.id !== b.dataset.tab));
    if (b.dataset.tab === 'tabMap') resizeCanvas();
    if (b.dataset.tab === 'tabSkin') resizeSkin();
    dirty = true;
  }));
  window.addEventListener('resize', () => { resizeCanvas(); resizeSkin(); });

  // Kezdeti értékek: URL paraméter (QR-kód) > mentett > a kiszolgáló saját címe
  // mód-választó a párosító képernyőn
  function setMode(m) {
    document.querySelectorAll('.seg button').forEach((b) => b.classList.toggle('active', b.dataset.mode === m));
    $('paneOnline').classList.toggle('hidden', m !== 'online');
    $('paneLan').classList.toggle('hidden', m !== 'lan');
    $('btnConnect').dataset.mode = m;
    $('connectError').textContent = '';
  }
  document.querySelectorAll('.seg button').forEach((b) => b.addEventListener('click', () => setMode(b.dataset.mode)));
  $('inOnline').addEventListener('input', () => {
    // automatikus formázás: ABCDE-12345
    const n = NovaOnline.normalize($('inOnline').value).slice(0, 10);
    $('inOnline').value = n.length > 5 ? n.slice(0, 5) + '-' + n.slice(5) : n;
  });
  $('inOnline').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('btnConnect').click(); });

  // Kezdeti értékek: URL paraméter (QR-kód / nova:// link) > mentett > a kiszolgáló saját címe
  const params = new URLSearchParams(location.search);
  const servedOverHttp = /^https?:$/.test(location.protocol);
  const initialAddr = params.get('addr') || store.get('addr', '') || (servedOverHttp ? location.origin : '');
  const initialCode = params.get('code') || store.get('code', '');
  const initialOnline = params.get('online') || store.get('online', '');
  $('inAddr').value = params.get('addr') || (params.get('code') && servedOverHttp ? location.origin : initialAddr);
  $('inCode').value = initialCode;
  if (initialOnline) {
    const n = NovaOnline.normalize(initialOnline);
    $('inOnline').value = n.length > 5 ? n.slice(0, 5) + '-' + n.slice(5) : n;
  }
  const startMode = params.get('online') ? 'online' : params.get('code') ? 'lan' : store.get('mode', 'online');
  setMode(startMode);

  requestAnimationFrame(draw);
  if (startMode === 'online' && initialOnline) connectOnline(initialOnline);
  else if (startMode === 'lan' && $('inAddr').value && initialCode) connect($('inAddr').value, initialCode);
})();
