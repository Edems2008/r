package hu.nova.companion.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import hu.nova.companion.fx.FxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Éjjellátó szemüveg ikon (alapból jobb felül), amíg rajtad van az éjjellátás. A lencsék pulzálnak, a végén villognak. */
public final class NightVisionElement extends HudElement {

    public NightVisionElement() {
        super("nightvision", "Éjjellátó szemüveg");
    }

    @Override
    public int width(Minecraft mc) {
        return 48;
    }

    @Override
    public int height(Minecraft mc) {
        return 36;
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.player != null && mc.player.hasEffect(MobEffects.NIGHT_VISION);
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        MobEffectInstance e = mc.player != null ? mc.player.getEffect(MobEffects.NIGHT_VISION) : null;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int remaining = e == null ? -1 : e.getDuration();
        // utolsó 10 másodperc: villog (mint a vanilla éjjellátás vége)
        if (remaining >= 0 && remaining < 200 && (remaining / 5) % 2 == 0) pulse *= 0.35f;

        FxTextures.ensure(mc);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().scale(48f / FxTextures.GOGGLES_W, 24f / FxTextures.GOGGLES_H, 1f);
        // halvány zöld fénykör a szemüveg mögött
        g.setColor(0.3f, 1f, 0.45f, 0.25f * pulse);
        g.blit(FxTextures.GLOW, -16, -24, 0, 0, 160, 112, 160, 112);
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(FxTextures.GOGGLES, 0, 0, 0, 0, FxTextures.GOGGLES_W, FxTextures.GOGGLES_H, FxTextures.GOGGLES_W, FxTextures.GOGGLES_H);
        // izzás a lencséken
        g.setColor(0.3f, 1f, 0.4f, 0.6f * pulse);
        for (int lx : new int[]{34, 94}) {
            g.blit(FxTextures.GLOW, lx - 24, 8, 0, 0, 48, 48, 48, 48);
        }
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
        RenderSystem.disableBlend();
        if (e != null && !e.isInfiniteDuration()) {
            int sec = remaining / 20;
            String t = String.format("%d:%02d", sec / 60, sec % 60);
            int tw = mc.font.width(t) + 8;
            HudStyle.panel(g, 24 - tw / 2, 25, tw, 10);
            HudStyle.text(g, mc.font, t, 24 - mc.font.width(t) * 0.8f / 2f, 26.5f, 0xFF7CFF8A, 0.8f);
        }
    }
}
