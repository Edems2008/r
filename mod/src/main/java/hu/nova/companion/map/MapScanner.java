package hu.nova.companion.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * A kliens által betöltött chunkokból felülnézeti színtérképet készít
 * (ugyanazokkal a színekkel, mint a vanilla térkép), és frissíti a játékos állapotát.
 * Minden tickben csak néhány chunkot dolgoz fel, hogy ne csökkentse az FPS-t.
 */
public final class MapScanner {

    private static final int CHUNKS_PER_TICK = 6;

    private final MapStore store;
    private final IntSupplier radius;
    private List<int[]> offsets = new ArrayList<>();
    private int offsetRadius = -1;
    private int cursor = 0;
    private int tickCounter = 0;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public MapScanner(MapStore store, IntSupplier radius) {
        this.store = store;
        this.radius = radius;
    }

    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            if (!store.dimension().isEmpty()) store.reset("");
            store.setStateJson("{\"online\":false}");
            return;
        }

        String dim = level.dimension().location().toString();
        if (!dim.equals(store.dimension())) store.reset(dim);

        tickCounter++;
        if (tickCounter % 2 == 0) store.setStateJson(buildState(mc, player, level, dim));

        int r = radius.getAsInt();
        if (r != offsetRadius) buildOffsets(r);

        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;
        boolean cave = level.dimensionType().hasCeiling();
        int caveStartY = (int) Math.floor(player.getY()) + 1;

        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            if (cursor >= offsets.size()) cursor = 0;
            int[] off = offsets.get(cursor++);
            int cx = pcx + off[0];
            int cz = pcz + off[1];
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null || chunk.isEmpty()) continue;
            store.putTile(cx, cz, scanChunk(level, chunk, cx, cz, cave, caveStartY));
        }

        if (tickCounter % 200 == 0) store.prune(pcx, pcz, r * 3);
    }

    /** Spirál-sorrend: a játékoshoz közeli chunkok kerülnek előre. */
    private void buildOffsets(int r) {
        List<int[]> list = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                if (dx * dx + dz * dz <= r * r + r) list.add(new int[]{dx, dz});
        list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        // a középső 3x3 chunkot sűrűbben frissítjük
        List<int[]> result = new ArrayList<>();
        int near = 0;
        for (int[] o : list) {
            result.add(o);
            if (++near % 20 == 0) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) result.add(new int[]{dx, dz});
            }
        }
        offsets = result;
        offsetRadius = r;
        cursor = 0;
    }

    private byte[] scanChunk(ClientLevel level, LevelChunk chunk, int cx, int cz, boolean cave, int caveStartY) {
        byte[] rgb = new byte[16 * 16 * 3];
        int[] heights = new int[16 * 16];
        int[] colors = new int[16 * 16];
        int minY = level.getMinBuildHeight();
        int baseX = cx << 4;
        int baseZ = cz << 4;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int y;
                if (cave) {
                    y = findCaveFloor(chunk, baseX + lx, baseZ + lz, caveStartY, minY);
                } else {
                    y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                }
                int color = 0;
                if (y >= minY) {
                    pos.set(baseX + lx, y, baseZ + lz);
                    BlockState state = chunk.getBlockState(pos);
                    MapColor mc = state.getMapColor(level, pos);
                    color = mc == null ? 0 : mc.col;
                }
                heights[lz * 16 + lx] = y;
                colors[lz * 16 + lx] = color;
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int idx = lz * 16 + lx;
                int h = heights[idx];
                int hn;
                if (lz > 0) hn = heights[idx - 16];
                else hn = cave ? h : level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + lx, baseZ - 1) - 1;
                float shade = h > hn ? 1.12f : (h < hn ? 0.82f : 1.0f);
                int c = colors[idx];
                rgb[idx * 3] = (byte) clamp((int) (((c >> 16) & 0xff) * shade));
                rgb[idx * 3 + 1] = (byte) clamp((int) (((c >> 8) & 0xff) * shade));
                rgb[idx * 3 + 2] = (byte) clamp((int) ((c & 0xff) * shade));
            }
        }
        return rgb;
    }

    /** Netherben / plafonos dimenzióban a játékos szintjéről lefelé keressük az első szilárd blokkot. */
    private int findCaveFloor(LevelChunk chunk, int x, int z, int startY, int minY) {
        boolean seenAir = false;
        for (int y = startY; y >= minY; y--) {
            pos.set(x, y, z);
            BlockState s = chunk.getBlockState(pos);
            if (s.isAir()) {
                seenAir = true;
            } else if (seenAir) {
                return y;
            }
        }
        return minY - 1;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    private String buildState(Minecraft mc, LocalPlayer p, ClientLevel level, String dim) {
        JsonObject o = new JsonObject();
        o.addProperty("online", true);
        o.addProperty("name", p.getGameProfile().getName());
        o.addProperty("uuid", p.getUUID().toString());
        o.addProperty("x", round2(p.getX()));
        o.addProperty("y", round2(p.getY()));
        o.addProperty("z", round2(p.getZ()));
        o.addProperty("yaw", round2(p.getYRot()));
        o.addProperty("pitch", round2(p.getXRot()));
        o.addProperty("dim", dim);
        o.addProperty("health", round2(p.getHealth()));
        o.addProperty("maxHealth", round2(p.getMaxHealth()));
        o.addProperty("food", p.getFoodData().getFoodLevel());
        o.addProperty("fps", mc.getFps());
        o.addProperty("time", level.getDayTime() % 24000L);
        o.addProperty("epoch", store.epoch());
        level.getBiome(p.blockPosition()).unwrapKey()
                .ifPresent(k -> o.addProperty("biome", k.location().toString()));
        ServerData server = mc.getCurrentServer();
        o.addProperty("server", server != null ? server.ip : "singleplayer");

        JsonArray players = new JsonArray();
        for (AbstractClientPlayer other : level.players()) {
            if (other == p) continue;
            JsonObject po = new JsonObject();
            po.addProperty("name", other.getGameProfile().getName());
            po.addProperty("uuid", other.getUUID().toString());
            po.addProperty("x", round2(other.getX()));
            po.addProperty("y", round2(other.getY()));
            po.addProperty("z", round2(other.getZ()));
            po.addProperty("yaw", round2(other.getYRot()));
            players.add(po);
        }
        o.add("players", players);
        return o.toString();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
