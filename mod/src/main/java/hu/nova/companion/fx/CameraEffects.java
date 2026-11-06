package hu.nova.companion.fx;

import hu.nova.companion.ClientBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ViewportEvent;

/** Méreg effekt: szédülés – a kamera lassan imbolyog és dől (csak vizuális, a célzást nem befolyásolja). */
public final class CameraEffects {

    private static float intensity = 0f;
    private static long last = System.nanoTime();

    private CameraEffects() {}

    public static float dizziness() {
        return intensity;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        if (p == null) return;

        MobEffectInstance poison = p.getEffect(MobEffects.POISON);
        float target = (ClientBootstrap.CONFIG.poisonDizzy && poison != null) ? Math.min(1.6f, 0.7f + 0.3f * poison.getAmplifier()) : 0f;
        if (!mc.isPaused()) intensity += (target - intensity) * Math.min(1f, dt * (target > intensity ? 0.8f : 1.5f));
        if (intensity < 0.001f) return;

        double t = p.tickCount + event.getPartialTick();
        float roll = (float) (Math.sin(t * 0.075) * 7.0 + Math.sin(t * 0.191) * 2.5) * intensity;
        float yaw = (float) (Math.sin(t * 0.053) * 2.2 + Math.sin(t * 0.137) * 0.8) * intensity;
        float pitch = (float) (Math.sin(t * 0.067 + 1.7) * 1.8) * intensity;
        event.setRoll(event.getRoll() + roll);
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
    }
}
