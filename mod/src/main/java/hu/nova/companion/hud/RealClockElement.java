package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Valós (a gép szerinti) idő HUD elemként – alapból kikapcsolva, a szerkesztőben jobb klikkel bekapcsolható. */
public final class RealClockElement extends HudElement {

    static final Locale HU = Locale.forLanguageTag("hu-HU");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", HU);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy. MM. dd., EEEE", HU);

    public RealClockElement() {
        super("realclock", "Valós idő");
    }

    private String text() {
        return "Valós: " + LocalDateTime.now().format(TIME);
    }

    @Override
    public int width(Minecraft mc) {
        return mc.font.width("Valós: 00:00:00") + 8;
    }

    @Override
    public int height(Minecraft mc) {
        return mc.font.lineHeight + 6;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        g.fill(0, 0, width(mc), height(mc), 0x90000000);
        g.drawString(mc.font, text(), 4, 3, 0xFFFFFFFF, true);
    }

    /** Az Esc (szünet) menü jobb felső sarkába rajzolt valós óra. */
    public static void drawPauseClock(GuiGraphics g, Minecraft mc, int screenW) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(TIME);
        String date = now.format(DATE);
        String label = "Valós idő";
        int w = Math.max(mc.font.width(date), mc.font.width(time) * 2) + 16;
        int x = screenW - w - 8;
        int y = 8;
        int h = 12 + mc.font.lineHeight * 4;
        g.fill(x, y, x + w, y + h, 0xA0000000);
        g.fill(x, y, x + w, y + 1, 0xFF4ADE80);
        g.drawString(mc.font, label, x + 8, y + 5, 0xFF8A96A6, true);
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 7 + mc.font.lineHeight, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(mc.font, time, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        g.drawString(mc.font, date, x + 8, y + 9 + mc.font.lineHeight * 3, 0xFFCCCCCC, true);

        // a Minecraft-idő is látszódjon itt
        long[] gt = ClockElement.gameTime(mc);
        String mcTime = String.format("Minecraft: %02d:%02d • %d. nap", gt[0], gt[1], gt[2]);
        int mw = mc.font.width(mcTime) + 16;
        g.fill(screenW - mw - 8, y + h + 4, screenW - 8, y + h + 8 + mc.font.lineHeight + 4, 0xA0000000);
        g.drawString(mc.font, mcTime, screenW - mw, y + h + 8, 0xFFFFE27A, true);
    }
}
