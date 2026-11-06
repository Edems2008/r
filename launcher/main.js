'use strict';
const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { AuthManager } = require('./src/auth');
const game = require('./src/game');
const pack = require('./src/pack');
const companion = require('./src/companion');
const official = require('./src/official');

const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, 'modpack.json'), 'utf8'));
const DATA_DIR = path.join(app.getPath('appData'), '.novalauncher');
const GAME_DIR = path.join(DATA_DIR, 'game');
const SETTINGS_FILE = path.join(DATA_DIR, 'settings.json');
const PACK_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'pack')
  : path.join(__dirname, 'build-resources');

const totalRamGb = Math.floor(os.totalmem() / 1024 ** 3);
const DEFAULT_SETTINGS = {
  serverAddress: manifest.defaultServer || '',
  autoJoin: true,
  ramGb: Math.min(8, Math.max(3, Math.floor(totalRamGb / 2))),
  fullscreen: false,
  shadersEnabled: true,
  shaderPack: '',
  companionEnabled: true,
  relayUrl: '',
  javaPath: '',
  closeOnLaunch: false,
};

function loadSettings() {
  try { return { ...DEFAULT_SETTINGS, ...JSON.parse(fs.readFileSync(SETTINGS_FILE, 'utf8')) }; } catch { return { ...DEFAULT_SETTINGS }; }
}
function saveSettings(s) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(SETTINGS_FILE, JSON.stringify(s, null, 2));
}

let win;
let settings = loadSettings();
const auth = new AuthManager(DATA_DIR);
let running = null;

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}
const log = (line) => send('log', line);
const progress = (label, done, total) => send('progress', { label, done, total });

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 700,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#0f1318',
    title: 'Nova Launcher',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
}

app.whenReady().then(() => {
  fs.mkdirSync(GAME_DIR, { recursive: true });
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

// ---- IPC ----
ipcMain.handle('app:info', () => ({
  version: app.getVersion(),
  pack: { name: manifest.name, minecraft: manifest.minecraft, forge: manifest.forge, mods: manifest.modrinth, shaderpacks: manifest.shaderpacks },
  totalRamGb,
  gameDir: GAME_DIR,
}));

ipcMain.handle('settings:get', () => settings);
ipcMain.handle('settings:set', (_e, patch) => {
  settings = { ...settings, ...patch };
  saveSettings(settings);
  if ('relayUrl' in patch || 'companionEnabled' in patch) {
    companion.writeConfig(GAME_DIR, { relayUrl: settings.relayUrl, companionEnabled: settings.companionEnabled });
  }
  return settings;
});

ipcMain.handle('auth:restore', () => auth.restore());
ipcMain.handle('auth:login', async () => {
  try { return { ok: true, profile: await auth.login() }; } catch (e) {
    return { ok: false, error: typeof e === 'string' ? e : (e && (e.message || e.name)) || String(e) };
  }
});
ipcMain.handle('auth:logout', () => { auth.logout(); return true; });

ipcMain.handle('pair:info', () => companion.pairingInfo(GAME_DIR));
ipcMain.handle('pair:newCode', () => {
  companion.writeConfig(GAME_DIR, { code: companion.randomCode(6), onlineCode: companion.randomCode(10) });
  return companion.pairingInfo(GAME_DIR);
});

ipcMain.handle('fx:get', () => companion.readEffects(GAME_DIR));
ipcMain.handle('fx:set', (_e, patch) => companion.writeEffects(GAME_DIR, patch));

ipcMain.handle('shaders:list', () => pack.listShaderpacks(GAME_DIR));
ipcMain.handle('shaders:download', async (_e, slug) => {
  try { return { ok: true, file: await pack.downloadShaderpack(slug, GAME_DIR, manifest.minecraft, log, progress) }; } catch (e) {
    return { ok: false, error: e.message };
  }
});

ipcMain.handle('open:folder', (_e, which) => {
  const dir = path.join(GAME_DIR, which === 'root' ? '' : which);
  fs.mkdirSync(dir, { recursive: true });
  return shell.openPath(dir);
});

ipcMain.handle('java:pick', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Java 17 kiválasztása', properties: ['openFile'] });
  return r.canceled ? null : r.filePaths[0];
});

ipcMain.handle('game:play', async () => {
  if (running) return { ok: false, error: 'A játék már fut.' };
  running = true;
  send('game:state', 'preparing');
  try {
    const proc = await game.launch({
      gameDir: GAME_DIR,
      dataDir: DATA_DIR,
      packDir: PACK_DIR,
      manifest,
      settings,
      getAuth: () => auth.mclc(),
      log,
      progress,
      onClose: (code) => {
        running = null;
        send('game:state', 'closed');
        log(`A játék kilépett (kód: ${code}).`);
        if (settings.closeOnLaunch && win) win.show();
      },
    });
    running = proc;
    send('game:state', 'running');
    if (settings.closeOnLaunch && win) win.hide();
    return { ok: true };
  } catch (e) {
    running = null;
    send('game:state', 'closed');
    log(`HIBA: ${e.stack || e.message || e}`);
    return { ok: false, error: e.message || String(e) };
  }
});

// Telepítés a hivatalos Minecraft Launcherbe (bejelentkezés nélkül – a hivatalos launcher fiókja indítja a játékot)
ipcMain.handle('official:install', async () => {
  if (running) return { ok: false, error: 'Épp fut egy telepítés vagy a játék.' };
  running = true;
  send('game:state', 'preparing');
  try {
    const r = await official.installIntoOfficial({ gameDir: GAME_DIR, dataDir: DATA_DIR, packDir: PACK_DIR, manifest, settings, log, progress });
    return { ok: true, ...r };
  } catch (e) {
    log(`HIBA: ${e.stack || e.message || e}`);
    return { ok: false, error: e.message || String(e) };
  } finally {
    running = null;
    send('game:state', 'closed');
  }
});
