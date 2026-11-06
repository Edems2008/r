'use strict';
/* global nova */
const $ = (id) => document.getElementById(id);
let settings = {};
let profile = null;
let gameState = 'closed';

// ---- navigáció ----
document.querySelectorAll('aside nav button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('aside nav button').forEach((x) => x.classList.toggle('active', x === b));
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('hidden', p.id !== 'page-' + b.dataset.page));
  if (b.dataset.page === 'phone') refreshPairing();
  if (b.dataset.page === 'shaders') refreshShaders();
  if (b.dataset.page === 'hud') refreshEffects();
}));

async function refreshEffects() {
  const fx = await nova.getEffects();
  document.querySelectorAll('[data-fx]').forEach((el) => { el.checked = !!fx[el.dataset.fx]; });
}
document.querySelectorAll('[data-fx]').forEach((el) => el.addEventListener('change', async () => {
  await nova.setEffects({ [el.dataset.fx]: el.checked });
}));
document.querySelectorAll('[data-open]').forEach((b) => b.addEventListener('click', () => nova.openFolder(b.dataset.open)));

// ---- napló / haladás ----
const logEl = $('log');
const logLines = [];
nova.onLog((line) => {
  logLines.push(line);
  if (logLines.length > 3000) logLines.splice(0, 1000);
  logEl.textContent = logLines.join('\n');
  logEl.scrollTop = logEl.scrollHeight;
  if (gameState === 'preparing' && line && !line.startsWith('[MCLC]')) $('progressLabel').textContent = line.slice(0, 140);
});
nova.onProgress(({ label, done, total }) => {
  const pct = total ? Math.min(100, (done / total) * 100) : 0;
  $('progressBar').style.width = pct + '%';
  const size = total > 1e5 ? ` (${(done / 1048576).toFixed(1)} / ${(total / 1048576).toFixed(1)} MB)` : (total ? ` (${done}/${total})` : '');
  $('progressLabel').textContent = label + size;
});
nova.onGameState((s) => {
  gameState = s;
  const btn = $('btnPlay');
  btn.disabled = s !== 'closed';
  btn.querySelector('span').textContent = s === 'preparing' ? 'INDÍTÁS…' : s === 'running' ? 'FUT' : 'JÁTÉK';
  btn.classList.toggle('busy', s !== 'closed');
  if (s === 'running') { $('progressLabel').textContent = 'A játék fut – jó szórakozást!'; $('progressBar').style.width = '100%'; }
  if (s === 'closed') { $('progressBar').style.width = '0'; }
});

// ---- fiók ----
['heroSkin', 'accHead'].forEach((id) => {
  $(id).addEventListener('error', () => $(id).classList.add('failed'));
  $(id).addEventListener('load', () => $(id).classList.remove('failed'));
});
function renderAccount() {
  $('accName').textContent = profile ? profile.name : 'Nincs bejelentkezve';
  $('accHead').src = profile ? `https://mc-heads.net/avatar/${profile.id}/64` : '';
  $('heroSkin').src = `https://mc-heads.net/body/${profile ? profile.id : 'MHF_Steve'}/320`;
  $('account').classList.toggle('signed', !!profile);
  $('btnLogin').classList.toggle('hidden', !!profile);
  $('btnLogout').classList.toggle('hidden', !profile);
}
$('btnLogin').addEventListener('click', async () => {
  $('btnLogin').textContent = 'Bejelentkezés…';
  const r = await nova.login();
  $('btnLogin').textContent = 'Bejelentkezés Microsofttal';
  if (r.ok) profile = r.profile;
  else if (r.error && !/cancel/i.test(r.error)) alert('Sikertelen bejelentkezés: ' + r.error);
  renderAccount();
});
$('btnLogout').addEventListener('click', async () => { await nova.logout(); profile = null; renderAccount(); });

// ---- beállítások kötése ----
function bind(id, key, kind = 'value') {
  const el = $(id);
  el[kind === 'checked' ? 'checked' : 'value'] = settings[key] ?? '';
  el.addEventListener('change', async () => {
    const v = kind === 'checked' ? el.checked : (kind === 'number' ? Number(el.value) : el.value.trim());
    settings = await nova.setSettings({ [key]: v });
    if (key === 'relayUrl') refreshPairing();
  });
}

async function refreshPairing() {
  const p = await nova.pairInfo();
  $('pairCode').textContent = p.code;
  $('pairCode2').textContent = p.code;
  $('lanAddr').textContent = p.lan.length ? `${p.lan[0].address}:${p.port}` : 'nincs hálózat';
  $('lanQr').src = p.lanQr || '';
  $('relayQr').classList.toggle('hidden', !p.relayQr);
  if (p.relayQr) $('relayQr').src = p.relayQr;
}
$('btnNewCode').addEventListener('click', async () => {
  if (gameState === 'running' && !confirm('A játék fut – az új kód csak a következő indítástól él. Folytatod?')) return;
  await nova.newPairCode();
  refreshPairing();
});

async function refreshShaders() {
  const list = await nova.listShaders();
  const sel = $('shaderPack');
  sel.innerHTML = '<option value="">(nincs kiválasztva)</option>' +
    list.map((f) => `<option value="${f.replace(/"/g, '&quot;')}">${f.replace(/</g, '&lt;')}</option>`).join('');
  sel.value = list.includes(settings.shaderPack) ? settings.shaderPack : '';
}
$('shaderPack').addEventListener('change', async () => { settings = await nova.setSettings({ shaderPack: $('shaderPack').value }); });
$('btnRefreshShaders').addEventListener('click', refreshShaders);

$('ramGb').addEventListener('input', () => { $('ramLabel').textContent = $('ramGb').value + ' GB'; });
$('btnPickJava').addEventListener('click', async () => {
  const p = await nova.pickJava();
  if (p) { $('javaPath').value = p; settings = await nova.setSettings({ javaPath: p }); }
});

function updateStats(info) {
  $('statMods').textContent = (info.pack.mods.length + 1);
  $('statRam').textContent = settings.ramGb + ' GB';
  $('statShader').textContent = settings.shadersEnabled && settings.shaderPack ? 'BE' : 'KI';
}

// ---- játék ----
$('btnPlay').addEventListener('click', async () => {
  if (!profile) { alert('Előbb jelentkezz be a Microsoft fiókoddal (bal alul).'); return; }
  settings = await nova.setSettings({ serverAddress: $('serverAddress').value.trim() });
  const r = await nova.play();
  if (!r.ok) {
    alert('Nem sikerült elindítani: ' + r.error);
    document.querySelector('[data-page="log"]').click();
  }
});

// ---- indulás ----
(async () => {
  const info = await nova.appInfo();
  settings = await nova.getSettings();
  $('packName').textContent = info.pack.name;
  $('packMeta').textContent = `Minecraft ${info.pack.minecraft} • Forge ${info.pack.forge} • shaderek: Oculus + Embeddium`;
  $('appVersion').textContent = `Nova Launcher v${info.version} • ${info.gameDir}`;
  $('modList').innerHTML = info.pack.mods.map((m) => `<li><b>${m.slug}</b><span class="muted small">${m.why || ''}</span></li>`).join('');
  $('ramGb').max = Math.max(4, info.totalRamGb);
  $('shaderDownloads').innerHTML = info.pack.shaderpacks
    .map((s) => `<button class="ghost" data-slug="${s.slug}">⬇ ${s.name}</button>`).join('');
  $('shaderDownloads').querySelectorAll('button').forEach((b) => b.addEventListener('click', async () => {
    b.disabled = true;
    const r = await nova.downloadShader(b.dataset.slug);
    b.disabled = false;
    if (!r.ok) return alert('Letöltési hiba: ' + r.error);
    settings = await nova.setSettings({ shaderPack: r.file, shadersEnabled: true });
    $('shadersEnabled').checked = true;
    await refreshShaders();
    $('progressLabel').textContent = `Shader kész: ${r.file}`;
  }));

  bind('serverAddress', 'serverAddress');
  bind('autoJoin', 'autoJoin', 'checked');
  bind('relayUrl', 'relayUrl');
  bind('companionEnabled', 'companionEnabled', 'checked');
  bind('shadersEnabled', 'shadersEnabled', 'checked');
  bind('ramGb', 'ramGb', 'number');
  bind('fullscreen', 'fullscreen', 'checked');
  bind('closeOnLaunch', 'closeOnLaunch', 'checked');
  bind('javaPath', 'javaPath');
  $('ramLabel').textContent = settings.ramGb + ' GB';
  updateStats(info);

  $('btnLogin').textContent = 'Fiók betöltése…';
  profile = await nova.restoreLogin();
  $('btnLogin').textContent = 'Bejelentkezés Microsofttal';
  renderAccount();
})();
