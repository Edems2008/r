package hu.nova.companion.hud;

import hu.nova.companion.ClientBootstrap;
import hu.nova.companion.config.CompanionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Képernyő-effektek ki/be kapcsolása (a HUD szerkesztőből nyílik). */
public final class EffectsScreen extends Screen {

    private final Screen parent;

    public EffectsScreen(Screen parent) {
        super(Component.literal("Nova effektek"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CompanionConfig c = ClientBootstrap.CONFIG;
        Object[][] toggles = {
                {"Vízcseppek", (BooleanSupplier) () -> c.waterDrops, (Consumer<Boolean>) v -> c.waterDrops = v},
                {"Esőcseppek", (BooleanSupplier) () -> c.rainDrops, (Consumer<Boolean>) v -> c.rainDrops = v},
                {"Égés effekt", (BooleanSupplier) () -> c.fireEffect, (Consumer<Boolean>) v -> c.fireEffect = v},
                {"Víz alatti szín", (BooleanSupplier) () -> c.underwaterTint, (Consumer<Boolean>) v -> c.underwaterTint = v},
                {"Méreg: szédülés", (BooleanSupplier) () -> c.poisonDizzy, (Consumer<Boolean>) v -> c.poisonDizzy = v},
                {"Hallucinációk", (BooleanSupplier) () -> c.hallucinations, (Consumer<Boolean>) v -> c.hallucinations = v},
                {"Falu hőse: falusiak", (BooleanSupplier) () -> c.villagerParty, (Consumer<Boolean>) v -> c.villagerParty = v},
                {"Gyorsaság: nyúló HUD", (BooleanSupplier) () -> c.speedStretch, (Consumer<Boolean>) v -> c.speedStretch = v},
                {"Ugrás: lebegő HUD", (BooleanSupplier) () -> c.jumpFloat, (Consumer<Boolean>) v -> c.jumpFloat = v},
                {"Óra az Esc menüben", (BooleanSupplier) () -> c.pauseClock, (Consumer<Boolean>) v -> c.pauseClock = v},
        };
        int colW = 150;
        int top = this.height / 2 - (toggles.length / 2) * 12 - 10;
        for (int i = 0; i < toggles.length; i++) {
            int col = i % 2;
            int row = i / 2;
            addToggle(this.width / 2 - colW - 2 + col * (colW + 4), top + row * 24, (String) toggles[i][0],
                    (BooleanSupplier) toggles[i][1], cast(toggles[i][2]));
        }
        int bottom = top + ((toggles.length + 1) / 2) * 24 + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, bottom, 200, 20).build());
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Boolean> cast(Object o) {
        return (Consumer<Boolean>) o;
    }

    private void addToggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(label(name, get.getAsBoolean()), b -> {
            boolean v = !get.getAsBoolean();
            set.accept(v);
            b.setMessage(label(name, v));
        }).bounds(x, y, 150, 20).build());
    }

    private static Component label(String name, boolean v) {
        return Component.literal(name + ": " + (v ? "§aBE" : "§cKI"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ClientBootstrap.CONFIG.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
