package hu.nova.companion.map;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Szálbiztos tároló a térkép-chunkokhoz és a játékos állapotához.
 * A kliens szál írja (MapScanner), a HTTP / relay szálak olvassák.
 */
public final class MapStore {

    public static final class Tile {
        public final int x;
        public final int z;
        public final long rev;
        /** 16x16 pixel RGB, base64 (768 bájt). */
        public final String data;

        Tile(int x, int z, long rev, String data) {
            this.x = x;
            this.z = z;
            this.rev = rev;
            this.data = data;
        }
    }

    public static final class TileBatch {
        public final int epoch;
        public final long rev;
        public final boolean more;
        public final List<Tile> tiles;

        TileBatch(int epoch, long rev, boolean more, List<Tile> tiles) {
            this.epoch = epoch;
            this.rev = rev;
            this.more = more;
            this.tiles = tiles;
        }
    }

    private final ConcurrentHashMap<Long, Tile> tiles = new ConcurrentHashMap<>();
    private final AtomicLong rev = new AtomicLong();
    private volatile int epoch = (int) (System.currentTimeMillis() & 0x7fffffff);
    private volatile String dimension = "";
    private volatile String stateJson = "{\"online\":false}";

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** Új világ / dimenzió: mindent törlünk, a kliensek az epoch változásból tudják, hogy újra kell rajzolni. */
    public synchronized void reset(String newDimension) {
        tiles.clear();
        dimension = newDimension == null ? "" : newDimension;
        epoch = epoch + 1;
    }

    public String dimension() {
        return dimension;
    }

    public int epoch() {
        return epoch;
    }

    public long currentRev() {
        return rev.get();
    }

    /** @return true ha a chunk tartalma megváltozott */
    public boolean putTile(int x, int z, byte[] rgb) {
        String data = Base64.getEncoder().encodeToString(rgb);
        long k = key(x, z);
        Tile old = tiles.get(k);
        if (old != null && old.data.equals(data)) return false;
        tiles.put(k, new Tile(x, z, rev.incrementAndGet(), data));
        return true;
    }

    /** Messze került chunkok kidobása, hogy a memória ne nőjön a végtelenségig. */
    public void prune(int centerX, int centerZ, int keepRadius) {
        tiles.values().removeIf(t -> Math.abs(t.x - centerX) > keepRadius || Math.abs(t.z - centerZ) > keepRadius);
    }

    public TileBatch tilesSince(long since, int limit) {
        int ep = epoch;
        List<Tile> list = new ArrayList<>();
        for (Tile t : tiles.values()) if (t.rev > since) list.add(t);
        list.sort(Comparator.comparingLong(t -> t.rev));
        boolean more = list.size() > limit;
        if (more) list = new ArrayList<>(list.subList(0, limit));
        long maxRev = list.isEmpty() ? Math.max(since, 0) : list.get(list.size() - 1).rev;
        if (!more && list.isEmpty()) maxRev = Math.min(Math.max(since, 0), rev.get());
        return new TileBatch(ep, maxRev, more, list);
    }

    public void setStateJson(String json) {
        stateJson = json;
    }

    public String stateJson() {
        return stateJson;
    }

    /** {"epoch":..,"rev":..,"more":..,"tiles":[{"x":..,"z":..,"d":".."}]} */
    public static String batchToJson(TileBatch b) {
        StringBuilder sb = new StringBuilder(64 + b.tiles.size() * 1060);
        sb.append("{\"epoch\":").append(b.epoch)
          .append(",\"rev\":").append(b.rev)
          .append(",\"more\":").append(b.more)
          .append(",\"tiles\":[");
        for (int i = 0; i < b.tiles.size(); i++) {
            Tile t = b.tiles.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"x\":").append(t.x).append(",\"z\":").append(t.z)
              .append(",\"d\":\"").append(t.data).append("\"}");
        }
        return sb.append("]}").toString();
    }
}
