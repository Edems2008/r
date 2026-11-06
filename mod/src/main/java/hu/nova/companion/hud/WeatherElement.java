package hu.nova.companion.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import hu.nova.companion.fx.FxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Minecraft-beli időjárás ikonnal: derült (nap / hold), eső, havazás, vihar,
 * illetve „felhős”, ha esik, de a biomban nincs csapadék (pl. sivatag).
 * Az időjárást a szerver szinkronizálja, így szerveren is pontos.
 */
public final class WeatherElement extends HudElement {

    public enum Kind {
        CLEAR_DAY("Derült", FxTextures.SUN, 0xFFFFE27A),
        CLEAR_NIGHT("Derült éjszaka", FxTextures.MOON, 0xFFDDE6FF),
        CLOUDY("Felhős", FxTextures.CLOUD, 0xFFD0D6DE),
        RAIN("Eső", FxTextures.RAIN, 0xFF7EC4FF),
        SNOW("Havazás", FxTextures.SNOW, 0xFFFFFFFF),
        THUNDER("Vihar", FxTextures.STORM, 0xFFFFD84A),
        NONE("Nincs időjárás", FxTextures.MOON, 0xFFAAAAAA);

        public final String label;
        public final ResourceLocation icon;
        public final int color;

        Kind(String label, ResourceLocation icon, int color) {
            this.label = label;
            this.icon = icon;
            this.color = color;
        }
    }

    public WeatherElement() {
        super("weather", "Időjárás");
    }

    public static Kind current(Minecraft mc) {
        Level level = mc.level;
        if (level == null || mc.player == null) return Kind.CLEAR_DAY;
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) return Kind.NONE;
        long t = level.getDayTime() % 24000L;
        boolean day = t < 12300L || t > 23850L;
        if (level.getRainLevel(1f) < 0.2f) return day ? Kind.CLEAR_DAY : Kind.CLEAR_NIGHT;
        BlockPos pos = mc.player.blockPosition();
        Biome.Precipitation p = level.getBiome(pos).value().getPrecipitationAt(pos);
        if (p == Biome.Precipitation.NONE) return Kind.CLOUDY;
        if (level.getThunderLevel(1f) > 0.5f) return Kind.THUNDER;
        return p == Biome.Precipitation.SNOW ? Kind.SNOW : Kind.RAIN;
    }

    @Override
    public int width(Minecraft mc) {
        int w = 0;
        for (Kind k : Kind.values()) w = Math.max(w, mc.font.width(k.label));
        return 24 + w + 6;
    }

    @Override
    public int height(Minecraft mc) {
        return 22;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        Kind k = current(mc);
        g.fill(0, 0, width(mc), height(mc), 0x90000000);
        FxTextures.ensure(mc);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float bob = 0f;
        if (k == Kind.THUNDER && (System.currentTimeMillis() / 90) % 37 == 0) {
            g.fill(0, 0, width(mc), height(mc), 0x40FFFFFF); // villám-villanás
        }
        if (k == Kind.RAIN || k == Kind.SNOW) bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * 0.6f;
        g.pose().pushPose();
        g.pose().translate(2, 1 + bob, 0);
        g.pose().scale(20f / FxTextures.ICON, 20f / FxTextures.ICON, 1f);
        g.blit(k.icon, 0, 0, 0, 0, FxTextures.ICON, FxTextures.ICON, FxTextures.ICON, FxTextures.ICON);
        g.pose().popPose();
        RenderSystem.disableBlend();
        g.drawString(mc.font, k.label, 25, (height(mc) - mc.font.lineHeight) / 2 + 1, k.color, true);
    }
}
