package hu.nova.companion.net;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Minimális MQTT 3.1.1 kliens WebSocketen át (QoS 0), a JDK beépített WebSocket kliensével.
 * Ennyi elég ahhoz, hogy a játék és a telefon egy nyilvános MQTT szerveren keresztül üzenjen egymásnak.
 */
public final class MiniMqtt implements WebSocket.Listener {

    private final BiConsumer<String, byte[]> onMessage;
    private final Runnable onClosed;
    private final ByteArrayOutputStream inbox = new ByteArrayOutputStream();
    private final CompletableFuture<Boolean> connack = new CompletableFuture<>();
    private volatile WebSocket ws;
    private volatile boolean closed;
    private int packetId = 1;

    public MiniMqtt(BiConsumer<String, byte[]> onMessage, Runnable onClosed) {
        this.onMessage = onMessage;
        this.onClosed = onClosed;
    }

    public void connect(String url, String clientId, int timeoutSec) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSec)).build();
        ws = http.newWebSocketBuilder()
                .subprotocols("mqtt")
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .buildAsync(URI.create(url), this)
                .get(timeoutSec, TimeUnit.SECONDS);

        ByteArrayOutputStream vh = new ByteArrayOutputStream();
        writeString(vh, "MQTT");
        vh.write(4);          // protokoll szint 3.1.1
        vh.write(0x02);       // clean session
        vh.write(0);
        vh.write(60);         // keepalive 60 mp
        writeString(vh, clientId);
        send(0x10, vh.toByteArray());
        if (!connack.get(timeoutSec, TimeUnit.SECONDS)) throw new IllegalStateException("MQTT szerver elutasította a csatlakozást");
    }

    public boolean isOpen() {
        return ws != null && !closed;
    }

    public void publish(String topic, byte[] payload, boolean retain) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream(payload.length + topic.length() + 4);
        writeString(body, topic);
        body.write(payload);
        send(0x30 | (retain ? 1 : 0), body.toByteArray());
    }

    public void subscribe(String topic) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int id = nextId();
        body.write(id >> 8);
        body.write(id & 0xff);
        writeString(body, topic);
        body.write(0); // QoS 0
        send(0x82, body.toByteArray());
    }

    public void ping() throws Exception {
        send(0xC0, new byte[0]);
    }

    public void close() {
        closed = true;
        WebSocket w = ws;
        if (w != null) {
            try {
                send(0xE0, new byte[0]); // DISCONNECT
            } catch (Exception ignored) {
            }
            w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    private synchronized int nextId() {
        packetId = packetId % 65535 + 1;
        return packetId;
    }

    /** A WebSocket egyszerre csak egy küldést enged, ezért szinkronizálva, megvárva küldünk. */
    private synchronized void send(int header, byte[] body) throws Exception {
        if (ws == null || closed) throw new IllegalStateException("nincs kapcsolat");
        ByteArrayOutputStream pkt = new ByteArrayOutputStream(body.length + 5);
        pkt.write(header);
        int len = body.length;
        do {
            int b = len % 128;
            len /= 128;
            if (len > 0) b |= 0x80;
            pkt.write(b);
        } while (len > 0);
        pkt.write(body);
        ws.sendBinary(ByteBuffer.wrap(pkt.toByteArray()), true).get(15, TimeUnit.SECONDS);
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b.length >> 8);
        out.write(b.length & 0xff);
        out.write(b, 0, b.length);
    }

    // ------------------------------------------------------------------ fogadás

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        byte[] chunk = new byte[data.remaining()];
        data.get(chunk);
        synchronized (inbox) {
            inbox.write(chunk, 0, chunk.length);
            parsePackets();
        }
        webSocket.request(1);
        return null;
    }

    private void parsePackets() {
        byte[] buf = inbox.toByteArray();
        int pos = 0;
        while (true) {
            if (buf.length - pos < 2) break;
            int header = buf[pos] & 0xff;
            int mult = 1, len = 0, i = pos + 1, b;
            boolean complete = false;
            do {
                if (i >= buf.length) break;
                b = buf[i++] & 0xff;
                len += (b & 0x7f) * mult;
                mult *= 128;
                if ((b & 0x80) == 0) {
                    complete = true;
                    break;
                }
            } while (mult <= 128 * 128 * 128);
            if (!complete || buf.length - i < len) break;
            handlePacket(header, buf, i, len);
            pos = i + len;
        }
        inbox.reset();
        if (pos < buf.length) inbox.write(buf, pos, buf.length - pos);
    }

    private void handlePacket(int header, byte[] buf, int off, int len) {
        int type = header >> 4;
        if (type == 2) { // CONNACK
            connack.complete(len >= 2 && buf[off + 1] == 0);
        } else if (type == 3) { // PUBLISH
            int qos = (header >> 1) & 3;
            int tlen = ((buf[off] & 0xff) << 8) | (buf[off + 1] & 0xff);
            String topic = new String(buf, off + 2, tlen, StandardCharsets.UTF_8);
            int p = off + 2 + tlen + (qos > 0 ? 2 : 0);
            byte[] payload = new byte[off + len - p];
            System.arraycopy(buf, p, payload, 0, payload.length);
            try {
                onMessage.accept(topic, payload);
            } catch (Exception e) {
                System.err.println("[NovaCompanion] MQTT üzenet feldolgozási hiba: " + e);
            }
        }
        // SUBACK (9), PINGRESP (13): nincs teendő
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        markClosed();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connack.complete(false);
        markClosed();
    }

    private void markClosed() {
        boolean was = closed;
        closed = true;
        if (!was && onClosed != null) onClosed.run();
    }
}
