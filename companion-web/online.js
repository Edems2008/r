/*
 * Nova Companion – „Bárhonnan” kapcsolat a telefon oldalán.
 * Nyilvános MQTT szerveren (WebSocket) át kapja a játék titkosított üzeneteit, így a telefonnak és a gépnek
 * nem kell ugyanazon a hálózaton lennie. A titkosítás megegyezik a mod OnlineCrypto osztályával:
 * PBKDF2-SHA256 (20 000 kör) -> topic azonosító + AES-256-GCM kulcs.
 */
(function () {
  'use strict';

  const BROKERS = [
    'wss://broker.emqx.io:8084/mqtt',
    'wss://broker.hivemq.com:8884/mqtt',
    'wss://test.mosquitto.org:8081/mqtt',
  ];
  const SALT = 'nova-companion-online-v1';
  const ITERATIONS = 20000;
  const enc = new TextEncoder();
  const dec = new TextDecoder();

  function normalize(code) {
    return String(code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  }

  async function deriveKeys(code) {
    if (!window.crypto || !crypto.subtle) throw new Error('Ez a böngésző nem támogatja a titkosítást (Web Crypto).');
    const base = await crypto.subtle.importKey('raw', enc.encode(normalize(code)), 'PBKDF2', false, ['deriveBits']);
    const bits = new Uint8Array(await crypto.subtle.deriveBits(
      { name: 'PBKDF2', salt: enc.encode(SALT), iterations: ITERATIONS, hash: 'SHA-256' }, base, 512));
    const topic = 'novamc/' + Array.from(bits.slice(0, 16), (b) => b.toString(16).padStart(2, '0')).join('');
    const key = await crypto.subtle.importKey('raw', bits.slice(32, 64), 'AES-GCM', false, ['encrypt', 'decrypt']);
    return { topic, key };
  }

  async function decrypt(key, data) {
    const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: data.slice(0, 12) }, key, data.slice(12));
    return new Uint8Array(pt);
  }

  async function encrypt(key, plain) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plain));
    const out = new Uint8Array(12 + ct.length);
    out.set(iv, 0);
    out.set(ct, 12);
    return out;
  }

  async function inflate(data) {
    const ds = new DecompressionStream('deflate');
    const stream = new Blob([data]).stream().pipeThrough(ds);
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }

  // ---------------------------------------------------------------- minimális MQTT 3.1.1 (QoS 0)

  function mqttString(s) {
    const b = enc.encode(s);
    return [b.length >> 8, b.length & 255, ...b];
  }

  function mqttPacket(header, body) {
    const len = [];
    let n = body.length;
    do { let d = n % 128; n = Math.floor(n / 128); if (n > 0) d |= 128; len.push(d); } while (n > 0);
    const out = new Uint8Array(1 + len.length + body.length);
    out[0] = header;
    out.set(len, 1);
    out.set(body, 1 + len.length);
    return out;
  }

  class MiniMqtt {
    constructor(url, onMessage, onClose) {
      this.url = url;
      this.onMessage = onMessage;
      this.onClose = onClose;
      this.buf = new Uint8Array(0);
      this.id = 1;
    }

    connect(timeoutMs = 7000) {
      return new Promise((resolve, reject) => {
        let done = false;
        const fail = (e) => { if (!done) { done = true; reject(e); } try { this.ws.close(); } catch { /* */ } };
        const timer = setTimeout(() => fail(new Error('időtúllépés')), timeoutMs);
        try {
          this.ws = new WebSocket(this.url, ['mqtt']);
        } catch (e) { clearTimeout(timer); reject(e); return; }
        this.ws.binaryType = 'arraybuffer';
        this.ws.onopen = () => {
          const clientId = 'novaphone-' + Math.random().toString(36).slice(2, 12);
          const body = [...mqttString('MQTT'), 4, 0x02, 0, 60, ...mqttString(clientId)];
          this.ws.send(mqttPacket(0x10, new Uint8Array(body)));
        };
        this.ws.onmessage = (ev) => this._receive(new Uint8Array(ev.data), (ok) => {
          clearTimeout(timer);
          if (done) return;
          done = true;
          ok ? resolve() : reject(new Error('elutasítva'));
        });
        this.ws.onerror = () => { clearTimeout(timer); fail(new Error('kapcsolódási hiba')); };
        this.ws.onclose = () => { clearTimeout(timer); if (!done) fail(new Error('lezárva')); else if (this.onClose) this.onClose(); };
      });
    }

    _receive(chunk, onConnack) {
      const b = new Uint8Array(this.buf.length + chunk.length);
      b.set(this.buf, 0);
      b.set(chunk, this.buf.length);
      let pos = 0;
      while (b.length - pos >= 2) {
        const header = b[pos];
        let mult = 1, len = 0, i = pos + 1, complete = false;
        while (i < b.length) {
          const d = b[i++];
          len += (d & 127) * mult;
          mult *= 128;
          if ((d & 128) === 0) { complete = true; break; }
        }
        if (!complete || b.length - i < len) break;
        const type = header >> 4;
        if (type === 2) onConnack(len >= 2 && b[i + 1] === 0);
        else if (type === 3) {
          const qos = (header >> 1) & 3;
          const tlen = (b[i] << 8) | b[i + 1];
          const topic = dec.decode(b.slice(i + 2, i + 2 + tlen));
          const p = i + 2 + tlen + (qos > 0 ? 2 : 0);
          this.onMessage(topic, b.slice(p, i + len));
        }
        pos = i + len;
      }
      this.buf = b.slice(pos);
    }

    subscribe(topic) {
      this.id = (this.id % 65535) + 1;
      this.ws.send(mqttPacket(0x82, new Uint8Array([this.id >> 8, this.id & 255, ...mqttString(topic), 0])));
    }

    publish(topic, payload) {
      const t = mqttString(topic);
      const body = new Uint8Array(t.length + payload.length);
      body.set(t, 0);
      body.set(payload, t.length);
      this.ws.send(mqttPacket(0x30, body));
    }

    ping() { try { this.ws.send(new Uint8Array([0xC0, 0])); } catch { /* */ } }

    close() { try { this.ws.send(new Uint8Array([0xE0, 0])); this.ws.close(); } catch { /* */ } }
  }

  // ---------------------------------------------------------------- kapcsolat-kezelés

  /**
   * @param {object} o
   * @param {string} o.code      online kód
   * @param {Function} o.onState (stateObj) – a játékos állapota
   * @param {Function} o.onTiles (epoch, [{x, z, rgb: Uint8Array}])
   * @param {Function} o.onStatus (text, ok)
   * @param {string[]} [o.brokers]
   * @returns {Promise<{stop: Function}>}
   */
  async function start(o) {
    const { topic, key } = await deriveKeys(o.code);
    const brokers = o.brokers && o.brokers.length ? o.brokers : (window.NOVA_TEST_BROKERS || BROKERS);
    let stopped = false;
    let client = null;
    let index = 0;
    let gotAnything = false;
    let lastStateAt = 0;
    let timers = [];

    const clearTimers = () => { timers.forEach(clearTimeout); timers.forEach(clearInterval); timers = []; };

    async function handle(t, payload) {
      if (t === topic + '/s') {
        gotAnything = true;
        if (payload.length === 0) { o.onState({ online: false }); return; } // a játék kilépett
        try {
          const msg = JSON.parse(dec.decode(await decrypt(key, payload)));
          const st = msg.s || { online: false };
          // régi (megtartott) állapot -> a játék valószínűleg nem fut
          if (Date.now() - msg.ts > 45000) st.online = false;
          lastStateAt = Date.now();
          o.onState(st);
        } catch { o.onStatus('Hibás kód? Nem sikerült visszafejteni az adatokat.', false); }
      } else if (t === topic + '/t') {
        gotAnything = true;
        try {
          const raw = await inflate(await decrypt(key, payload));
          const dv = new DataView(raw.buffer, raw.byteOffset, raw.byteLength);
          if (dv.getUint8(0) !== 84) return; // 'T'
          const epoch = dv.getInt32(1);
          const count = dv.getUint16(5);
          const tiles = [];
          let p = 7;
          for (let i = 0; i < count; i++) {
            tiles.push({ x: dv.getInt32(p), z: dv.getInt32(p + 4), rgb: raw.subarray(p + 8, p + 8 + 768) });
            p += 776;
          }
          o.onTiles(epoch, tiles);
        } catch { /* sérült csomag */ }
      }
    }

    async function requestMap() {
      if (!client) return;
      try { client.publish(topic + '/r', await encrypt(key, enc.encode('{"since":0}'))); } catch { /* */ }
    }

    async function connectLoop() {
      while (!stopped) {
        const url = brokers[index % brokers.length];
        o.onStatus('Csatlakozás: ' + url.replace(/^wss:\/\//, '').split('/')[0] + '…', false);
        const c = new MiniMqtt(url, (t, p) => handle(t, p), () => {
          if (stopped) return;
          clearTimers();
          client = null;
          o.onStatus('A kapcsolat megszakadt, újracsatlakozás…', false);
          setTimeout(connectLoop, 2000);
        });
        try {
          await c.connect();
          if (stopped) { c.close(); return; }
          client = c;
          gotAnything = false;
          c.subscribe(topic + '/s');
          c.subscribe(topic + '/t');
          await requestMap();
          o.onStatus('Kapcsolódva (interneten át)', true);
          timers.push(setInterval(() => c.ping(), 25000));
          // ha ezen a szerveren semmi nem jön, lehet, hogy a játék egy másikat használ -> továbblépünk
          timers.push(setTimeout(() => {
            if (!gotAnything && !stopped) {
              o.onStatus('Ezen a szerveren nincs adat, a következőt próbálom…', false);
              index++;
              clearTimers();
              client = null;
              c.onClose = null;
              c.close();
              setTimeout(connectLoop, 500);
            }
          }, 9000));
          // ha a térkép nem jött meg (pl. a játék később indult), kérjük újra
          timers.push(setInterval(() => { if (Date.now() - lastStateAt > 15000) requestMap(); }, 15000));
          return;
        } catch (e) {
          index++;
          o.onStatus('Nem elérhető szerver, a következőt próbálom…', false);
          await new Promise((r) => setTimeout(r, 1200));
        }
      }
    }

    connectLoop();
    return {
      requestMap,
      stop() {
        stopped = true;
        clearTimers();
        if (client) { client.onClose = null; client.close(); }
        client = null;
      },
    };
  }

  window.NovaOnline = { start, normalize, BROKERS };
})();
