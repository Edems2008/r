package hu.nova.companion.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Egységes HUD megjelenés: lekerekített, áttetsző „üveg” panelek, finom fények és árnyékok. */
public final class HudStyle {

    public static final int ACCENT = 0xFF4ADE80;
    public static final int TEXT = 0xFFF2F5F8;
    public static final int MUTED = 0xFF9AA6B5;

    private HudStyle() {}

    /** Lekerekített sarkú, színátmenetes panel árnyékkal és felső fényvonallal. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        int top = 0xB8161C25, bottom = 0xCC090C11;
        // lágy árnyék
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, 0x50000000);
        g.fill(x + 2, y + h + 1, x + w - 2, y + h + 2, 0x22000000);
        // test (1px-es saroklekerekítés)
        g.fill(x + 1, y, x + w - 1, y + 1, top);
        g.fillGradient(x, y + 1, x + w, y + h - 1, top, bottom);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
        // üveg-hatás: felső fényvonal + halvány oldalsó keret
        g.fill(x + 1, y, x + w - 1, y + 1, 0x38FFFFFF);
        g.fill(x, y + 1, x + 1, y + h - 1, 0x14FFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0x0CFFFFFF);
    }

    /** Színes jelzőcsík a panel bal szélén. */
    public static void accent(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y + 2, x + 2, y + h - 2, color);
        g.fill(x + 2, y + 2, x + 3, y + h - 2, (color & 0x00FFFFFF) | 0x40000000);
    }

    /** Kicsinyített (vagy nagyított) szöveg. */
    public static void text(GuiGraphics g, Font font, String s, float x, float y, int color, float scale) {
        if (((color >>> 24) & 0xff) < 6) return; // a Minecraft a ~0 alfájú szöveget átlátszatlannak rajzolná
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Generált textúra kirajzolása tetszőleges méretben, alfa-keveréssel. */
    public static void icon(GuiGraphics g, ResourceLocation tex, int texW, int texH, float x, float y, float w, float h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / texW, h / texH, 1f);
        g.blit(tex, 0, 0, 0, 0, texW, texH, texW, texH);
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    /**
     * Élethű sáv: lekerekített végek, belső árnyék, fényes felső él, finom osztásjelek.
     * @param frac kitöltés 0..1
     */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int topColor, int bottomColor, int segments) {
        barBackground(g, x, y, w, h);
        barFill(g, x, y, w, h, frac, topColor, bottomColor);
        barSegments(g, x, y, w, h, segments);
    }

    public static void barBackground(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y - 1, x + w, y, 0xC0000000);
        g.fill(x, y + h, x + w, y + h + 1, 0xC0000000);
        g.fill(x - 1, y, x, y + h, 0xC0000000);
        g.fill(x + w, y, x + w + 1, y + h, 0xC0000000);
        g.fillGradient(x, y, x + w, y + h, 0xD0141012, 0xD0221C1E);
    }

    public static void barFill(GuiGraphics g, int x, int y, int w, int h, float frac, int topColor, int bottomColor) {
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (fw <= 0) return;
        g.fillGradient(x, y, x + fw, y + h, topColor, bottomColor);
        g.fill(x, y, x + fw, y + 1, 0x60FFFFFF);                  // fényes él
        if (h > 4) g.fill(x, y + 1, x + fw, y + h / 2, 0x18FFFFFF); // üveges felső fél
        g.fill(x + fw - 1, y, x + fw, y + h, 0x30FFFFFF);         // csillanás a sáv végén
    }

    public static void barSegments(GuiGraphics g, int x, int y, int w, int h, int segments) {
        g.fill(x, y + h - 1, x + w, y + h, 0x40000000); // belső árnyék alul
        for (int i = 1; i < segments; i++) {
            int sx = x + Math.round(w * i / (float) segments);
            g.fill(sx, y, sx + 1, y + h, 0x38000000);
        }
    }

    public static int withAlpha(int color, float a) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (color & 0x00FFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xff, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = (b >>> 24) & 0xff, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }
}
