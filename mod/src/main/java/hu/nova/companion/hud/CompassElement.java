package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Vízszintes iránytű sáv: a szélek felé elhalványuló jelek, kiemelt É, fokszám-jelvény középen. */
public final class CompassElement extends HudElement {

    private static final int WIDTH = 220;
    private static final int BAR = 20;
    private static final float VISIBLE = 140f;
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
        return BAR + 14;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        float yaw = mc.player != null ? mc.player.getYRot() : 0f;
        float heading = Mth.positiveModulo(yaw + 180f, 360f);
        float ppd = WIDTH / VISIBLE;
        int center = WIDTH / 2;

        HudStyle.panel(g, 0, 0, WIDTH, BAR);
        for (int deg = 0; deg < 360; deg += 5) {
            float delta = Mth.wrapDegrees(deg - heading);
            if (Math.abs(delta) > VISIBLE / 2f) continue;
            int x = center + Math.round(delta * ppd);
            // elhalványulás a szélek felé
            float fade = 1f - (float) Math.pow(Math.abs(delta) / (VISIBLE / 2f), 2.2);
            if (deg % 45 == 0) {
                String name = NAMES[deg / 45];
                boolean cardinal = deg % 90 == 0;
                int base = deg == 0 ? 0xFFFF5A5A : (cardinal ? 0xFFFFFFFF : 0xFFC9D2DD);
                float s = cardinal ? 1f : 0.75f;
                float tw = mc.font.width(name) * s;
                HudStyle.text(g, mc.font, name, x - tw / 2f + 0.5f, cardinal ? 3 : 4.5f, HudStyle.withAlpha(base, fade), s);
                g.fill(x, BAR - 5, x + 1, BAR - 1, HudStyle.withAlpha(0xFFFFFFFF, fade));
            } else if (deg % 15 == 0) {
                g.fill(x, BAR - 4, x + 1, BAR - 1, HudStyle.withAlpha(0xFFB0BAC6, fade * 0.9f));
            } else {
                g.fill(x, BAR - 2, x + 1, BAR - 1, HudStyle.withAlpha(0xFF8090A0, fade * 0.6f));
            }
        }
        // középső jelölő: arany háromszög felül + vonal
        g.fill(center - 3, 0, center + 4, 1, 0xFFFFD34D);
        g.fill(center - 2, 1, center + 3, 2, 0xFFFFD34D);
        g.fill(center - 1, 2, center + 2, 3, 0xFFFFD34D);
        g.fill(center, 3, center + 1, BAR - 1, 0x90FFD34D);

        // fokszám-jelvény
        String num = (Math.round(heading) % 360) + "°";
        int nw = mc.font.width(num) + 10;
        HudStyle.panel(g, center - nw / 2, BAR + 2, nw, 11);
        g.drawString(mc.font, num, center - mc.font.width(num) / 2 + 1, BAR + 4, 0xFFFFD34D, true);
    }
}
