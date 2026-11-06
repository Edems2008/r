package hu.nova.companion.fx;

import com.mojang.blaze3d.platform.NativeImage;
import hu.nova.companion.NovaCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Az effektekhez szükséges textúrákat futásidőben, kódból generáljuk
 * (vízcsepp, lágy szélű vignetta, izzó parázs), így nincs szükség képfájlokra.
 */
public final class FxTextures {

    public static final ResourceLocation DROP = new ResourceLocation(NovaCompanion.MODID, "dynamic/drop");
    public static final ResourceLocation VIGNETTE = new ResourceLocation(NovaCompanion.MODID, "dynamic/vignette");
    public static final ResourceLocation GLOW = new ResourceLocation(NovaCompanion.MODID, "dynamic/glow");

    public static final ResourceLocation SUN = new ResourceLocation(NovaCompanion.MODID, "dynamic/w_sun");
    public static final ResourceLocation MOON = new ResourceLocation(NovaCompanion.MODID, "dynamic/w_moon");
    public static final ResourceLocation CLOUD = new ResourceLocation(NovaCompanion.MODID, "dynamic/w_cloud");
    public static final ResourceLocation RAIN = new ResourceLocation(NovaCompanion.MODID, "dynamic/w_rain");
    public static final ResourceLocation SNOW = new ResourceLocation(NovaCompanion.MODID, "dynamic/w_snow");
    public static final ResourceLocation STORM = new ResourceLocation(NovaCompanion.MODID, "dynamic/w_storm");
    public static final ResourceLocation GOGGLES = new ResourceLocation(NovaCompanion.MODID, "dynamic/goggles");
    public static final int ICON = 32;
    public static final int GOGGLES_W = 64;
    public static final int GOGGLES_H = 32;

    public static final int DROP_W = 32;
    public static final int DROP_H = 40;
    public static final int VIGNETTE_SIZE = 256;
    public static final int GLOW_SIZE = 32;

    private static boolean ready;

    private FxTextures() {}

    public static void ensure(Minecraft mc) {
        if (ready) return;
        ready = true;
        register(mc, DROP, DROP_W, DROP_H, FxTextures::dropPixel);
        register(mc, VIGNETTE, VIGNETTE_SIZE, VIGNETTE_SIZE, FxTextures::vignettePixel);
        register(mc, GLOW, GLOW_SIZE, GLOW_SIZE, FxTextures::glowPixel);
        register(mc, SUN, ICON, ICON, FxTextures::sunPixel);
        register(mc, MOON, ICON, ICON, FxTextures::moonPixel);
        register(mc, CLOUD, ICON, ICON, (x, y) -> cloudPixel(x, y, 0, false));
        register(mc, RAIN, ICON, ICON, (x, y) -> precipitationPixel(x, y, 0));
        register(mc, SNOW, ICON, ICON, (x, y) -> precipitationPixel(x, y, 1));
        register(mc, STORM, ICON, ICON, (x, y) -> precipitationPixel(x, y, 2));
        register(mc, GOGGLES, GOGGLES_W, GOGGLES_H, FxTextures::gogglesPixel);
    }

    private interface PixelFn {
        /** @return ARGB */
        int argb(int x, int y);
    }

    private static void register(Minecraft mc, ResourceLocation loc, int w, int h, PixelFn fn) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = fn.argb(x, y);
                int a = (c >>> 24) & 0xff, r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r); // NativeImage: ABGR
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        tex.setFilter(true, false); // lágy (lineáris) szűrés
        mc.getTextureManager().register(loc, tex);
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    static int argb(float a, float r, float g, float b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    /** Csepp alakú, fénytörést utánzó vízcsepp: áttetsző közép, sötétebb perem, fehér csillanás. */
    static int dropPixel(int x, int y) {
        float cx = 16f, cy = 24f;
        float dx = (x + 0.5f - cx);
        float dy = (y + 0.5f - cy);
        // felfelé elkeskenyedő forma
        float rx = dy < 0 ? 12f * (1f + dy / 30f) : 12f;
        float ry = dy < 0 ? 22f : 14f;
        if (rx <= 0.5f) return 0;
        float d = (float) Math.sqrt((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry));
        if (d >= 1f) return 0;
        float edge = smooth(1f, 0.85f, d);                 // élsimítás
        float rim = smooth(0.55f, 0.95f, d);               // sötétebb perem
        float alpha = (0.18f + 0.55f * rim) * edge;
        float shade = 0.75f + 0.25f * (1f - (dy + 14f) / 28f); // felül világosabb
        float r = 0.70f * shade, g = 0.82f * shade, b = 0.95f * shade;
        // csillanás bal felül
        float hx = x + 0.5f - 11.5f, hy = y + 0.5f - 18f;
        float hl = smooth(4.5f, 1.0f, (float) Math.sqrt(hx * hx + hy * hy));
        // halvány visszfény jobb alul
        float lx = x + 0.5f - 20f, ly = y + 0.5f - 31f;
        float lo = smooth(4f, 0.5f, (float) Math.sqrt(lx * lx + ly * ly)) * 0.5f;
        float glint = Math.max(hl, lo);
        r = r + (1f - r) * glint;
        g = g + (1f - g) * glint;
        b = b + (1f - b) * glint;
        alpha = Math.max(alpha, glint * 0.95f * edge);
        return argb(alpha, r, g, b);
    }

    /** Fehér, a szélek felé erősödő vignetta (színezése rajzoláskor történik). */
    static int vignettePixel(int x, int y) {
        float nx = (x + 0.5f) / VIGNETTE_SIZE * 2f - 1f;
        float ny = (y + 0.5f) / VIGNETTE_SIZE * 2f - 1f;
        float d = (float) Math.sqrt(nx * nx + ny * ny) / 1.4142f;
        float a = smooth(0.35f, 1.0f, d);
        return argb(a * a, 1f, 1f, 1f);
    }

    /** Lágy kör alakú izzás (parázshoz). */
    static int glowPixel(int x, int y) {
        float c = GLOW_SIZE / 2f;
        float dx = x + 0.5f - c, dy = y + 0.5f - c;
        float d = (float) Math.sqrt(dx * dx + dy * dy) / c;
        float a = smooth(1f, 0f, d);
        return argb(a * a, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ időjárás ikonok

    private static float dist(float x, float y, float cx, float cy) {
        float dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** alpha-kompozit: src rá a dst-re (ARGB) */
    static int over(int dst, int src) {
        float sa = ((src >>> 24) & 0xff) / 255f, da = ((dst >>> 24) & 0xff) / 255f;
        float oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        float r = (((src >> 16) & 0xff) * sa + ((dst >> 16) & 0xff) * da * (1 - sa)) / oa;
        float g = (((src >> 8) & 0xff) * sa + ((dst >> 8) & 0xff) * da * (1 - sa)) / oa;
        float b = ((src & 0xff) * sa + (dst & 0xff) * da * (1 - sa)) / oa;
        return argb(oa, r / 255f, g / 255f, b / 255f);
    }

    static int sunPixel(int ix, int iy) {
        float x = ix + 0.5f, y = iy + 0.5f;
        float d = dist(x, y, 16, 16);
        int out = 0;
        // sugarak
        double ang = Math.atan2(y - 16, x - 16);
        double rel = Math.abs(((ang / (Math.PI / 4)) % 1 + 1) % 1 - 0.5); // 0.5 = sugár közepe
        if (d > 9.5f && d < 15f && rel > 0.36) {
            float a = smooth(15f, 13.5f, d) * smooth(0.36f, 0.42f, (float) rel);
            out = argb(a, 1f, 0.78f, 0.15f);
        }
        if (d < 8.5f) {
            float a = smooth(8.5f, 7.5f, d);
            float t = Math.min(1f, d / 8f);
            out = over(out, argb(a, 1f, 0.95f - 0.2f * t, 0.45f - 0.35f * t));
        }
        return out;
    }

    static int moonPixel(int ix, int iy) {
        float x = ix + 0.5f, y = iy + 0.5f;
        float a = smooth(11f, 10f, dist(x, y, 15, 16)) * smooth(8.5f, 9.5f, dist(x, y, 21, 12));
        if (a <= 0) return 0;
        float crater = smooth(2.2f, 1.2f, dist(x, y, 10, 19)) * 0.15f + smooth(1.6f, 0.8f, dist(x, y, 13, 24)) * 0.12f;
        return argb(a, 0.93f - crater, 0.94f - crater, 0.80f - crater);
    }

    /** felhő maszk 0..1 (dy: függőleges eltolás) */
    private static float cloudMask(float x, float y, float dy) {
        float m = 0f;
        m = Math.max(m, smooth(6.5f, 5.5f, dist(x, y, 10, 19 + dy)));
        m = Math.max(m, smooth(8f, 7f, dist(x, y, 17, 14 + dy)));
        m = Math.max(m, smooth(6f, 5f, dist(x, y, 24, 18 + dy)));
        if (x > 9 && x < 25 && y > 17 + dy && y < 24.5f + dy) m = Math.max(m, smooth(24.5f + dy, 23.5f + dy, y));
        return m;
    }

    static int cloudPixel(int ix, int iy, float dy, boolean dark) {
        float x = ix + 0.5f, y = iy + 0.5f;
        float m = cloudMask(x, y, dy);
        if (m <= 0) return 0;
        float shade = 1f - 0.25f * Math.max(0f, Math.min(1f, (y - (8 + dy)) / 16f)); // alul sötétebb
        float base = dark ? 0.42f : 0.92f;
        float c = base * shade;
        return argb(m, c, c, Math.min(1f, c + (dark ? 0.06f : 0.04f)));
    }

    /** 0 = eső, 1 = hó, 2 = vihar (villámmal) */
    static int precipitationPixel(int ix, int iy, int kind) {
        float x = ix + 0.5f, y = iy + 0.5f;
        int out = 0;
        if (kind == 0 || kind == 2) {
            // ferde esőcsíkok
            for (int i = 0; i < 4; i++) {
                float sx = 10 + i * 4.5f, sy = 23;
                float t = (y - sy) / 7f;
                if (t < 0 || t > 1) continue;
                float lx = sx - t * 2.5f;
                float a = smooth(1.1f, 0.4f, Math.abs(x - lx)) * (1f - t * 0.3f);
                if (a > 0) out = over(out, argb(a, 0.35f, 0.65f, 1f));
            }
        } else {
            float[][] flakes = {{10, 26}, {16, 29}, {22, 26}, {13, 31}, {20, 31}, {25, 30}};
            for (float[] f : flakes) {
                float a = smooth(1.6f, 0.8f, dist(x, y, f[0], f[1]));
                if (a > 0) out = over(out, argb(a, 1f, 1f, 1f));
            }
        }
        out = over(out, cloudPixel(ix, iy, -5, kind == 2));
        if (kind == 2) {
            // villám: cikcakk sokszög
            float[][] bolt = {{18, 14}, {13, 23}, {17, 23}, {14, 31}, {22, 20}, {18, 20}, {21, 14}};
            if (insidePolygon(x, y, bolt)) out = over(out, argb(1f, 1f, 0.88f, 0.2f));
        }
        return out;
    }

    private static boolean insidePolygon(float x, float y, float[][] poly) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            float xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    /** Éjjellátó szemüveg: pánt, keret, két zölden izzó lencse. */
    static int gogglesPixel(int ix, int iy) {
        float x = ix + 0.5f, y = iy + 0.5f;
        int out = 0;
        // pánt
        if (y > 12 && y < 19) out = argb(1f, 0.16f, 0.17f, 0.15f);
        // orrnyereg
        if (x > 27 && x < 37 && y > 11 && y < 17) out = argb(1f, 0.24f, 0.26f, 0.22f);
        float[][] lenses = {{18, 16}, {46, 16}};
        for (float[] l : lenses) {
            float d = dist(x, y, l[0], l[1]);
            if (d < 13f) {
                float frame = smooth(13f, 12f, d);
                out = over(out, argb(frame, 0.30f, 0.32f, 0.28f));       // külső keret
                if (d < 10.5f) out = over(out, argb(1f, 0.12f, 0.13f, 0.11f));
                if (d < 9f) {
                    float t = d / 9f;
                    float glow = 1f - t * 0.65f;
                    out = over(out, argb(smooth(9f, 8.2f, d), 0.25f * glow, 1f * glow, 0.35f * glow));
                    float hl = smooth(3f, 1f, dist(x, y, l[0] - 3.5f, l[1] - 3.5f));
                    if (hl > 0) out = over(out, argb(hl * 0.8f, 0.9f, 1f, 0.9f));
                }
            }
        }
        return out;
    }
}
