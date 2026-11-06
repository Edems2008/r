package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Valós (a gép szerinti) idő – HUD elemként (alapból ki) és az Esc menüben. */
public final class RealClockElement extends HudElement {

    static final Locale HU = Locale.forLanguageTag("hu-HU");
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", HU);
    static final DateTimeFormatter SEC = DateTimeFormatter.ofPattern("ss", HU);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy. MMMM d., EEEE", HU);

    public RealClockElement() {
        super("realclock", "Valós idő");
    }

    @Override
    public int width(Minecraft mc) {
        return 62;
    }

    @Override
    public int height(Minecraft mc) {
        return 16;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        LocalDateTime now = LocalDateTime.now();
        HudStyle.panel(g, 0, 0, 62, 16);
        HudStyle.accent(g, 0, 0, 16, 0xFFE5E7EB);
        String hm = now.format(HM);
        g.drawString(mc.font, hm, 6, 4, HudStyle.TEXT, true);
        HudStyle.text(g, mc.font, ":" + now.format(SEC), 6 + mc.font.width(hm), 5.5f, HudStyle.MUTED, 0.75f);
        HudStyle.text(g, mc.font, "IRL", 62 - 5 - mc.font.width("IRL") * 0.6f, 6, 0xFF7A8696, 0.6f);
    }

    /** Az Esc (szünet) menü jobb felső sarkába rajzolt valós óra + Minecraft-idő. */
    public static void drawPauseClock(GuiGraphics g, Minecraft mc, int screenW) {
        LocalDateTime now = LocalDateTime.now();
        String hm = now.format(HM);
        String sec = now.format(SEC);
        String date = now.format(DATE);
        date = date.substring(0, 1).toUpperCase(HU) + date.substring(1);
        int w = Math.max(mc.font.width(date) + 16, (int) (mc.font.width(hm) * 2.5f + mc.font.width(sec) * 1.2f) + 22);
        int h = 54;
        int x = screenW - w - 10, y = 10;
        HudStyle.panel(g, x, y, w, h);
        g.fill(x + 1, y, x + w - 1, y + 1, 0xFF4ADE80);
        HudStyle.text(g, mc.font, "VALÓS IDŐ", x + 8, y + 6, 0xFF7FE3A5, 0.7f);
        HudStyle.text(g, mc.font, hm, x + 8, y + 15, 0xFFFFFFFF, 2.5f);
        HudStyle.text(g, mc.font, sec, x + 10 + mc.font.width(hm) * 2.5f, y + 23, HudStyle.MUTED, 1.2f);
        HudStyle.text(g, mc.font, date, x + 8, y + 42, 0xFFC9D2DD, 0.85f);

        long[] gt = ClockElement.gameTime(mc);
        String mcTime = String.format("Minecraft  %02d:%02d  •  %d. nap", gt[0], gt[1], gt[2]);
        int mw = (int) (mc.font.width(mcTime) * 0.85f) + 14;
        HudStyle.panel(g, screenW - mw - 10, y + h + 5, mw, 14);
        HudStyle.text(g, mc.font, mcTime, screenW - mw - 3, y + h + 9, 0xFFFFE7A0, 0.85f);
    }
}
