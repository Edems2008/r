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
}
