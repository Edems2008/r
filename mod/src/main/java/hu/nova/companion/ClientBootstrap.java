package hu.nova.companion;

import com.mojang.blaze3d.platform.InputConstants;
import hu.nova.companion.config.CompanionConfig;
import hu.nova.companion.hud.HudEditorScreen;
import hu.nova.companion.hud.HudManager;
import hu.nova.companion.map.MapScanner;
import hu.nova.companion.map.MapStore;
import hu.nova.companion.net.CompanionHttpServer;
import hu.nova.companion.net.RelayPusher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

public final class ClientBootstrap {

    public static CompanionConfig CONFIG;
    public static final MapStore STORE = new MapStore();
    public static final KeyMapping HUD_EDITOR_KEY = new KeyMapping(
            "key.novacompanion.hud_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.novacompanion");

    private static MapScanner scanner;
    private static CompanionHttpServer httpServer;
    private static RelayPusher relay;

    private ClientBootstrap() {}

    static void init(IEventBus modBus) {
        CONFIG = CompanionConfig.load(FMLPaths.CONFIGDIR.get().resolve("novacompanion.json"));
        scanner = new MapScanner(STORE, () -> CONFIG.mapRadius);

        modBus.addListener(ClientBootstrap::onRegisterOverlays);
        modBus.addListener(ClientBootstrap::onRegisterKeys);
        modBus.addListener(ClientBootstrap::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
    }

    private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("nova_hud", (gui, graphics, partialTick, width, height) ->
                HudManager.render(graphics, width, height));
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(HUD_EDITOR_KEY);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        if (!CONFIG.companionEnabled) return;
        httpServer = new CompanionHttpServer(STORE, () -> CONFIG.code);
        try {
            httpServer.start(CONFIG.port);
            System.out.println("[NovaCompanion] Telefonos szerver fut a " + CONFIG.port + " porton, kód: " + CONFIG.code);
        } catch (Exception e) {
            System.err.println("[NovaCompanion] Nem sikerült elindítani a szervert (" + CONFIG.port + "): " + e);
        }
        relay = new RelayPusher(STORE, () -> CONFIG.relayUrl, () -> CONFIG.code, () -> CONFIG.relayKey);
        relay.start();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (HUD_EDITOR_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new HudEditorScreen());
        }
        if (CONFIG.companionEnabled) scanner.tick(mc);
    }
}
