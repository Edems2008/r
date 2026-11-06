package hu.nova.companion.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import hu.nova.companion.ClientBootstrap;
import hu.nova.companion.config.CompanionConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Képernyő-effektek (csak első személyű nézetben):
 *  - vízből kijőve vízcseppek, amik egy ideig megtapadnak, majd csíkot húzva lefolynak
 *  - eső esetén időnként egy-egy csepp
 *  - tűzben / lávában izzó, vibráló hő-vignetta és felszálló parázs, ami kijutás után lassan elhalványul
 *  - víz alatt enyhe kékes szín
 */
public final class ScreenEffects {

    private static final class Drop {
        float x, y, r;
        float stick;      // ennyi ideig tapad, mielőtt elindul
        float vy;
        float life, maxLife;
        float wobblePhase;
        final float[] trailX = new float[14];
        final float[] trailY = new float[14];
        int trailLen;
        float lastTrailY;
    }

    private static final class Ember {
        float x, y, vx, vy, life, maxLife, size;
    }

    private final List<Drop> drops = new ArrayList<>();
    private final List<Ember> embers = new ArrayList<>();
    private final Random rnd = new Random();
    private long lastNanos = System.nanoTime();
    private boolean wasEyeInWater;
    private float underwater;
    private float heat;
    private float time;

    public void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (p == null || mc.level == null) return;
        if (mc.isPaused()) dt = 0f;
        time += dt;

        CompanionConfig cfg = ClientBootstrap.CONFIG;
        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        boolean eyeInWater = p.isEyeInFluid(FluidTags.WATER);
        boolean inLava = p.isInLava();
        boolean burning = p.isOnFire() || inLava;

        // --- állapotváltozások ---
        if (wasEyeInWater && !eyeInWater && cfg.waterDrops) spawnDrops(w, h, 34 + rnd.nextInt(14), true);
        if (eyeInWater || inLava) drops.clear(); // víz alatt / lávában nincsenek tapadó cseppek
        wasEyeInWater = eyeInWater;

        if (cfg.rainDrops && !eyeInWater && dt > 0) {
            BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
            if (mc.level.isRainingAt(head) && rnd.nextFloat() < dt * 2.5f) spawnDrops(w, h, 1, false);
        }

        underwater = approach(underwater, eyeInWater && cfg.underwaterTint ? 1f : 0f, dt * 3f);
        float heatTarget = !cfg.fireEffect ? 0f : inLava ? 1.25f : burning ? 1f : 0f;
        heat = approach(heat, heatTarget, dt * (heatTarget > heat ? 5f : 0.6f));

        if (!firstPerson) {
            drops.clear();
            embers.clear();
            return;
        }

        FxTextures.ensure(mc);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        if (underwater > 0.01f) {
            g.setColor(0.05f, 0.30f, 0.55f, 0.55f * underwater);
            g.blit(FxTextures.VIGNETTE, 0, 0, w, h, 0, 0, FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE,
                    FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE);
        }

        if (heat > 0.01f) renderHeat(g, w, h, dt);
        else embers.clear();

        if (!drops.isEmpty()) renderDrops(g, w, h, dt);

        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ víz

    private void spawnDrops(int w, int h, int count, boolean fromWater) {
        for (int i = 0; i < count && drops.size() < 90; i++) {
            Drop d = new Drop();
            d.x = rnd.nextFloat() * w;
            d.y = fromWater ? rnd.nextFloat() * h : rnd.nextFloat() * h * 0.7f;
            d.r = fromWater ? 2.2f + rnd.nextFloat() * rnd.nextFloat() * 6.5f : 2.0f + rnd.nextFloat() * 3.5f;
            d.stick = fromWater ? rnd.nextFloat() * 1.8f : 0.3f + rnd.nextFloat() * 1.5f;
            d.maxLife = d.life = d.stick + 2.5f + rnd.nextFloat() * 3.5f;
            d.wobblePhase = rnd.nextFloat() * 6.28f;
            d.lastTrailY = d.y;
            drops.add(d);
        }
    }

    private void renderDrops(GuiGraphics g, int w, int h, float dt) {
        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.life -= dt;
            if (d.stick > 0) {
                d.stick -= dt;
                // a kis cseppek tapadva maradnak és csak elpárolognak
            } else if (d.r > 3.2f) {
                // nagyobb cseppek gyorsulva lefolynak, kicsit kanyarogva
                d.vy = Math.min(d.vy + (25f + d.r * 12f) * dt, 45f + d.r * 18f);
                d.y += d.vy * dt;
                d.x += (float) Math.sin(time * 2.3f + d.wobblePhase) * 6f * dt;
                d.r = Math.max(2.4f, d.r - dt * 0.35f); // útközben fogy
                if (d.y - d.lastTrailY > 2.5f) {
                    // nyomvonal pont rögzítése
                    System.arraycopy(d.trailX, 0, d.trailX, 1, d.trailX.length - 1);
                    System.arraycopy(d.trailY, 0, d.trailY, 1, d.trailY.length - 1);
                    d.trailX[0] = d.x;
                    d.trailY[0] = d.y;
                    d.trailLen = Math.min(d.trailLen + 1, d.trailX.length);
                    d.lastTrailY = d.y;
                }
            }
            if (d.life <= 0 || d.y - d.r * 2 > h) {
                it.remove();
                continue;
            }
            float fade = Math.min(1f, d.life / 1.2f);

            // a lefolyt csepp nedves csíkja
            for (int i = 0; i < d.trailLen; i++) {
                float k = 1f - (float) i / d.trailLen;
                drawDrop(g, d.trailX[i], d.trailY[i] - d.r * 0.6f, d.r * (0.35f + 0.25f * k), 0.45f * k * fade);
            }
            drawDrop(g, d.x, d.y, d.r, fade);
        }
    }

    private static void drawDrop(GuiGraphics g, float x, float y, float r, float alpha) {
        if (alpha <= 0.01f) return;
        float s = r / 12f; // a textúra csepp-sugara 12 px
        g.setColor(1f, 1f, 1f, Math.min(1f, alpha));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1f);
        g.blit(FxTextures.DROP, -16, -24, 0, 0, FxTextures.DROP_W, FxTextures.DROP_H, FxTextures.DROP_W, FxTextures.DROP_H);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ tűz

    private void renderHeat(GuiGraphics g, int w, int h, float dt) {
        float flicker = 0.80f + 0.12f * (float) Math.sin(time * 13.0) + 0.08f * (float) Math.sin(time * 31.7 + 1.3);
        float a = Math.min(1f, heat) * flicker;

        // izzó szélek
        g.setColor(1.0f, 0.38f, 0.06f, 0.75f * a);
        g.blit(FxTextures.VIGNETTE, 0, 0, w, h, 0, 0, FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE,
                FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE);
        // forróbb, sárgás belső réteg erősebb hőnél (láva)
        if (heat > 1f) {
            g.setColor(1.0f, 0.75f, 0.2f, 0.5f * (heat - 1f) * 4f * flicker);
            g.blit(FxTextures.VIGNETTE, -w / 8, -h / 8, w + w / 4, h + h / 4, 0, 0, FxTextures.VIGNETTE_SIZE,
                    FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE);
        }
        g.setColor(1f, 1f, 1f, 1f);
        // alulról felcsapó hő
        int top = (int) (h * (0.55f + 0.1f * (1f - flicker)));
        int alpha = (int) (110 * a) & 0xff;
        g.fillGradient(0, top, w, h, 0x00FF5000, (alpha << 24) | 0xFF4A00);

        // parázs
        if (heat > 0.3f && rnd.nextFloat() < dt * 40f * Math.min(1f, heat)) {
            Ember e = new Ember();
            e.x = rnd.nextFloat() * w;
            e.y = h + 4;
            e.vx = (rnd.nextFloat() - 0.5f) * 30f;
            e.vy = -(40f + rnd.nextFloat() * 90f);
            e.maxLife = e.life = 0.8f + rnd.nextFloat() * 1.6f;
            e.size = 1.5f + rnd.nextFloat() * 3f;
            embers.add(e);
        }
        Iterator<Ember> it = embers.iterator();
        while (it.hasNext()) {
            Ember e = it.next();
            e.life -= dt;
            e.x += e.vx * dt + (float) Math.sin(time * 5f + e.size * 3f) * 12f * dt;
            e.y += e.vy * dt;
            if (e.life <= 0) {
                it.remove();
                continue;
            }
            float k = e.life / e.maxLife;
            g.setColor(1f, 0.55f + 0.35f * k, 0.15f, Math.min(1f, k * 1.4f) * Math.min(1f, heat));
            float s = e.size * (0.6f + 0.4f * k) * 2f;
            g.pose().pushPose();
            g.pose().translate(e.x - s / 2f, e.y - s / 2f, 0);
            g.pose().scale(s / FxTextures.GLOW_SIZE, s / FxTextures.GLOW_SIZE, 1f);
            g.blit(FxTextures.GLOW, 0, 0, 0, 0, FxTextures.GLOW_SIZE, FxTextures.GLOW_SIZE, FxTextures.GLOW_SIZE, FxTextures.GLOW_SIZE);
            g.pose().popPose();
        }
    }

    private static float approach(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }
}
