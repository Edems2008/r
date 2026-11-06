package hu.nova.companion.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Minecraft-beli óra + napszámláló.
 * A dayTime értéket a szerver szinkronizálja a kliensre, így szerveren játszva is pontos.
 * (dayTime 0 = reggel 6:00, egy nap = 24000 tick)
 */
public final class ClockElement extends HudElement {

    public ClockElement() {
        super("clock", "Minecraft óra");
    }

    /** @return {óra, perc, hányadik nap (1-től), eltelt teljes napok} */
    public static long[] gameTime(Minecraft mc) {
        long dayTime = mc.level != null ? mc.level.getDayTime() : 0L;
        long daysPassed = dayTime / 24000L;
        long t = dayTime % 24000L;
        long hour = (t / 1000L + 6L) % 24L;
        long minute = (t % 1000L) * 60L / 1000L;
        return new long[]{hour, minute, daysPassed + 1, daysPassed};
    }

    private String[] lines(Minecraft mc) {
        long[] gt = gameTime(mc);
        boolean day = gt[0] >= 6 && gt[0] < 18;
        String icon = day ? "☀" : "☾";
        String time = String.format("%s %02d:%02d", icon, gt[0], gt[1]);
        return new String[]{time + "   " + gt[2] + ". nap", "Eltelt: " + gt[3] + " nap"};
    }

    @Override
    public int width(Minecraft mc) {
        int w = mc.font.width("☀ 00:00   0000. nap");
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
        long hour = gameTime(mc)[0];
        int timeColor = (hour >= 6 && hour < 18) ? 0xFFFFE27A : 0xFF9DB8FF;
        g.fill(0, 0, width(mc), height(mc), 0x90000000);
        g.drawString(mc.font, l[0], 4, 3, timeColor, true);
        g.drawString(mc.font, l[1], 4, 5 + mc.font.lineHeight, 0xFFAAAAAA, true);
    }
}
