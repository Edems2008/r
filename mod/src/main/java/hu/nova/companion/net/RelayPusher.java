package hu.nova.companion.net;

import hu.nova.companion.map.MapStore;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Internetes kapcsolat: másodpercenként feltolja az állapotot és a megváltozott chunkokat
 * a relay szerverre, ahonnan a telefon bárhonnan (mobilnetről is) le tudja kérni.
 */
public final class RelayPusher {

    private final MapStore store;
    private final Supplier<String> relayUrl;
    private final Supplier<String> code;
    private final Supplier<String> key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ScheduledExecutorService exec;
    private long pushedRev = 0;
    private int pushedEpoch = Integer.MIN_VALUE;
    private volatile String lastError = null;

    public RelayPusher(MapStore store, Supplier<String> relayUrl, Supplier<String> code, Supplier<String> key) {
        this.store = store;
        this.relayUrl = relayUrl;
        this.code = code;
        this.key = key;
    }

    public synchronized void start() {
        stop();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NovaCompanion-Relay");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(this::pushSafe, 2, 1, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    public String lastError() {
        return lastError;
    }

    private void pushSafe() {
        try {
            push();
            lastError = null;
        } catch (Exception e) {
            lastError = e.toString();
        }
    }

    private void push() throws Exception {
        String base = relayUrl.get();
        if (base == null || base.isBlank()) return;
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        int epoch = store.epoch();
        if (epoch != pushedEpoch) pushedRev = 0;
        MapStore.TileBatch batch = store.tilesSince(pushedRev, 150);

        String body = "{\"key\":\"" + escape(key.get()) + "\""
                + ",\"from\":" + pushedRev
                + ",\"state\":" + store.stateJson()
                + ",\"map\":" + MapStore.batchToJson(batch) + "}";

        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/push?code="
                        + URLEncoder.encode(code.get(), StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("relay HTTP " + resp.statusCode() + ": " + resp.body());
        if (resp.body().contains("\"resync\":true")) {
            // a relay újraindult és elvesztette a térképet -> mindent újra küldünk
            pushedRev = 0;
            pushedEpoch = epoch;
            return;
        }
        pushedEpoch = batch.epoch;
        pushedRev = batch.rev;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
