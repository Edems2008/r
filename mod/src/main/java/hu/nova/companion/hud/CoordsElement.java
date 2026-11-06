package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

/** Koordináták színkódolt tengelyekkel (X piros, Y zöld, Z kék), nézési iránnyal és Nether/Felszín átszámítással. */
public final class CoordsElement extends HudElement {

    private static final int RX = 0xFFFF6B6B, RY = 0xFF6BE08A, RZ = 0xFF6BB6FF;

    public CoordsElement() {
        super("coords", "Koordináták");
    }

    @Override
    public int width(Minecraft mc) {
        return 150;
    }

    @Override
    public int height(Minecraft mc) {
        return 30;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        Player p = mc.player;
        int x = p == null ? 0 : (int) Math.floor(p.getX());
        int y = p == null ? 0 : (int) Math.floor(p.getY());
        int z = p == null ? 0 : (int) Math.floor(p.getZ());
        HudStyle.panel(g, 0, 0, 150, 30);
        HudStyle.accent(g, 0, 0, 30, 0xFF6BB6FF);

        int cx = 6;
        cx = axis(g, mc, cx, "X", x, RX);
        cx = axis(g, mc, cx, "Y", y, RY);
        axis(g, mc, cx, "Z", z, RZ);

        String dir = "-";
        if (p != null) {
            Direction d = p.getDirection();
            dir = switch (d) {
                case NORTH -> "▲ Észak  -Z";
                case SOUTH -> "▼ Dél  +Z";
                case EAST -> "▶ Kelet  +X";
                case WEST -> "◀ Nyugat  -X";
                default -> d.getName();
            };
        }
        HudStyle.text(g, mc.font, dir, 6, 18, HudStyle.MUTED, 0.75f);
        if (mc.level != null) {
            String dim = mc.level.dimension().location().getPath();
            String other = null;
            if (dim.equals("overworld")) other = "Nether " + (x >> 3) + " " + (z >> 3);
            else if (dim.equals("the_nether")) other = "Világ " + (x * 8) + " " + (z * 8);
            if (other != null) {
                float w = mc.font.width(other) * 0.75f;
                HudStyle.text(g, mc.font, other, 150 - 5 - w, 18, 0xFFC084FC, 0.75f);
            }
        }
    }

    private static int axis(GuiGraphics g, Minecraft mc, int x, String label, int value, int color) {
        // kis színes címke-doboz + érték
        int lw = mc.font.width(label) + 4;
        g.fill(x, 4, x + lw, 14, HudStyle.withAlpha(color, 0.25f));
        g.fill(x, 4, x + lw, 5, HudStyle.withAlpha(color, 0.6f));
        g.drawString(mc.font, label, x + 2, 5, color, true);
        String v = String.valueOf(value);
        g.drawString(mc.font, v, x + lw + 3, 5, HudStyle.TEXT, true);
        return x + lw + 3 + Math.max(mc.font.width(v), mc.font.width("-0000")) + 5;
    }
}
