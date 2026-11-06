package hu.nova.companion.hud;

import hu.nova.companion.fx.FxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
        int barW = HALF - 12;
        FxTextures.ensure(mc);

        // ----- életerő (bal alsó) -----
        float total = maxHealth + absorption;
        int[] hc = healthColors(p);
        boolean low = health <= maxHealth * 0.3f;
        float pulse = low ? 0.5f + 0.5f * (float) Math.sin(time * 9f) : 0f;
        if (low) g.fill(10, bottom, 12 + barW + 1, bottom + 9, HudStyle.withAlpha(0xFFFF2020, 0.35f * pulse));
        HudStyle.barBackground(g, 12, bottom + 1, barW, 7);
        // sebzés-csík (világos), majd a valódi érték
        int trailW = Math.round(barW * Mth.clamp(trailHealth / total, 0f, 1f));
        if (trailW > 0) g.fillGradient(12, bottom + 1, 12 + trailW, bottom + 8, 0xFFFFE4E4, 0xFFD89A9A);
        HudStyle.barFill(g, 12, bottom + 1, barW, 7, shownHealth / total, hc[0], hc[1]);
        if (absorption > 0) {
            int from = Math.round(barW * (shownHealth / total));
            int to = Math.round(barW * Math.min(1f, (shownHealth + absorption) / total));
            g.fillGradient(12 + from, bottom + 1, 12 + to, bottom + 8, 0xFFFFE680, 0xFFC99A00);
            g.fill(12 + from, bottom + 1, 12 + to, bottom + 2, 0x70FFFFFF);
        }
        HudStyle.barSegments(g, 12, bottom + 1, barW, 7, 10);
        // a szív ikon alacsony HP-nál „dobog”
        float beat = low ? 1f + 0.18f * Math.max(0f, (float) Math.sin(time * 9f)) : 1f;
        float hs = 10f * beat;
        HudStyle.icon(g, FxTextures.HEART, FxTextures.SMALL, FxTextures.SMALL, 5 - hs / 2f, bottom + 4.5f - hs / 2f, hs, hs);
        label(g, mc, 12, bottom + 1, barW, fmt(health / 2f) + (absorption > 0 ? " +" + fmt(absorption / 2f) : "") + " / " + fmt(maxHealth / 2f));

        // ----- éhség (jobb alsó) -----
        boolean hungerEffect = p.hasEffect(MobEffects.HUNGER);
        HudStyle.bar(g, rightX + 12, bottom + 1, barW, 7, shownFood / 20f,
                hungerEffect ? 0xFFC4E07A : 0xFFF7BE6A, hungerEffect ? 0xFF6A8A26 : 0xFFA4570E, 10);
        // telítettség: arany csík a sáv tetején
        int satW = Math.round(barW * Math.min(saturation, 20f) / 20f);
        if (satW > 0) g.fill(rightX + 12, bottom + 1, rightX + 12 + satW, bottom + 2, 0xFFFFF0A0);
        HudStyle.icon(g, FxTextures.FOOD, FxTextures.SMALL, FxTextures.SMALL, rightX, bottom - 0.5f, 10, 10);
        label(g, mc, rightX + 12, bottom + 1, barW, food + " / 20");

        // ----- páncél (bal középső) -----
        if (armor > 0) {
            int y = bottom - ROW;
            HudStyle.bar(g, 12, y + 1, barW, 7, shownArmor / 20f, 0xFFE8EEF5, 0xFF808C9C, 10);
            HudStyle.icon(g, FxTextures.ARMOR, FxTextures.SMALL, FxTextures.SMALL, 0, y - 0.5f, 10, 10);
            label(g, mc, 12, y + 1, barW, armor + " / 20");
        }

        // ----- víz / szomjúság és levegő (jobb oldalon felfelé) -----
        int rowY = bottom - ROW;
        if (shownThirst >= 0) {
            HudStyle.bar(g, rightX + 12, rowY + 1, barW, 7, shownThirst / 20f, 0xFF7FD0FF, 0xFF1462C4, 10);
            HudStyle.icon(g, FxTextures.WATER, FxTextures.SMALL, FxTextures.SMALL, rightX, rowY - 0.5f, 10, 10);
            label(g, mc, rightX + 12, rowY + 1, barW, thirst + " / 20");
            rowY -= ROW;
        }
        if (showAir) {
            HudStyle.bar(g, rightX + 12, rowY + 1, barW, 7, Math.max(0, shownAir) / maxAir, 0xFFC8F6FF, 0xFF3AA2C4, 10);
            HudStyle.icon(g, FxTextures.BUBBLE, FxTextures.SMALL, FxTextures.SMALL, rightX, rowY - 0.5f, 10, 10);
            label(g, mc, rightX + 12, rowY + 1, barW, Math.max(0, Math.round(air / 20f)) + " mp");
        }
    }

    private static int[] healthColors(Player p) {
        if (p.hasEffect(MobEffects.WITHER)) return new int[]{0xFF5A5A5A, 0xFF1E1E1E};
        if (p.hasEffect(MobEffects.POISON)) return new int[]{0xFF9BD34A, 0xFF4A7A12};
        if (p.isFullyFrozen() || p.getTicksFrozen() > 0) return new int[]{0xFFBEE8FF, 0xFF5A9CC8};
        if (p.hasEffect(MobEffects.REGENERATION)) return new int[]{0xFFFF7A9A, 0xFFC0184A};
        return new int[]{0xFFFF5A4A, 0xFFA81414};
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
