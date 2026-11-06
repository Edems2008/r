package hu.nova.companion;

import com.mojang.blaze3d.platform.InputConstants;
import hu.nova.companion.config.CompanionConfig;
import hu.nova.companion.fx.CameraEffects;
import hu.nova.companion.fx.ScreenEffects;
import hu.nova.companion.hud.HudEditorScreen;
import hu.nova.companion.hud.HudManager;
import hu.nova.companion.hud.RealClockElement;
import hu.nova.companion.hud.ThirstCompat;
import hu.nova.companion.hud.VitalsElement;
import hu.nova.companion.map.MapScanner;
import hu.nova.companion.map.MapStore;
import hu.nova.companion.net.CompanionHttpServer;
import hu.nova.companion.net.OnlineBridge;
import hu.nova.companion.net.RelayPusher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

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
    private static OnlineBridge online;
    private static final ScreenEffects FX = new ScreenEffects();
    private static final Set<ResourceLocation> REPLACED_VANILLA = Set.of(
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id());

    private ClientBootstrap() {}

    static void init(IEventBus modBus) {
        CONFIG = CompanionConfig.load(FMLPaths.CONFIGDIR.get().resolve("novacompanion.json"));
        scanner = new MapScanner(STORE, () -> CONFIG.mapRadius);

        modBus.addListener(ClientBootstrap::onRegisterOverlays);
        modBus.addListener(ClientBootstrap::onRegisterKeys);
        modBus.addListener(ClientBootstrap::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onRenderOverlayPre);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onScreenRenderPost);
        MinecraftForge.EVENT_BUS.addListener(CameraEffects::onCameraAngles);
    }

    private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // képernyő-effektek a világ fölé, de minden HUD alá
        event.registerBelowAll("nova_screen_fx", (gui, graphics, partialTick, width, height) ->
                FX.render(graphics, width, height));
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
        if (CONFIG.onlineEnabled) {
            online = new OnlineBridge(STORE, () -> CONFIG.onlineCode, () -> CONFIG.onlineBrokers);
            online.start();
            System.out.println("[NovaCompanion] Bárhonnan kapcsolat bekapcsolva, online kód: " + CONFIG.onlineCode);
        }
    }

    /** A vanilla szívek / kaja / páncél / buborék (és a szomjúság mod saját sávja) helyett a Nova sávok látszanak. */
    private static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!VitalsElement.replacesVanilla(mc)) return;
        ResourceLocation id = event.getOverlay().id();
        if (REPLACED_VANILLA.contains(id)) {
            event.setCanceled(true);
            return;
        }
        String thirstMod = ThirstCompat.source();
        if (thirstMod != null && id.getNamespace().equals(thirstMod)
                && (id.getPath().contains("thirst") || id.getPath().contains("water"))
                && ThirstCompat.thirst(mc.player) >= 0) {
            event.setCanceled(true);
        }
    }

    /** Esc menü: valós (gép szerinti) idő a jobb felső sarokban. */
    private static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (CONFIG.pauseClock && event.getScreen() instanceof PauseScreen) {
            RealClockElement.drawPauseClock(event.getGuiGraphics(), Minecraft.getInstance(), event.getScreen().width);
        }
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
