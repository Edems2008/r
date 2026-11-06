'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const invoke = (ch) => (...args) => ipcRenderer.invoke(ch, ...args);
const on = (ch) => (cb) => {
  const fn = (_e, payload) => cb(payload);
  ipcRenderer.on(ch, fn);
  return () => ipcRenderer.removeListener(ch, fn);
};

contextBridge.exposeInMainWorld('nova', {
  appInfo: invoke('app:info'),
  getSettings: invoke('settings:get'),
  setSettings: invoke('settings:set'),
  restoreLogin: invoke('auth:restore'),
  login: invoke('auth:login'),
  logout: invoke('auth:logout'),
  pairInfo: invoke('pair:info'),
  newPairCode: invoke('pair:newCode'),
  listShaders: invoke('shaders:list'),
  downloadShader: invoke('shaders:download'),
  openFolder: invoke('open:folder'),
  pickJava: invoke('java:pick'),
  play: invoke('game:play'),
  onLog: on('log'),
  onProgress: on('progress'),
  onGameState: on('game:state'),
});
