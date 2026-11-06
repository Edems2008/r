package hu.nova.companion.hud;

import hu.nova.companion.ClientBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Az egész HUD közös mozgása:
 *  - Gyorsaság effekt + futás: a HUD vízszintesen megnyúlik (erősebb effektnél jobban), mintha „húzna” a sebesség
 *  - Ugrásfokozó effekt: ugráskor a HUD felfelé lebeg, majd rugalmasan visszaáll
 */
public final class HudMotion {

    private static float stretch = 1f;
    private static float offset = 0f;
    private static float offsetVel = 0f;
    private static long last = System.nanoTime();

    private HudMotion() {}

    public static void update(Minecraft mc) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        Player p = mc.player;
        if (p == null || mc.isPaused()) return;
        var cfg = ClientBootstrap.CONFIG;

        // --- gyorsaság ---
        float target = 1f;
        MobEffectInstance speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
        if (cfg.speedStretch && speed != null && p.isSprinting()) {
            target = 1f + Math.min(0.22f, 0.07f * (speed.getAmplifier() + 1));
            target += 0.012f * (float) Math.sin(System.currentTimeMillis() / 70.0); // enyhe remegés
        }
        stretch += (target - stretch) * Math.min(1f, dt * 8f);

        // --- ugrásfokozó: rugó-csillapító modell ---
        float targetOffset = 0f;
        MobEffectInstance jump = p.getEffect(MobEffects.JUMP);
        if (cfg.jumpFloat && jump != null && !p.onGround()) {
            double vy = p.getDeltaMovement().y;
            if (vy > 0) targetOffset = -(float) Math.min(30.0, vy * 45.0 * (1 + 0.3 * jump.getAmplifier()));
            else targetOffset = -(float) Math.max(0.0, 8.0 + vy * 10.0);
        }
        float k = 60f, damping = 9f;
        float acc = k * (targetOffset - offset) - damping * offsetVel;
        offsetVel += acc * dt;
        offset += offsetVel * dt;
        if (Math.abs(offset) < 0.05f && Math.abs(offsetVel) < 0.05f && targetOffset == 0f) offset = offsetVel = 0f;
    }

    public static float stretchX() {
        return stretch;
    }

    /** megnyúláskor függőlegesen kicsit összenyomódik (mint egy rajzfilmben) */
    public static float stretchY() {
        return 1f - (stretch - 1f) * 0.25f;
    }

    public static float offsetY() {
        return offset;
    }

    public static float speedAmount() {
        return Math.max(0f, (stretch - 1f) / 0.22f);
    }
}
