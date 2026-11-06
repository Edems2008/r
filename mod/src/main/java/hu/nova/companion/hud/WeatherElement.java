package hu.nova.companion.hud;

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

    /** Becsült hőmérséklet °C-ban a biom hőmérsékletéből, napszakból, időjárásból és magasságból. */
    public static int temperature(Minecraft mc) {
        if (mc.level == null || mc.player == null) return 0;
        BlockPos pos = mc.player.blockPosition();
        Biome b = mc.level.getBiome(pos).value();
        float base = b.getBaseTemperature() * 25f - 2f;
        long t = mc.level.getDayTime() % 24000L;
        float dayCurve = (float) Math.sin((t - 1000) / 24000.0 * Math.PI * 2); // délután a legmelegebb
        base += dayCurve * 5f;
        base -= mc.level.getRainLevel(1f) * 4f;
        if (pos.getY() > 90) base -= (pos.getY() - 90) * 0.12f;
        if (!mc.level.dimensionType().hasSkyLight()) base = mc.level.dimensionType().ultraWarm() ? 52f : 8f;
        return Math.round(base);
    }

    @Override
    public int width(Minecraft mc) {
        return 112;
    }

    @Override
    public int height(Minecraft mc) {
        return 26;
    }

    @Override
    protected void draw(GuiGraphics g, Minecraft mc) {
        Kind k = current(mc);
        HudStyle.panel(g, 0, 0, 112, 26);
        HudStyle.accent(g, 0, 0, 26, k.color);
        FxTextures.ensure(mc);
        if (k == Kind.THUNDER && (System.currentTimeMillis() / 90) % 37 == 0) {
            g.fill(1, 1, 111, 25, 0x50FFFFFF); // villám-villanás
        }
        float bob = (k == Kind.RAIN || k == Kind.SNOW) ? (float) Math.sin(System.currentTimeMillis() / 450.0) * 0.7f : 0f;
        HudStyle.icon(g, k.icon, FxTextures.ICON, FxTextures.ICON, 3, bob, 26, 26);
        g.drawString(mc.font, k.label, 31, 5, k.color, true);
        if (k != Kind.NONE) {
            int temp = temperature(mc);
            int tc = temp <= 0 ? 0xFF9FD8FF : temp >= 30 ? 0xFFFF9A6B : 0xFFC9D2DD;
            HudStyle.text(g, mc.font, temp + "°C", 31, 15, tc, 0.8f);
        } else {
            HudStyle.text(g, mc.font, "itt nincs ég", 31, 15, HudStyle.MUTED, 0.8f);
        }
    }
}
