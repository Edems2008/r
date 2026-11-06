package hu.nova.companion.fx;

import com.mojang.blaze3d.platform.NativeImage;
import hu.nova.companion.NovaCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Minden effekt- és ikon-textúrát futásidőben, kódból generálunk (nincs szükség képfájlokra).
 * A pixelek 4x4-es túlmintavételezéssel (élsimítás) készülnek, a formák egyszerű
 * 3D-s modellek (gömb, magasságmező) alapján kapnak fényt és árnyékot, így élethűbbek.
 */
public final class FxTextures {

    private static ResourceLocation id(String name) {
        return new ResourceLocation(NovaCompanion.MODID, "dynamic/" + name);
    }

    public static final ResourceLocation DROP = id("drop");
    public static final ResourceLocation VIGNETTE = id("vignette");
    public static final ResourceLocation GLOW = id("glow");
    public static final ResourceLocation SUN = id("w_sun");
    public static final ResourceLocation MOON = id("w_moon");
    public static final ResourceLocation CLOUD = id("w_cloud");
    public static final ResourceLocation RAIN = id("w_rain");
    public static final ResourceLocation SNOW = id("w_snow");
    public static final ResourceLocation STORM = id("w_storm");
    public static final ResourceLocation GOGGLES = id("goggles");
    public static final ResourceLocation HEART = id("i_heart");
    public static final ResourceLocation FOOD = id("i_food");
    public static final ResourceLocation ARMOR = id("i_armor");
    public static final ResourceLocation BUBBLE = id("i_bubble");
    public static final ResourceLocation WATER = id("i_water");

    public static final int DROP_W = 64;
    public static final int DROP_H = 80;
    public static final int VIGNETTE_SIZE = 256;
    public static final int GLOW_SIZE = 64;
    public static final int ICON = 64;
    public static final int GOGGLES_W = 128;
    public static final int GOGGLES_H = 64;
    public static final int SMALL = 32;

    private static boolean ready;

    private FxTextures() {}

    public static void ensure(Minecraft mc) {
        if (ready) return;
        ready = true;
        register(mc, DROP, DROP_W, DROP_H, FxTextures::drop);
        register(mc, VIGNETTE, VIGNETTE_SIZE, VIGNETTE_SIZE, FxTextures::vignette);
        register(mc, GLOW, GLOW_SIZE, GLOW_SIZE, FxTextures::glow);
        register(mc, SUN, ICON, ICON, FxTextures::sun);
        register(mc, MOON, ICON, ICON, FxTextures::moon);
        register(mc, CLOUD, ICON, ICON, (x, y) -> cloud(x, y, 0f, 0));
        register(mc, RAIN, ICON, ICON, (x, y) -> weather(x, y, 0));
        register(mc, SNOW, ICON, ICON, (x, y) -> weather(x, y, 1));
        register(mc, STORM, ICON, ICON, (x, y) -> weather(x, y, 2));
        register(mc, GOGGLES, GOGGLES_W, GOGGLES_H, FxTextures::goggles);
        register(mc, HEART, SMALL, SMALL, FxTextures::heart);
        register(mc, FOOD, SMALL, SMALL, FxTextures::food);
        register(mc, ARMOR, SMALL, SMALL, FxTextures::armor);
        register(mc, BUBBLE, SMALL, SMALL, FxTextures::bubble);
        register(mc, WATER, SMALL, SMALL, FxTextures::waterIcon);
    }

    /** Pixelfüggvény: lebegőpontos koordináta (pixel-egységben) -> ARGB. */
    interface PixelFn {
        int argb(float x, float y);
    }

    private static void register(Minecraft mc, ResourceLocation loc, int w, int h, PixelFn fn) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = sample(fn, x, y);
                int a = (c >>> 24) & 0xff, r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r); // NativeImage: ABGR
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        tex.setFilter(true, false);
        mc.getTextureManager().register(loc, tex);
    }

    /** 4x4 túlmintavételezés, előszorzott alfával átlagolva (élsimítás). */
    static int sample(PixelFn fn, int px, int py) {
        float a = 0, r = 0, g = 0, b = 0;
        for (int sy = 0; sy < 4; sy++) {
            for (int sx = 0; sx < 4; sx++) {
                int c = fn.argb(px + (sx + 0.5f) / 4f, py + (sy + 0.5f) / 4f);
                float ca = ((c >>> 24) & 0xff) / 255f;
                a += ca;
                r += ((c >> 16) & 0xff) / 255f * ca;
                g += ((c >> 8) & 0xff) / 255f * ca;
                b += (c & 0xff) / 255f * ca;
            }
        }
        if (a <= 0) return 0;
        return argb(a / 16f, r / a, g / a, b / a);
    }

    // ================================================================== segédfüggvények

    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static float smooth(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float dist(float x, float y, float cx, float cy) {
        float dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static int argb(float a, float r, float g, float b) {
        return (c255(a) << 24) | (c255(r) << 16) | (c255(g) << 8) | c255(b);
    }

    private static int c255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    /** src rá a dst-re (normál alfa-kompozit) */
    static int over(int dst, int src) {
        float sa = ((src >>> 24) & 0xff) / 255f;
        if (sa <= 0) return dst;
        float da = ((dst >>> 24) & 0xff) / 255f;
        float oa = sa + da * (1 - sa);
        float r = (((src >> 16) & 0xff) * sa + ((dst >> 16) & 0xff) * da * (1 - sa)) / oa / 255f;
        float g = (((src >> 8) & 0xff) * sa + ((dst >> 8) & 0xff) * da * (1 - sa)) / oa / 255f;
        float b = ((src & 0xff) * sa + (dst & 0xff) * da * (1 - sa)) / oa / 255f;
        return argb(oa, r, g, b);
    }

    /** Gömb-felület megvilágítása: (nx, ny) a kör középpontjától mért normalizált pozíció. */
    static float sphereLight(float nx, float ny, float lx, float ly, float lz) {
        float d2 = nx * nx + ny * ny;
        if (d2 >= 1f) return 0f;
        float nz = (float) Math.sqrt(1f - d2);
        float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        return Math.max(0f, (nx * lx + ny * ly + nz * lz) / len);
    }

    /** Pont és szakasz távolsága. */
    static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float t = clamp01(((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy));
        return dist(px, py, ax + vx * t, ay + vy * t);
    }

    static float hash(float x, float y) {
        double s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    /** Egyszerű értékzaj (0..1) a természetes textúrákhoz. */
    static float noise(float x, float y) {
        float ix = (float) Math.floor(x), iy = (float) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return mix(mix(a, b, fx), mix(c, d, fx), fy);
    }

    // ================================================================== képernyő-effektek

    /**
     * Vízcsepp a „lencsén”: szinte átlátszó test, a fénytörés miatt sötétebb felső perem,
     * világos fókuszált fény (kausztika) alul, éles csúcsfény bal felül és halvány árnyék.
     */
    static int drop(float x, float y) {
        float cx = 32f, cy = 48f;
        float dx = x - cx, dy = y - cy;
        // csepp-forma: alul kör, felfelé elkeskenyedő
        float rx = dy < 0 ? 22f * (float) Math.pow(clamp01(1f + dy / 46f), 0.75) : 22f;
        float ry = dy < 0 ? 40f : 24f;
        int out = 0;
        // árnyék (jobbra-lent eltolva)
        float sdx = x - cx - 2.5f, sdy = y - cy - 3f;
        float srx = sdy < 0 ? 22f * (float) Math.pow(clamp01(1f + sdy / 46f), 0.75) : 22f;
        float sry = sdy < 0 ? 40f : 24f;
        if (srx > 0.5f) {
            float sd = (float) Math.sqrt(sdx * sdx / (srx * srx) + sdy * sdy / (sry * sry));
            float sa = smooth(1.12f, 0.9f, sd) * 0.22f;
            if (sa > 0) out = argb(sa, 0.02f, 0.05f, 0.1f);
        }
        if (rx <= 0.5f) return out;
        float d = (float) Math.sqrt(dx * dx / (rx * rx) + dy * dy / (ry * ry));
        if (d >= 1.02f) return out;
        float edge = smooth(1.02f, 0.94f, d);
        float nx = dx / rx, ny = dy / ry;
        // fénytörés: a felső-bal perem sötét, a test áttetsző
        float rim = smooth(0.55f, 1f, d);
        float topDark = clamp01(0.5f - ny * 0.6f - nx * 0.25f);
        float alpha = (0.10f + 0.55f * rim * (0.45f + 0.55f * topDark)) * edge;
        float r = mix(0.80f, 0.20f, rim * topDark), g = mix(0.88f, 0.28f, rim * topDark), b = mix(0.96f, 0.38f, rim * topDark);
        // kausztika: világos sarló alul belül
        float caustic = smooth(0.95f, 0.65f, d) * smooth(0.25f, 0.75f, ny) * smooth(0.3f, 0.65f, d);
        r = mix(r, 1f, caustic * 0.9f);
        g = mix(g, 1f, caustic * 0.9f);
        b = mix(b, 1f, caustic * 0.85f);
        alpha = Math.max(alpha, caustic * 0.8f * edge);
        int body = argb(alpha, r, g, b);
        out = over(out, body);
        // csúcsfény (fő + kicsi másodlagos)
        float hl = smooth(1f, 0.55f, (float) Math.sqrt(sq((x - 23.5f) / 6f) + sq((y - 38f) / 8.5f)));
        float hl2 = smooth(1f, 0.4f, dist(x, y, 41f, 60f) / 3f);
        float spec = Math.max(hl, hl2 * 0.7f) * edge;
        if (spec > 0) out = over(out, argb(spec * 0.95f, 1f, 1f, 1f));
        return out;
    }

    static float sq(float v) {
        return v * v;
    }

    static int vignette(float x, float y) {
        float nx = x / VIGNETTE_SIZE * 2f - 1f;
        float ny = y / VIGNETTE_SIZE * 2f - 1f;
        float d = (float) Math.sqrt(nx * nx * 0.92f + ny * ny * 1.08f) / 1.4142f;
        float a = smooth(0.30f, 1.05f, d);
        return argb((float) Math.pow(a, 1.6), 1f, 1f, 1f);
    }

    static int glow(float x, float y) {
        float c = GLOW_SIZE / 2f;
        float d = dist(x, y, c, c) / c;
        float a = (float) Math.pow(clamp01(1f - d), 2.2);
        return argb(a, 1f, 1f, 1f);
    }

    // ================================================================== időjárás (64x64)

    static int sun(float x, float y) {
        float d = dist(x, y, 32, 32);
        int out = 0;
        // korona / derengés
        float halo = (float) Math.pow(clamp01(1f - d / 32f), 2.0) * 0.55f;
        if (halo > 0) out = argb(halo, 1f, 0.78f, 0.25f);
        // elkeskenyedő, lágy sugarak (12 db, váltakozó hossz)
        double ang = Math.atan2(y - 32, x - 32);
        double seg = (ang + Math.PI) / (2 * Math.PI) * 12.0;
        int idx = (int) Math.floor(seg);
        float rel = (float) Math.abs(seg - idx - 0.5) * 2f; // 0 = sugár közepe
        float len = (idx % 2 == 0) ? 30f : 25f;
        float width = mix(0.55f, 0.08f, clamp01((d - 17f) / (len - 17f)));
        float ray = smooth(width + 0.12f, width, rel) * smooth(len, len - 3f, d) * smooth(15f, 18f, d);
        if (ray > 0) out = over(out, argb(ray * 0.95f, 1f, 0.80f, 0.22f));
        // napkorong: középen fehéres-sárga, a szélén sötétebb narancs (perem-sötétedés)
        if (d < 16f) {
            float t = d / 15f;
            float limb = (float) Math.sqrt(clamp01(1f - t * t));
            float a = smooth(16f, 15f, d);
            float r = 1f, g = mix(0.62f, 0.97f, limb), b = mix(0.10f, 0.62f, limb * limb);
            out = over(out, argb(a, r, g, b));
            float hl = smooth(7f, 0f, dist(x, y, 27f, 26f)) * 0.55f;
            out = over(out, argb(hl, 1f, 1f, 0.92f));
        }
        return out;
    }

    static int moon(float x, float y) {
        float cx = 32, cy = 32, R = 20;
        float d = dist(x, y, cx, cy);
        int out = 0;
        float halo = (float) Math.pow(clamp01(1f - d / 31f), 2.4) * 0.45f;
        if (halo > 0) out = argb(halo, 0.75f, 0.82f, 1f);
        if (d < R + 1f) {
            float nx = (x - cx) / R, ny = (y - cy) / R;
            float a = smooth(R + 1f, R - 0.2f, d);
            // holdfázis: fény jobbról-fentről, a sötét oldal halványan látszik (földfény)
            float lit = sphereLight(nx, ny, 0.55f, -0.35f, 0.78f);
            float phase = smooth(0.02f, 0.25f, lit);
            float n = noise(x * 0.18f, y * 0.18f) * 0.5f + noise(x * 0.45f, y * 0.45f) * 0.25f;
            // kráterek: sötét folt világos peremmel
            float crater = 0f;
            float[][] cr = {{25, 27, 4.5f}, {37, 39, 3.5f}, {31, 41, 2.5f}, {40, 26, 2.8f}, {24, 38, 2.2f}};
            for (float[] c : cr) {
                float cd = dist(x, y, c[0], c[1]) / c[2];
                crater += smooth(1f, 0.6f, cd) * 0.18f - smooth(1.25f, 1f, cd) * smooth(0.9f, 1.05f, cd) * 0.08f;
            }
            float base = 0.86f - n * 0.18f - crater;
            float bright = mix(0.30f, 1f, phase) * base;
            out = over(out, argb(a, bright * 0.98f, bright * 0.97f, bright * 0.88f + (1 - phase) * 0.06f));
        }
        return out;
    }

    /** Felhő magasságmező: több gömb-„lebeny” uniója; visszaad: magasság (0 = kívül). */
    static float cloudHeight(float x, float y, float oy) {
        float[][] lobes = {
                {20, 38, 11}, {32, 29, 14}, {45, 35, 11}, {27, 41, 10}, {39, 41, 10}, {52, 41, 7}, {12, 43, 6.5f}
        };
        float h = 0;
        for (float[] l : lobes) {
            float d2 = sq(x - l[0]) + sq(y - (l[1] + oy));
            float r2 = l[2] * l[2];
            if (d2 < r2) h = Math.max(h, (float) Math.sqrt(r2 - d2));
        }
        // lapos alj
        if (y > 46f + oy) h *= smooth(49f + oy, 46f + oy, y);
        return h;
    }

    /** tone: 0 = fehér gomolyfelhő, 1 = esőfelhő (szürkébb), 2 = viharfelhő (sötét) */
    static int cloud(float x, float y, float oy, int tone) {
        float h = cloudHeight(x, y, oy);
        if (h <= 0) return 0;
        float e = 0.6f;
        float gx = cloudHeight(x + e, y, oy) - cloudHeight(x - e, y, oy);
        float gy = cloudHeight(x, y + e, oy) - cloudHeight(x, y - e, oy);
        // normál a magasságmezőből, fény bal-fentről
        float nx = -gx, ny = -gy, nz = 2.4f * e;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float light = Math.max(0f, (nx * -0.45f + ny * -0.75f + nz * 0.6f) / len);
        float ao = smooth(0f, 6f, h); // sűrűbb a közepe
        float a = smooth(0f, 1.2f, h);
        float base = tone == 0 ? 0.97f : tone == 1 ? 0.78f : 0.42f;
        float dark = tone == 0 ? 0.68f : tone == 1 ? 0.50f : 0.22f;
        float v = mix(dark, base, clamp01(light * 1.15f)) * mix(0.9f, 1f, ao);
        float vertical = clamp01((y - (18 + oy)) / 34f); // alul kicsit sötétebb
        v *= 1f - vertical * 0.12f;
        float blue = tone == 2 ? 0.07f : 0.04f;
        return argb(a, v, v + 0.01f, Math.min(1f, v + blue));
    }

    /** 0 = eső, 1 = hó, 2 = vihar */
    static int weather(float x, float y, int kind) {
        int out = 0;
        if (kind == 0 || kind == 2) {
            // elmosódott, ferde esőcsíkok
            float[][] streaks = {{18, 44}, {27, 47}, {36, 44}, {45, 47}, {22, 54}, {40, 55}};
            for (float[] s : streaks) {
                float ax = s[0], ay = s[1], bx = s[0] - 5f, by = s[1] + 11f;
                float sd = segDist(x, y, ax, ay, bx, by);
                float t = clamp01((y - ay) / (by - ay));
                float a = smooth(1.6f, 0.6f, sd) * (0.35f + 0.65f * t);
                if (a > 0) out = over(out, argb(a, 0.55f, 0.78f, 1f));
            }
        } else {
            // hatágú hópelyhek
            float[][] flakes = {{20, 50, 4.5f}, {33, 57, 4f}, {45, 50, 4.5f}, {27, 61, 2.8f}, {40, 62, 3f}};
            for (float[] f : flakes) {
                float fx = x - f[0], fy = y - f[1];
                float r = (float) Math.sqrt(fx * fx + fy * fy);
                if (r > f[2] + 1) continue;
                double a = Math.atan2(fy, fx);
                double k = Math.abs(((a / (Math.PI / 3)) % 1 + 1) % 1 - 0.5) * 2; // 1 = kar közepe
                float arm = (float) (r * Math.sin((1 - k) * Math.PI / 6));
                float fa = smooth(0.9f, 0.35f, Math.abs(arm)) * smooth(f[2] + 0.6f, f[2] - 0.4f, r);
                fa = Math.max(fa, smooth(1.4f, 0.6f, r));
                if (fa > 0) out = over(out, argb(fa, 1f, 1f, 1f));
            }
        }
        out = over(out, cloud(x, y, -10f, kind == 2 ? 2 : 1));
        if (kind == 2) {
            // villám ragyogással
            float[][] bolt = {{36, 30}, {28, 44}, {34, 44}, {27, 60}, {42, 39}, {35, 39}, {41, 30}};
            float minD = 99f;
            for (int i = 0; i < bolt.length; i++) {
                float[] p = bolt[i], q = bolt[(i + 1) % bolt.length];
                minD = Math.min(minD, segDist(x, y, p[0], p[1], q[0], q[1]));
            }
            boolean inside = insidePolygon(x, y, bolt);
            float glowA = (float) Math.pow(clamp01(1f - minD / 9f), 2) * 0.55f;
            if (!inside && glowA > 0) out = over(out, argb(glowA, 1f, 0.9f, 0.45f));
            if (inside) {
                float core = smooth(0f, 2.2f, minD);
                out = over(out, argb(1f, 1f, mix(0.80f, 1f, core), mix(0.15f, 0.85f, core)));
            }
        }
        return out;
    }

    static boolean insidePolygon(float x, float y, float[][] poly) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            float xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    // ================================================================== éjjellátó szemüveg (128x64)

    static int goggles(float x, float y) {
        int out = 0;
        // szövött pánt
        if (y > 25 && y < 39) {
            float weave = 0.5f + 0.5f * (float) Math.sin(x * 1.3f) * (float) Math.sin(y * 1.7f);
            float edgeShade = 1f - 0.45f * sq((y - 32f) / 7f);
            float v = (0.10f + 0.05f * weave) * edgeShade;
            float a = smooth(25f, 26.2f, y) * smooth(39f, 37.8f, y);
            out = argb(a, v, v * 1.05f, v * 0.95f);
        }
        // orrnyereg (fém)
        if (x > 52 && x < 76 && y > 22 && y < 34) {
            float t = (y - 22f) / 12f;
            float v = mix(0.55f, 0.18f, t);
            float a = smooth(52f, 53.5f, x) * smooth(76f, 74.5f, x) * smooth(22f, 23.2f, y) * smooth(34f, 32.8f, y);
            out = over(out, argb(a, v, v, v * 1.05f));
        }
        float[][] lenses = {{34, 32}, {94, 32}};
        for (float[] l : lenses) {
            float d = dist(x, y, l[0], l[1]);
            if (d > 28f) continue;
            float nx = (x - l[0]) / 27f, ny = (y - l[1]) / 27f;
            // fém foglalat: hengeres megvilágítás
            float housing = smooth(27.5f, 26.5f, d);
            float shade = sphereLight(nx, ny, -0.5f, -0.7f, 0.6f);
            float metal = 0.16f + 0.55f * (float) Math.pow(shade, 2.2);
            out = over(out, argb(housing, metal * 0.95f, metal, metal * 0.92f));
            // csavarok a peremen
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + Math.PI / 6;
                float bx = l[0] + (float) Math.cos(a) * 23.5f, by = l[1] + (float) Math.sin(a) * 23.5f;
                float bd = dist(x, y, bx, by);
                if (bd < 2.2f) {
                    float bl = sphereLight((x - bx) / 2.2f, (y - by) / 2.2f, -0.5f, -0.7f, 0.6f);
                    out = over(out, argb(smooth(2.2f, 1.6f, bd), 0.3f + 0.6f * bl, 0.3f + 0.6f * bl, 0.32f + 0.6f * bl));
                }
            }
            // belső sötét gyűrű
            if (d < 21f) out = over(out, argb(smooth(21f, 20f, d), 0.04f, 0.05f, 0.04f));
            // lencse: mély zöld üveg, középen világít
            if (d < 19f) {
                float t = d / 19f;
                float glowV = (float) Math.pow(1f - t, 1.4);
                float scan = 0.92f + 0.08f * (float) Math.sin(y * 2.2f);
                float r = (0.05f + 0.35f * glowV) * scan, g = (0.25f + 0.75f * glowV) * scan, b = (0.08f + 0.30f * glowV) * scan;
                out = over(out, argb(smooth(19f, 18.2f, d), r, g, b));
                // célkereszt gyűrű
                float ring = smooth(0.9f, 0.3f, Math.abs(d - 9f)) * 0.25f;
                out = over(out, argb(ring, 0.6f, 1f, 0.6f));
                // tükröződés: ív bal felül
                float arcD = dist(x, y, l[0] + 4f, l[1] + 5f);
                float arc = smooth(1.6f, 0.4f, Math.abs(arcD - 15f)) * smooth(0f, -10f, (x - l[0]) + (y - l[1]) * 0.8f);
                float spot = smooth(4.5f, 1f, dist(x, y, l[0] - 7f, l[1] - 8f));
                float refl = Math.max(arc * 0.7f, spot * 0.85f);
                if (refl > 0) out = over(out, argb(refl, 0.92f, 1f, 0.92f));
            }
        }
        return out;
    }

    // ================================================================== kis ikonok (32x32) az állapotsávokhoz

    static int heart(float x, float y) {
        // két kör + lekerekített háromszög uniója, előjeles távolságmezővel
        float c1 = dist(x, y, 10.6f, 11.5f) - 6.6f;
        float c2 = dist(x, y, 21.4f, 11.5f) - 6.6f;
        float[][] tri = {{4.4f, 13.6f}, {27.6f, 13.6f}, {16f, 27.5f}};
        float td = Math.min(segDist(x, y, tri[0][0], tri[0][1], tri[2][0], tri[2][1]),
                segDist(x, y, tri[1][0], tri[1][1], tri[2][0], tri[2][1]));
        float t = insidePolygon(x, y, tri) ? -td : td;
        float sdf = Math.min(Math.min(c1, c2), t + 0.6f);
        if (sdf > 0.8f) return 0;
        float a = smooth(0.8f, -0.4f, sdf);
        // „pufók” 3D hatás: a peremtől befelé emelkedik
        float depth = clamp01(-sdf / 5.5f);
        float nz = (float) Math.sqrt(depth);
        float light = clamp01(0.25f + 0.75f * nz - (x - 16f) * 0.012f - (y - 16f) * 0.02f);
        float r = mix(0.42f, 1f, light), g = mix(0.02f, 0.22f, light * light), b = mix(0.06f, 0.24f, light * light);
        int out = argb(a, r, g, b);
        float hl = smooth(1f, 0.25f, (float) Math.sqrt(sq((x - 9.5f) / 3.6f) + sq((y - 9.5f) / 2.6f)));
        return over(out, argb(hl * 0.85f * a, 1f, 0.94f, 0.94f));
    }

    static int food(float x, float y) {
        int out = 0;
        // csont: két gömbvég + rúd (jobbra lent)
        float bone = Math.min(segDist(x, y, 19, 19, 25, 25) - 2.2f, Math.min(dist(x, y, 26, 23.5f) - 2.6f, dist(x, y, 23.5f, 26) - 2.6f));
        if (bone < 1f) {
            float l = clamp01(0.55f + 0.45f * (-(x - 24f) - (y - 24f)) / 6f);
            out = argb(smooth(1f, 0f, bone), 0.82f + 0.18f * l, 0.80f + 0.18f * l, 0.72f + 0.18f * l);
        }
        // hús: elforgatott ellipszis, gömbszerű árnyalással + pirult kéreg
        float cx = 13f, cy = 13f;
        float rx = ((x - cx) + (y - cy)) / 1.4142f, ry = ((x - cx) - (y - cy)) / 1.4142f;
        float d = (float) Math.sqrt(sq(rx / 12f) + sq(ry / 8.5f));
        if (d < 1.05f) {
            float nl = sphereLight(rx / 12f, ry / 8.5f, -0.6f, -0.2f, 0.75f);
            float n = noise(x * 0.9f, y * 0.9f);
            float r = mix(0.35f, 0.85f, nl) - n * 0.06f, g = mix(0.12f, 0.42f, nl) - n * 0.04f, b = mix(0.04f, 0.16f, nl);
            out = over(out, argb(smooth(1.05f, 0.95f, d), r, g, b));
            float hl = smooth(1f, 0.2f, dist(x, y, 9f, 9f) / 3.2f);
            out = over(out, argb(hl * 0.6f, 1f, 0.9f, 0.75f));
        }
        return out;
    }

    static int armor(float x, float y) {
        // mellvért sziluett
        float[][] shape = {{6, 6}, {12, 4}, {16, 8}, {20, 4}, {26, 6}, {29, 13}, {24, 13}, {24, 28}, {8, 28}, {8, 13}, {3, 13}};
        if (!insidePolygon(x, y, shape)) return 0;
        float minD = 99f;
        for (int i = 0; i < shape.length; i++) {
            float[] p = shape[i], q = shape[(i + 1) % shape.length];
            minD = Math.min(minD, segDist(x, y, p[0], p[1], q[0], q[1]));
        }
        float a = smooth(0f, 0.8f, minD);
        // fémes: függőleges színátmenet + középvonal + szélek kiemelése
        float t = (y - 4f) / 24f;
        float v = mix(0.92f, 0.45f, t) - 0.12f * (float) Math.abs(x - 16f) / 13f;
        float bevel = smooth(2.5f, 0f, minD) * 0.25f;
        float center = smooth(1.2f, 0f, Math.abs(x - 16f)) * 0.12f * (y > 9 ? 1 : 0);
        v = clamp01(v + bevel - center);
        return argb(a, v * 0.92f, v * 0.96f, v);
    }

    static int bubble(float x, float y) {
        float d = dist(x, y, 16, 16) / 12f;
        if (d > 1.05f) return 0;
        float a = smooth(1.05f, 0.95f, d);
        float rim = smooth(0.6f, 1f, d);
        int out = argb(a * (0.18f + 0.6f * rim), 0.55f, 0.85f, 1f);
        float hl = smooth(1f, 0.3f, dist(x, y, 12f, 11f) / 3.5f);
        out = over(out, argb(hl * 0.95f, 1f, 1f, 1f));
        float hl2 = smooth(1f, 0.3f, dist(x, y, 21f, 22f) / 1.8f);
        return over(out, argb(hl2 * 0.6f, 1f, 1f, 1f));
    }

    /** Vízcsepp ikon a szomjúság sávhoz (telt, kék). */
    static int waterIcon(float x, float y) {
        float cx = 16f, cy = 19f;
        float dx = x - cx, dy = y - cy;
        float rx = dy < 0 ? 10f * (float) Math.pow(clamp01(1f + dy / 16f), 0.8) : 10f;
        float ry = dy < 0 ? 16f : 10f;
        if (rx <= 0.3f) return 0;
        float d = (float) Math.sqrt(dx * dx / (rx * rx) + dy * dy / (ry * ry));
        if (d > 1.04f) return 0;
        float a = smooth(1.04f, 0.94f, d);
        float l = sphereLight(dx / 10f, Math.max(-1f, dy / 12f), -0.5f, -0.6f, 0.65f);
        int out = argb(a, mix(0.05f, 0.45f, l), mix(0.30f, 0.75f, l), mix(0.70f, 1f, l));
        float hl = smooth(1f, 0.3f, (float) Math.sqrt(sq((x - 12.5f) / 2.2f) + sq((y - 16f) / 3.5f)));
        return over(out, argb(hl * 0.9f * a, 1f, 1f, 1f));
    }
}
