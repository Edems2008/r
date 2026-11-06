package hu.nova.companion.net;

import hu.nova.companion.map.MapStore;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.Deflater;

/**
 * „Bárhonnan” kapcsolat: a játék egy ingyenes, nyilvános MQTT szerveren keresztül küldi az állapotot és a térképet
 * a telefonnak, így a két eszköznek NEM kell ugyanazon a hálózaton lennie, és semmit sem kell beállítani.
 * Minden üzenet AES-256-GCM-mel titkosított (lásd {@link OnlineCrypto}).
 *
 * Csatornák (topic = novamc/&lt;a kódból származtatott azonosító&gt;):
 *   .../s  állapot (JSON, megtartott üzenet, ~2x mp-enként)
 *   .../t  térkép-chunkok (tömörített bináris csomagok)
 *   .../r  a telefon kérése: „küldd újra a teljes térképet”
 */
public final class OnlineBridge {

    private static final int TILE_BYTES = 768;
    private static final int TILES_PER_MESSAGE = 48;

    private final MapStore store;
    private final Supplier<String> code;
    private final Supplier<List<String>> brokers;
    private ScheduledExecutorService exec;
    private volatile MiniMqtt mqtt;
    private OnlineCrypto crypto;
    private String cryptoCode = "";
    private int brokerIndex = 0;
    private long nextConnectAt = 0;
    private long lastPing = 0;
    private long tick = 0;
    private long sentRev = 0;
    private int sentEpoch = Integer.MIN_VALUE;
    private volatile boolean resyncRequested;
    private volatile String status = "indul…";

    public OnlineBridge(MapStore store, Supplier<String> code, Supplier<List<String>> brokers) {
        this.store = store;
        this.code = code;
        this.brokers = brokers;
    }

    public String status() {
        return status;
    }

    public synchronized void start() {
        stop();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NovaCompanion-Online");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(this::loopSafe, 1, 500, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::clearAndClose, "NovaCompanion-Online-Exit"));
    }

    public synchronized void stop() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
        clearAndClose();
    }

    /** Kilépéskor töröljük a megtartott állapotot, hogy a telefon tudja: a játék nem fut. */
    private void clearAndClose() {
        MiniMqtt m = mqtt;
        mqtt = null;
        if (m != null && m.isOpen() && crypto != null) {
            try {
                m.publish(crypto.topic + "/s", new byte[0], true);
            } catch (Exception ignored) {
            }
            m.close();
        }
    }

    private void loopSafe() {
        try {
            loop();
        } catch (Exception e) {
            status = "hiba: " + e.getMessage();
            MiniMqtt m = mqtt;
            mqtt = null;
            if (m != null) m.close();
            nextConnectAt = System.currentTimeMillis() + 3000;
        }
    }

    private void loop() throws Exception {
        String c = OnlineCrypto.normalize(code.get());
        if (c.length() < 8) {
            status = "nincs online kód";
            return;
        }
        if (crypto == null || !c.equals(cryptoCode)) {
            crypto = OnlineCrypto.fromCode(c);
            cryptoCode = c;
            MiniMqtt old = mqtt;
            mqtt = null;
            if (old != null) old.close();
        }

        long now = System.currentTimeMillis();
        MiniMqtt m = mqtt;
        if (m == null || !m.isOpen()) {
            if (now < nextConnectAt) return;
            connectNext();
            return;
        }

        tick++;
        // állapot: megtartott (retained) üzenet, így a telefon csatlakozáskor azonnal megkapja
        String state = "{\"ts\":" + now + ",\"s\":" + store.stateJson() + "}";
        m.publish(crypto.topic + "/s", crypto.encrypt(state.getBytes(StandardCharsets.UTF_8)), true);

        if (tick % 2 == 0) sendTiles(m);
        if (now - lastPing > 30000) {
            m.ping();
            lastPing = now;
        }
    }

    private void connectNext() throws Exception {
        List<String> list = brokers.get();
        if (list == null || list.isEmpty()) {
            status = "nincs szerver megadva";
            return;
        }
        String url = list.get(brokerIndex % list.size());
        status = "csatlakozás: " + url;
        MiniMqtt m = new MiniMqtt(this::onMessage, () -> status = "kapcsolat megszakadt, újracsatlakozás…");
        try {
            m.connect(url, "novamod-" + UUID.randomUUID().toString().substring(0, 12), 8);
            m.subscribe(crypto.topic + "/r");
            mqtt = m;
            sentRev = 0; // új kapcsolat: a teljes térképet elküldjük
            lastPing = System.currentTimeMillis();
            status = "online: " + url;
            System.out.println("[NovaCompanion] Online kapcsolat: " + url);
        } catch (Exception e) {
            m.close();
            brokerIndex++; // legközelebb a következő szervert próbáljuk
            nextConnectAt = System.currentTimeMillis() + 4000;
            status = "nem elérhető: " + url;
            System.err.println("[NovaCompanion] Online szerver nem elérhető (" + url + "): " + e);
        }
    }

    private void onMessage(String topic, byte[] payload) {
        if (crypto != null && topic.equals(crypto.topic + "/r")) {
            try {
                crypto.decrypt(payload); // csak a kódot ismerő telefon kérése számít
                resyncRequested = true;
            } catch (Exception ignored) {
            }
        }
    }

    private void sendTiles(MiniMqtt m) throws Exception {
        int epoch = store.epoch();
        if (epoch != sentEpoch || resyncRequested) {
            sentRev = 0;
            sentEpoch = epoch;
            resyncRequested = false;
        }
        // legfeljebb 3 csomag fél másodpercenként, hogy a nyilvános szerver ne fojtson le
        for (int i = 0; i < 3; i++) {
            MapStore.TileBatch batch = store.tilesSince(sentRev, TILES_PER_MESSAGE);
            if (batch.tiles.isEmpty()) return;
            m.publish(crypto.topic + "/t", crypto.encrypt(encodeTiles(batch)), false);
            sentRev = batch.rev;
            if (!batch.more) return;
        }
    }

    /** 'T', epoch (int), darabszám (short), majd chunkonként: x (int), z (int), 768 bájt RGB – zlib-bel tömörítve */
    static byte[] encodeTiles(MapStore.TileBatch batch) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(8 + batch.tiles.size() * (TILE_BYTES + 8));
        DataOutputStream out = new DataOutputStream(raw);
        out.writeByte('T');
        out.writeInt(batch.epoch);
        out.writeShort(batch.tiles.size());
        Base64.Decoder dec = Base64.getDecoder();
        for (MapStore.Tile t : batch.tiles) {
            out.writeInt(t.x);
            out.writeInt(t.z);
            out.write(dec.decode(t.data));
        }
        out.flush();
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(raw.toByteArray());
        d.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) z.write(buf, 0, d.deflate(buf));
        d.end();
        return z.toByteArray();
    }
}
