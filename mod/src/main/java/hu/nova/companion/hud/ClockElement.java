package hu.nova.companion.hud;

import hu.nova.companion.fx.FxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Minecraft-beli óra + napszámláló, alatta a nap menetét mutató sáv (éjszaka → hajnal → nappal → alkony).
 * A dayTime értéket a szerver szinkronizálja, így szerveren is pontos. (dayTime 0 = reggel 6:00)
 */
public final class ClockElement extends HudElement {

    private static final int W = 112, H = 32;

    public ClockElement() {
        super("clock", "Minecraft óra");
    }

    /** @return {óra, perc, hányadik nap (1-től), eltelt teljes napok} */
    public static long[] gameTime(Minecraft mc) {
        long dayTime = mc.level != null ? mc.level.getDayTime() : 0L;
        long daysPassed = dayTime / 24000L;
        long t = dayTime % 24000L;
        long hour = (t / 1000L + 6L) % 24L;
        long minute = (t % 1000L) * 60L / 1000L;
        return new long[]{hour, minute, daysPassed + 1, daysPassed};
    }

    @Override
    public int width(Minecraft mc) {
        return W;
    }

    @Override
    public int height(Minecraft mc) {
        return H;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        long[] gt = gameTime(mc);
        boolean day = gt[0] >= 6 && gt[0] < 18;
        FxTextures.ensure(mc);
        HudStyle.panel(g, 0, 0, W, H);
        HudStyle.accent(g, 0, 0, H, day ? 0xFFFFC94D : 0xFF8DA2FF);
        HudStyle.icon(g, day ? FxTextures.SUN : FxTextures.MOON, FxTextures.ICON, FxTextures.ICON, 3, 1, 18, 18);

        String time = String.format("%02d:%02d", gt[0], gt[1]);
        HudStyle.text(g, mc.font, time, 23, 4, day ? 0xFFFFE7A0 : 0xFFD6DEFF, 1.25f);
        String dayStr = gt[2] + ". nap";
        float dw = mc.font.width(dayStr) * 0.85f;
        HudStyle.text(g, mc.font, dayStr, W - 5 - dw, 4, HudStyle.TEXT, 0.85f);
        String passed = "eltelt " + gt[3];
        float pw = mc.font.width(passed) * 0.7f;
        HudStyle.text(g, mc.font, passed, W - 5 - pw, 13, HudStyle.MUTED, 0.7f);

        // napszak-sáv: a teljes nap 0:00-24:00, a jelölő a jelenlegi idő
        int bx = 5, by = 23, bw = W - 10, bh = 4;
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            int x0 = bx + bw * i / segs, x1 = bx + bw * (i + 1) / segs;
            g.fill(x0, by, x1, by + bh, skyColor(i + 0.5f));
        }
        g.fill(bx, by, bx + bw, by + 1, 0x30FFFFFF);
        float now = gt[0] + gt[1] / 60f;
        int mx = bx + Math.round(bw * now / 24f);
        g.fill(mx - 1, by - 2, mx + 1, by + bh + 2, 0xFFFFFFFF);
        g.fill(mx - 2, by - 2, mx + 2, by - 1, 0xFFFFFFFF);
    }

    /** az égbolt színe az adott órában (éjjel sötétkék, hajnal/alkony narancs, nappal égkék) */
    static int skyColor(float hour) {
        int night = 0xFF1B2450, dawn = 0xFFF29A4A, day = 0xFF6EC1FF;
        if (hour < 4.5f || hour >= 20f) return night;
        if (hour < 6f) return HudStyle.lerpColor(night, dawn, (hour - 4.5f) / 1.5f);
        if (hour < 8f) return HudStyle.lerpColor(dawn, day, (hour - 6f) / 2f);
        if (hour < 17f) return day;
        if (hour < 18.5f) return HudStyle.lerpColor(day, dawn, (hour - 17f) / 1.5f);
        return HudStyle.lerpColor(dawn, night, (hour - 18.5f) / 1.5f);
    }
}
