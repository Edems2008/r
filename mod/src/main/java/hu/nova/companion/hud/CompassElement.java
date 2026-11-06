package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Vízszintes iránytű sáv (mint a modern FPS játékokban). */
public final class CompassElement extends HudElement {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 22;
    private static final float VISIBLE_DEGREES = 120f;
    private static final String[] NAMES = {"É", "ÉK", "K", "DK", "D", "DNY", "NY", "ÉNY"};

    public CompassElement() {
        super("compass", "Iránytű");
    }

    @Override
    public int width(Minecraft mc) {
        return WIDTH;
    }

    @Override
    public int height(Minecraft mc) {
        return HEIGHT + mc.font.lineHeight + 2;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        float yaw = mc.player != null ? mc.player.getYRot() : 0f;
        // Minecraft: yaw 0 = dél, 90 = nyugat, 180 = észak -> iránytű fok (É = 0)
        float heading = Mth.positiveModulo(yaw + 180f, 360f);
        float ppd = WIDTH / VISIBLE_DEGREES;
        int center = WIDTH / 2;

        g.fill(0, 0, WIDTH, HEIGHT, 0x90000000);

        for (int deg = 0; deg < 360; deg += 15) {
            float delta = Mth.wrapDegrees(deg - heading);
            if (Math.abs(delta) > VISIBLE_DEGREES / 2f) continue;
            int x = center + Math.round(delta * ppd);
            if (deg % 45 == 0) {
                String name = NAMES[deg / 45];
                int color = deg == 0 ? 0xFFFF5555 : 0xFFFFFFFF;
                g.drawString(mc.font, name, x - mc.font.width(name) / 2, 3, color, true);
                g.fill(x, HEIGHT - 6, x + 1, HEIGHT - 1, 0xFFFFFFFF);
            } else {
                g.fill(x, HEIGHT - 4, x + 1, HEIGHT - 1, 0xFFAAAAAA);
            }
        }

        // középső jelölő + fokszám
        g.fill(center - 1, 0, center + 1, HEIGHT, 0xFFFFD700);
        String num = Math.round(heading) % 360 + "°";
        int nw = mc.font.width(num);
        g.fill(center - nw / 2 - 2, HEIGHT, center + nw / 2 + 3, HEIGHT + mc.font.lineHeight + 2, 0x90000000);
        g.drawString(mc.font, num, center - nw / 2 + 1, HEIGHT + 1, 0xFFFFD700, true);
    }
}
