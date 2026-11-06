package hu.nova.companion.hud;

import hu.nova.companion.fx.FxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Realisztikusabb állapotsávok a vanilla szívek / combok / páncél / buborékok helyett:
 * fényes, színátmenetes sávok simított animációval, „sebzés-csíkkal”, alacsony HP-nál pulzálással,
 * méreg / wither / fagyás színekkel, telítettség jelzéssel, és víz (szomjúság) sávval, ha van ilyen mod.
 * A HUD szerkesztőben mozgatható; ha kikapcsolod, visszajönnek a vanilla kijelzések.
 */
public final class VitalsElement extends HudElement {

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final int W = 182;
    private static final int ROW = 11;
    private static final int ROWS = 3;
    private static final int HALF = 89;

    // simított értékek
    private float shownHealth = -1, trailHealth = -1, trailDelay;
    private float shownFood = -1, shownThirst = -1, shownArmor = -1, shownAir = -1;
    private long lastNanos = System.nanoTime();
    private float time;

    public VitalsElement() {
        super("vitals", "Állapot sávok");
    }

    @Override
    public int width(Minecraft mc) {
        return W;
    }

    @Override
    public int height(Minecraft mc) {
        return ROW * ROWS;
    }

    @Override
    protected int reservedBottom() {
        return 30; // hotbar + XP sáv fölé
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    public static boolean replacesVanilla(Minecraft mc) {
        var c = hu.nova.companion.ClientBootstrap.CONFIG.element("vitals");
        return c.enabled && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        Player p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        time += dt;

        float health = p.getHealth();
        float maxHealth = Math.max(1f, p.getMaxHealth());
        float absorption = p.getAbsorptionAmount();
        int food = p.getFoodData().getFoodLevel();
        float saturation = p.getFoodData().getSaturationLevel();
        int armor = p.getArmorValue();
        int air = p.getAirSupply();
        int maxAir = Math.max(1, p.getMaxAirSupply());
        int thirst = ThirstCompat.thirst(p);
        boolean showAir = air < maxAir || p.isEyeInFluid(FluidTags.WATER);

        // --- simítás ---
        if (shownHealth < 0) { shownHealth = trailHealth = health; shownFood = food; shownArmor = armor; shownAir = air; shownThirst = thirst; }
        shownHealth = approach(shownHealth, health, dt * 30f);
        if (health < trailHealth) {
            trailDelay -= dt;
            if (trailDelay <= 0) trailHealth = approach(trailHealth, health, dt * 8f);
        } else {
            trailHealth = health;
            trailDelay = 0.6f;
        }
        if (health < shownHealth - 0.01f) trailDelay = Math.max(trailDelay, 0.6f);
        shownFood = approach(shownFood, food, dt * 20f);
        shownArmor = approach(shownArmor, armor, dt * 30f);
        shownAir = approach(shownAir, air, dt * 600f);
        shownThirst = thirst < 0 ? -1 : approach(Math.max(0, shownThirst), thirst, dt * 20f);

        int bottom = ROW * (ROWS - 1); // alsó sor teteje
        int rightX = W - HALF;

        // ----- életerő (bal alsó) -----
        float total = maxHealth + absorption;
        int[] hc = healthColors(p, mc);
        boolean low = health <= maxHealth * 0.3f;
        float pulse = low ? 0.5f + 0.5f * (float) Math.sin(time * 9f) : 0f;
        barFrame(g, 11, bottom + 1, HALF - 11, 7, low ? lerpColor(0xFF000000, 0xFF8A0000, pulse) : 0xFF000000);
        int innerW = HALF - 11;
        fillPart(g, 11, bottom + 1, innerW, 7, trailHealth / total, 0xFFFFE0E0, 0xFFC08080);   // sebzés csík
        fillPart(g, 11, bottom + 1, innerW, 7, shownHealth / total, hc[0], hc[1]);
        if (absorption > 0) {
            int from = Math.round(innerW * (shownHealth / total));
            int to = Math.round(innerW * ((shownHealth + absorption) / total));
            g.fillGradient(11 + from, bottom + 1, 11 + to, bottom + 8, 0xFFFFE066, 0xFFC89B00);
        }
        gloss(g, 11, bottom + 1, innerW, 7);
        icon(g, 0, bottom, 16, 0, 52, 0);
        label(g, mc, 11, bottom + 1, innerW, fmt(health / 2f) + (absorption > 0 ? " +" + fmt(absorption / 2f) : "") + " / " + fmt(maxHealth / 2f));

        // ----- éhség (jobb alsó) -----
        boolean hungerEffect = p.hasEffect(MobEffects.HUNGER);
        barFrame(g, rightX + 11, bottom + 1, HALF - 11, 7, 0xFF000000);
        fillPart(g, rightX + 11, bottom + 1, HALF - 11, 7, shownFood / 20f,
                hungerEffect ? 0xFFB5D46A : 0xFFF2B35A, hungerEffect ? 0xFF6E8A2A : 0xFFA35E12);
        // telítettség: vékony arany csík a sáv tetején
        fillPart(g, rightX + 11, bottom + 1, HALF - 11, 1, Math.min(saturation, 20f) / 20f, 0xFFFFF2A8, 0xFFFFF2A8);
        gloss(g, rightX + 11, bottom + 1, HALF - 11, 7);
        icon(g, rightX, bottom, 16, 27, hungerEffect ? 88 : 52, 27);
        label(g, mc, rightX + 11, bottom + 1, HALF - 11, food + " / 20");

        // ----- páncél (bal középső) -----
        if (armor > 0) {
            int y = bottom - ROW;
            barFrame(g, 11, y + 1, HALF - 11, 7, 0xFF000000);
            fillPart(g, 11, y + 1, HALF - 11, 7, shownArmor / 20f, 0xFFDCE4EE, 0xFF7C8898);
            gloss(g, 11, y + 1, HALF - 11, 7);
            icon(g, 0, y, -1, -1, 34, 9);
            label(g, mc, 11, y + 1, HALF - 11, armor + " / 20");
        }

        // ----- víz / szomjúság és levegő (jobb oldalon felfelé) -----
        int rowY = bottom - ROW;
        if (shownThirst >= 0) {
            barFrame(g, rightX + 11, rowY + 1, HALF - 11, 7, 0xFF000000);
            fillPart(g, rightX + 11, rowY + 1, HALF - 11, 7, shownThirst / 20f, 0xFF6EC8FF, 0xFF1666C8);
            gloss(g, rightX + 11, rowY + 1, HALF - 11, 7);
            dropIcon(g, mc, rightX + 1, rowY);
            label(g, mc, rightX + 11, rowY + 1, HALF - 11, thirst + " / 20");
            rowY -= ROW;
        }
        if (showAir) {
            barFrame(g, rightX + 11, rowY + 1, HALF - 11, 7, 0xFF000000);
            fillPart(g, rightX + 11, rowY + 1, HALF - 11, 7, Math.max(0, shownAir) / maxAir, 0xFFB8F4FF, 0xFF3AA8C8);
            gloss(g, rightX + 11, rowY + 1, HALF - 11, 7);
            icon(g, rightX, rowY, -1, -1, 16, 18);
            label(g, mc, rightX + 11, rowY + 1, HALF - 11, Math.max(0, Math.round(air / 20f)) + " mp");
        }
    }

    private static int[] healthColors(Player p, Minecraft mc) {
        if (p.hasEffect(MobEffects.WITHER)) return new int[]{0xFF5A5A5A, 0xFF1E1E1E};
        if (p.hasEffect(MobEffects.POISON)) return new int[]{0xFF9BD34A, 0xFF4A7A12};
        if (p.isFullyFrozen() || p.getTicksFrozen() > 0) return new int[]{0xFFBEE8FF, 0xFF5A9CC8};
        if (p.hasEffect(MobEffects.REGENERATION)) return new int[]{0xFFFF7A9A, 0xFFC0184A};
        return new int[]{0xFFFF5A4A, 0xFFA81414};
    }

    private static void barFrame(GuiGraphics g, int x, int y, int w, int h, int border) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, border | 0xFF000000);
        g.fillGradient(x, y, x + w, y + h, 0xC0201818, 0xC0080606);
    }

    private static void fillPart(GuiGraphics g, int x, int y, int w, int h, float frac, int top, int bottom) {
        int fw = Math.round(w * Mth.clamp(frac, 0f, 1f));
        if (fw > 0) g.fillGradient(x, y, x + fw, y + h, top, bottom);
    }

    /** fényes felső él + sötétebb alsó él = „üveges” hatás */
    private static void gloss(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, 0x50FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x30000000);
    }

    private static void icon(GuiGraphics g, int x, int y, int bgU, int bgV, int u, int v) {
        if (bgU >= 0) g.blit(ICONS, x, y, bgU, bgV, 9, 9);
        g.blit(ICONS, x, y, u, v, 9, 9);
    }

    private static void dropIcon(GuiGraphics g, Minecraft mc, int x, int y) {
        FxTextures.ensure(mc);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(0.35f, 0.65f, 1f, 1f);
        g.pose().pushPose();
        g.pose().translate(x, y - 1, 0);
        g.pose().scale(7f / FxTextures.DROP_W * 1.2f, 10f / FxTextures.DROP_H * 1.1f, 1f);
        g.blit(FxTextures.DROP, 0, 0, 0, 0, FxTextures.DROP_W, FxTextures.DROP_H, FxTextures.DROP_W, FxTextures.DROP_H);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void label(GuiGraphics g, Minecraft mc, int x, int y, int w, String text) {
        float s = 0.62f;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - mc.font.width(text) * s / 2f, y + 1.2f, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static String fmt(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format("%.1f", v);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static float approach(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }
}
