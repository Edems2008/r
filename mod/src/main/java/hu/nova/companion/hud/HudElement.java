package hu.nova.companion.hud;

import hu.nova.companion.config.CompanionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Egy mozgatható HUD elem. A pozíció a szabad képernyőterület arányában van tárolva. */
public abstract class HudElement {

    public final String id;
    public final String label;

    protected HudElement(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public abstract int width(Minecraft mc);

    public abstract int height(Minecraft mc);

    /** Rajzolás 1x-es léptékben, (0,0) a bal felső sarok. */
    protected abstract void draw(GuiGraphics g, Minecraft mc);

    public int screenX(CompanionConfig.Element c, Minecraft mc, int screenW) {
        int w = Math.round(width(mc) * c.scale);
        return (int) Math.round(c.x * Math.max(0, screenW - w));
    }

    public int screenY(CompanionConfig.Element c, Minecraft mc, int screenH) {
        int h = Math.round(height(mc) * c.scale);
        return (int) Math.round(c.y * Math.max(0, screenH - h));
    }

    public void render(GuiGraphics g, Minecraft mc, CompanionConfig.Element c, int screenW, int screenH) {
        int x = screenX(c, mc, screenW);
        int y = screenY(c, mc, screenH);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(c.scale, c.scale, 1f);
        draw(g, mc);
        g.pose().popPose();
    }
}
