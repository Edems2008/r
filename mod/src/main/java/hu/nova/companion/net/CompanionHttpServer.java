package hu.nova.companion.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hu.nova.companion.map.MapStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Helyi HTTP szerver a telefonos apphoz.
 *  GET /api/state?code=XXXX           – játékos állapota (pozíció, irány, skin uuid, többi játékos)
 *  GET /api/tiles?code=XXXX&since=N   – megváltozott térkép-chunkok
 *  GET /                              – a beépített webes kezelőfelület (QR-kódról böngészőben is megnyitható)
 */
public final class CompanionHttpServer {

    private static final String WEB_ROOT = "/assets/novacompanion/web";

    private final MapStore store;
    private final Supplier<String> codeSupplier;
    private HttpServer server;
    private ExecutorService pool;

    public CompanionHttpServer(MapStore store, Supplier<String> codeSupplier) {
        this.store = store;
        this.codeSupplier = codeSupplier;
    }

    public synchronized void start(int port) throws IOException {
        stop();
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 16);
        pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "NovaCompanion-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(pool);
        server.createContext("/api/state", ex -> {
            if (!preflightAndAuth(ex)) return;
            send(ex, 200, "application/json", store.stateJson().getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/api/tiles", ex -> {
            if (!preflightAndAuth(ex)) return;
            Map<String, String> q = query(ex);
            long since = parseLong(q.get("since"), 0);
            int epoch = (int) parseLong(q.get("epoch"), -1);
            if (epoch != store.epoch()) since = 0; // új világ -> mindent újraküldünk
            String json = MapStore.batchToJson(store.tilesSince(since, 400));
            send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/api/ping", ex -> {
            if (!preflightAndAuth(ex)) return;
            send(ex, 200, "application/json", "{\"ok\":true,\"app\":\"novacompanion\"}".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/", this::serveStatic);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    private boolean preflightAndAuth(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return false;
        }
        String code = query(ex).get("code");
        if (code == null || !code.trim().equalsIgnoreCase(codeSupplier.get())) {
            send(ex, 401, "application/json", "{\"error\":\"bad_code\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        return true;
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        cors(ex);
        String path = ex.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
        if (path.contains("..")) {
            send(ex, 400, "text/plain", "bad path".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try (InputStream in = CompanionHttpServer.class.getResourceAsStream(WEB_ROOT + path)) {
            if (in == null) {
                send(ex, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(ex, 200, contentType(path), in.readAllBytes());
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            out.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    static long parseLong(String s, long def) {
        if (s == null) return def;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
