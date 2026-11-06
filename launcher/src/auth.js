'use strict';
/* Microsoft / Xbox bejelentkezés (msmc). A refresh token titkosítva (safeStorage) kerül mentésre. */
const fs = require('fs');
const path = require('path');
const { safeStorage } = require('electron');
const { Auth } = require('msmc');

class AuthManager {
  constructor(dataDir) {
    this.file = path.join(dataDir, 'account.dat');
    this.auth = new Auth('select_account');
    this.mc = null; // msmc Minecraft objektum
  }

  _saveRefresh(token) {
    const data = safeStorage.isEncryptionAvailable()
      ? Buffer.concat([Buffer.from('E1'), safeStorage.encryptString(token)])
      : Buffer.concat([Buffer.from('P1'), Buffer.from(token, 'utf8')]);
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    fs.writeFileSync(this.file, data);
  }

  _loadRefresh() {
    try {
      const buf = fs.readFileSync(this.file);
      const tag = buf.subarray(0, 2).toString();
      const body = buf.subarray(2);
      if (tag === 'E1') return safeStorage.decryptString(body);
      if (tag === 'P1') return body.toString('utf8');
    } catch { /* nincs mentett fiók */ }
    return null;
  }

  profile() {
    if (!this.mc || !this.mc.profile) return null;
    return { name: this.mc.profile.name, id: this.mc.profile.id };
  }

  async login() {
    const xbox = await this.auth.launch('electron', {
      width: 520, height: 680, resizable: false, title: 'Bejelentkezés Microsoft fiókkal',
    });
    this.mc = await xbox.getMinecraft();
    this._saveRefresh(xbox.save());
    return this.profile();
  }

  /** Csendes bejelentkezés induláskor a mentett tokennel. */
  async restore() {
    const token = this._loadRefresh();
    if (!token) return null;
    try {
      const xbox = await this.auth.refresh(token);
      this.mc = await xbox.getMinecraft();
      this._saveRefresh(xbox.save());
      return this.profile();
    } catch (e) {
      console.warn('Mentett fiók frissítése sikertelen:', e && e.message);
      return null;
    }
  }

  /** Érvényes MCLC authorization objektum (szükség esetén frissít). */
  async mclc() {
    if (!this.mc) throw new Error('Előbb jelentkezz be Microsoft fiókkal.');
    if (!this.mc.validate()) await this.mc.refresh(true);
    return this.mc.mclc();
  }

  logout() {
    this.mc = null;
    fs.rmSync(this.file, { force: true });
  }
}

module.exports = { AuthManager };
