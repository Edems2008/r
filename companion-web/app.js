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

  function startSession() {
    session++;
    connected = true;
    tiles.clear();
    tilesSince = 0;
    tilesEpoch = null;
    $('connect').classList.add('hidden');
    $('main').classList.remove('hidden');
    resizeCanvas();
    pollState(session);
    pollTiles(session);
  }

  function disconnect() {
    session++;
    connected = false;
    clearTimeout(stateTimer);
    clearTimeout(tilesTimer);
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

    $('title').textContent = `${state.name} • ${state.server === 'singleplayer' ? 'Egyjátékos' : state.server}`;
    if (follow) { viewX = state.x; viewZ = state.z; }
    if (prev.x !== state.x || prev.z !== state.z || prev.yaw !== state.yaw) dirty = true;

    const heading = compassHeading(state.yaw);
    $('mapInfo').innerHTML =
      `<b>${Math.floor(state.x)} / ${Math.floor(state.y)} / ${Math.floor(state.z)}</b><br>` +
      `${DIMS[state.dim] || state.dim} • ${prettyBiome(state.biome)}<br>` +
      `${dirName(heading)} ${Math.round(heading)}° • ${state.fps} FPS`;
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

  function renderStats() {
    if (!state.online) return;
    const hearts = (state.health / 2).toFixed(1);
    $('stats').innerHTML = [
      ['❤️ Életerő', `${hearts} / ${(state.maxHealth / 2).toFixed(0)}`],
      ['🍗 Éhség', `${state.food} / 20`],
      ['🌍 Dimenzió', DIMS[state.dim] || state.dim],
      ['🌲 Biom', prettyBiome(state.biome)],
      ['🕒 Játékidő', timeOfDay(state.time || 0)],
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
    let c = tiles.get(x + ',' + z);
    if (!c) {
      c = document.createElement('canvas');
      c.width = 16; c.height = 16;
      tiles.set(x + ',' + z, c);
    }
    const tctx = c.getContext('2d');
    const img = tctx.createImageData(16, 16);
    for (let i = 0, j = 0; i < 256; i++, j += 3) {
      const r = bin.charCodeAt(j), g = bin.charCodeAt(j + 1), bl = bin.charCodeAt(j + 2);
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

    if (state.online) {
      for (const p of state.players || []) drawPlayer(p, false, scale);
      drawPlayer(state, true, scale);
    }
  }

  function drawPlayer(p, self, scale) {
    const size = (self ? 26 : 20) * (window.devicePixelRatio || 1) / scale;
    ctx.save();
    ctx.translate(p.x, p.z);
    // a fej és a felirat mindig álló helyzetben
    if (rotateMap && state.online) ctx.rotate(compassHeading(state.yaw) * Math.PI / 180);
    if (self) {
      // irány-nyíl: MC yaw 0 = +Z (dél)
      ctx.save();
      const rad = Math.atan2(Math.cos(p.yaw * Math.PI / 180), -Math.sin(p.yaw * Math.PI / 180));
      ctx.rotate(rad - (rotateMap ? compassHeading(state.yaw) * Math.PI / 180 : 0));
      ctx.beginPath();
      ctx.moveTo(size * 1.25, 0);
      ctx.lineTo(size * 0.45, -size * 0.45);
      ctx.lineTo(size * 0.45, size * 0.45);
      ctx.closePath();
      ctx.fillStyle = '#4ade80';
      ctx.fill();
      ctx.restore();
    }
    const img = headImage(p.uuid);
    ctx.fillStyle = self ? '#4ade80' : '#ffffff';
    ctx.fillRect(-size / 2 - size * 0.08, -size / 2 - size * 0.08, size * 1.16, size * 1.16);
    if (img.complete && img.naturalWidth) ctx.drawImage(img, -size / 2, -size / 2, size, size);
    else { ctx.fillStyle = '#60a5fa'; ctx.fillRect(-size / 2, -size / 2, size, size); }
    if (!self) {
      ctx.font = `${size * 0.55}px system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.lineWidth = size * 0.12;
      ctx.strokeStyle = '#000';
      ctx.strokeText(p.name, 0, -size * 0.8);
      ctx.fillStyle = '#fff';
      ctx.fillText(p.name, 0, -size * 0.8);
    }
    ctx.restore();
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
  $('btnConnect').addEventListener('click', () => connect($('inAddr').value, $('inCode').value));
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
  const params = new URLSearchParams(location.search);
  const servedOverHttp = /^https?:$/.test(location.protocol);
  const initialAddr = params.get('addr') || store.get('addr', '') || (servedOverHttp ? location.origin : '');
  const initialCode = params.get('code') || store.get('code', '');
  $('inAddr').value = params.get('addr') || (params.get('code') && servedOverHttp ? location.origin : initialAddr);
  $('inCode').value = initialCode;

  requestAnimationFrame(draw);
  if ($('inAddr').value && initialCode) connect($('inAddr').value, initialCode);
})();
