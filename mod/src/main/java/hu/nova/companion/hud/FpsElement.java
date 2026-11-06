package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Valós FPS számláló: nagy szám, színes állapotjelző és az utolsó ~10 másodperc mini-grafikonja. */
public final class FpsElement extends HudElement {

    private static final int W = 74;
    private static final int SAMPLES = 40;
    private final int[] history = new int[SAMPLES];
    private int head;
    private long lastSample;

    public FpsElement() {
        super("fps", "FPS");
    }

    @Override
    public int width(Minecraft mc) {
        return W;
    }

    @Override
    public int height(Minecraft mc) {
        return 28;
    }

    private static int color(int fps) {
        return fps >= 60 ? 0xFF4ADE80 : fps >= 30 ? 0xFFFACC15 : 0xFFF87171;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        int fps = mc.getFps();
        long now = System.currentTimeMillis();
        if (now - lastSample > 250) {
            lastSample = now;
            history[head] = fps;
            head = (head + 1) % SAMPLES;
        }
        int c = color(fps);
        HudStyle.panel(g, 0, 0, W, 28);
        HudStyle.accent(g, 0, 0, 28, c);
        // szám nagyobb méretben + „FPS” felirat
        String num = String.valueOf(fps);
        HudStyle.text(g, mc.font, num, 6, 4, HudStyle.TEXT, 1.25f);
        HudStyle.text(g, mc.font, "FPS", 6 + mc.font.width(num) * 1.25f + 3, 6.5f, HudStyle.MUTED, 0.75f);
        // állapotjelző pötty
        g.fill(W - 9, 5, W - 5, 9, c);
        g.fill(W - 10, 6, W - 4, 8, HudStyle.withAlpha(c, 0.35f));

        // mini-grafikon
        int gx = 6, gy = 17, gw = W - 12, gh = 7;
        g.fill(gx, gy, gx + gw, gy + gh, 0x30000000);
        int max = 60;
        for (int v : history) max = Math.max(max, v);
        float step = gw / (float) SAMPLES;
        for (int i = 0; i < SAMPLES; i++) {
            int v = history[(head + i) % SAMPLES];
            if (v <= 0) continue;
            int bh = Math.max(1, Math.round(gh * v / (float) max));
            int x0 = gx + Math.round(i * step);
            int x1 = Math.max(x0 + 1, gx + Math.round((i + 1) * step) - 0);
            g.fillGradient(x0, gy + gh - bh, x1, gy + gh, HudStyle.withAlpha(color(v), 0.95f), HudStyle.withAlpha(color(v), 0.35f));
        }
    }
}
