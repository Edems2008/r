package hu.nova.companion;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Nova Companion – kliensoldali mod a Nova Launcherhez.
 *  - mozgatható HUD: valós FPS, iránytű sáv, koordináták (H billentyű = szerkesztő)
 *  - élő térkép + skin megosztása a telefonos appal (LAN vagy relay szerveren át)
 */
@Mod(NovaCompanion.MODID)
public final class NovaCompanion {
    public static final String MODID = "novacompanion";

    public NovaCompanion() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
