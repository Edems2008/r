package hu.nova.companion.hud;

import hu.nova.companion.ClientBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public final class HudManager {

    public static final List<HudElement> ELEMENTS = List.of(
            new CompassElement(),
            new FpsElement(),
            new CoordsElement(),
            new ClockElement(),
            new RealClockElement(),
            new VitalsElement(),
            new WeatherElement(),
            new NightVisionElement()
    );

    private HudManager() {}

    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) return;
        if (mc.screen instanceof HudEditorScreen) return; // a szerkesztő maga rajzolja
        // gyorsaság: megnyúló HUD, ugrásfokozó: felfelé lebegő HUD
        HudMotion.update(mc);
        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(HudMotion.stretchX(), HudMotion.stretchY(), 1f);
        g.pose().translate(-width / 2f, -height / 2f + HudMotion.offsetY(), 0);
        for (HudElement e : ELEMENTS) {
            var c = ClientBootstrap.CONFIG.element(e.id);
            if (c.enabled && e.shouldRender(mc)) e.render(g, mc, c, width, height);
        }
        g.pose().popPose();
    }
}
