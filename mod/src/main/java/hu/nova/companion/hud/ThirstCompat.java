package hu.nova.companion.hud;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Szomjúság-modok kiolvasása reflexióval (nincs fordítási függőség, ha nincs fent a mod, egyszerűen nem jelenik meg a sáv).
 * Támogatott: Tough As Nails (toughasnails), Thirst Was Taken (thirst).
 */
public final class ThirstCompat {

    private static boolean initialized;
    private static String source; // "toughasnails" | "thirst" | null
    private static Method tanGetThirst;     // ThirstHelper.getThirst(Player) -> IThirst
    private static Method tanValue;         // IThirst.getThirst()
    private static Capability<?> twtCap;    // ModCapabilities.PLAYER_THIRST
    private static Method twtValue;         // IThirst.getThirst()
    private static boolean failed;

    private ThirstCompat() {}

    private static void init() {
        initialized = true;
        try {
            if (ModList.get().isLoaded("toughasnails")) {
                Class<?> helper = Class.forName("toughasnails.api.thirst.ThirstHelper");
                tanGetThirst = helper.getMethod("getThirst", Player.class);
                tanValue = Class.forName("toughasnails.api.thirst.IThirst").getMethod("getThirst");
                source = "toughasnails";
                return;
            }
            if (ModList.get().isLoaded("thirst")) {
                Class<?> caps = Class.forName("dev.ghen.thirst.foundation.common.capability.ModCapabilities");
                Field f = caps.getField("PLAYER_THIRST");
                twtCap = (Capability<?>) f.get(null);
                twtValue = Class.forName("dev.ghen.thirst.foundation.common.capability.IThirst").getMethod("getThirst");
                source = "thirst";
            }
        } catch (Throwable t) {
            System.err.println("[NovaCompanion] Szomjúság mod felismerése sikertelen: " + t);
            source = null;
        }
    }

    /** A szomjúság mod azonosítója (a saját HUD-ját ilyenkor elrejtjük), vagy null. */
    public static String source() {
        if (!initialized) init();
        return failed ? null : source;
    }

    /** @return 0..20, vagy -1 ha nincs szomjúság mod */
    public static int thirst(Player p) {
        if (source() == null || p == null) return -1;
        try {
            if (tanGetThirst != null) {
                Object data = tanGetThirst.invoke(null, p);
                return data == null ? -1 : ((Number) tanValue.invoke(data)).intValue();
            }
            if (twtCap != null) {
                Optional<?> data = p.getCapability(twtCap).resolve();
                return data.isPresent() ? ((Number) twtValue.invoke(data.get())).intValue() : -1;
            }
        } catch (Throwable t) {
            failed = true;
            System.err.println("[NovaCompanion] Szomjúság kiolvasása sikertelen, a víz sáv kikapcsolva: " + t);
        }
        return -1;
    }
}
