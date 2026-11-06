package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Valós FPS számláló (a Minecraft saját mért értéke), színezve. */
public final class FpsElement extends HudElement {

    public FpsElement() {
        super("fps", "FPS");
    }

    private String text(Minecraft mc) {
        return mc.getFps() + " FPS";
    }

    @Override
    public int width(Minecraft mc) {
        return Math.max(mc.font.width("000 FPS"), mc.font.width(text(mc))) + 8;
    }

    @Override
    public int height(Minecraft mc) {
        return mc.font.lineHeight + 6;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        int fps = mc.getFps();
        int color = fps >= 60 ? 0xFF55FF55 : fps >= 30 ? 0xFFFFFF55 : 0xFFFF5555;
        g.fill(0, 0, width(mc), height(mc), 0x90000000);
        g.drawString(mc.font, text(mc), 4, 3, color, true);
    }
}
