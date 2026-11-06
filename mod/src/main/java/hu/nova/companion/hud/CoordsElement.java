package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

/** Koordináták + nézési irány + (Overworldben) a Nether megfelelő koordinátái. */
public final class CoordsElement extends HudElement {

    public CoordsElement() {
        super("coords", "Koordináták");
    }

    private String[] lines(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return new String[]{"XYZ: -", ""};
        int x = (int) Math.floor(p.getX());
        int y = (int) Math.floor(p.getY());
        int z = (int) Math.floor(p.getZ());
        Direction d = p.getDirection();
        String dir = switch (d) {
            case NORTH -> "Észak (-Z)";
            case SOUTH -> "Dél (+Z)";
            case EAST -> "Kelet (+X)";
            case WEST -> "Nyugat (-X)";
            default -> d.getName();
        };
        String second = dir;
        if (mc.level != null) {
            String dim = mc.level.dimension().location().getPath();
            if (dim.equals("overworld")) second += "  Nether: " + (x / 8) + " " + (z / 8);
            else if (dim.equals("the_nether")) second += "  Világ: " + (x * 8) + " " + (z * 8);
        }
        return new String[]{"XYZ: " + x + " / " + y + " / " + z, second};
    }

    @Override
    public int width(Minecraft mc) {
        int w = mc.font.width("XYZ: -00000 / 000 / -00000");
        for (String s : lines(mc)) w = Math.max(w, mc.font.width(s));
        return w + 8;
    }

    @Override
    public int height(Minecraft mc) {
        return mc.font.lineHeight * 2 + 8;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        String[] l = lines(mc);
        g.fill(0, 0, width(mc), height(mc), 0x90000000);
        g.drawString(mc.font, l[0], 4, 3, 0xFFFFFFFF, true);
        g.drawString(mc.font, l[1], 4, 5 + mc.font.lineHeight, 0xFFAAAAAA, true);
    }
}
