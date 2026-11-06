package hu.nova.companion.hud;

import hu.nova.companion.ClientBootstrap;
import hu.nova.companion.config.CompanionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * HUD szerkesztő (alapból H billentyű):
 *  - bal egérgomb + húzás: elem mozgatása
 *  - jobb egérgomb: elem be/ki kapcsolása
 *  - görgő: elem méretezése
 */
public final class HudEditorScreen extends Screen {

    private HudElement dragging;
    private double grabDx;
    private double grabDy;

    public HudEditorScreen() {
        super(Component.translatable("screen.novacompanion.hud_editor"));
    }

    @Override
    protected void init() {
        int y = this.height / 2 + 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.novacompanion.reset"), b -> {
            ClientBootstrap.CONFIG.resetHud();
            ClientBootstrap.CONFIG.save();
        }).bounds(this.width / 2 + 2, y, 100, 20).build());

        CompanionConfig cfg = ClientBootstrap.CONFIG;
        int y2 = y + 24;
        addToggle(this.width / 2 - 155, y2, "Vízcseppek", () -> cfg.waterDrops, v -> cfg.waterDrops = v);
        addToggle(this.width / 2 - 52, y2, "Esőcseppek", () -> cfg.rainDrops, v -> cfg.rainDrops = v);
        addToggle(this.width / 2 + 51, y2, "Égés effekt", () -> cfg.fireEffect, v -> cfg.fireEffect = v);
        addToggle(this.width / 2 - 103, y2 + 24, "Víz alatti szín", () -> cfg.underwaterTint, v -> cfg.underwaterTint = v);
        addToggle(this.width / 2 + 0, y2 + 24, "Óra az Esc menüben", () -> cfg.pauseClock, v -> cfg.pauseClock = v);
    }

    private void addToggle(int x, int y, String name, java.util.function.BooleanSupplier get,
                           java.util.function.Consumer<Boolean> set) {
        java.util.function.Function<Boolean, Component> label =
                v -> Component.literal(name + ": " + (v ? "BE" : "KI"));
        addRenderableWidget(Button.builder(label.apply(get.getAsBoolean()), b -> {
            boolean v = !get.getAsBoolean();
            set.accept(v);
            b.setMessage(label.apply(v));
        }).bounds(x, y, 103, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x60000000);
        Minecraft mc = Minecraft.getInstance();
        for (HudElement e : HudManager.ELEMENTS) {
            CompanionConfig.Element c = ClientBootstrap.CONFIG.element(e.id);
            int x = e.screenX(c, mc, width);
            int y = e.screenY(c, mc, height);
            int w = Math.round(e.width(mc) * c.scale);
            int h = Math.round(e.height(mc) * c.scale);
            e.render(g, mc, c, width, height);
            if (!c.enabled) g.fill(x, y, x + w, y + h, 0xA0400000);
            int border = c.enabled ? (e == dragging ? 0xFFFFD700 : 0xFF55FF55) : 0xFFFF5555;
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, border);
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            if (hovered) {
                String tip = e.label + (c.enabled ? "" : " (kikapcsolva)") + "  " + Math.round(c.scale * 100) + "%";
                g.drawString(font, tip, x, y + h + 3 > height - 10 ? y - 11 : y + h + 3, 0xFFFFFFFF, true);
            }
        }
        g.drawCenteredString(font, Component.translatable("screen.novacompanion.help"), width / 2, height / 2 - 4, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.novacompanion.help2"), width / 2, height / 2 + 10, 0xFFAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private HudElement elementAt(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = HudManager.ELEMENTS.size() - 1; i >= 0; i--) {
            HudElement e = HudManager.ELEMENTS.get(i);
            CompanionConfig.Element c = ClientBootstrap.CONFIG.element(e.id);
            int x = e.screenX(c, mc, width);
            int y = e.screenY(c, mc, height);
            int w = Math.round(e.width(mc) * c.scale);
            int h = Math.round(e.height(mc) * c.scale);
            if (mx >= x && mx < x + w && my >= y && my < y + h) return e;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        HudElement e = elementAt(mx, my);
        if (e == null) return false;
        CompanionConfig.Element c = ClientBootstrap.CONFIG.element(e.id);
        if (button == 1) {
            c.enabled = !c.enabled;
            return true;
        }
        if (button == 0) {
            Minecraft mc = Minecraft.getInstance();
            dragging = e;
            grabDx = mx - e.screenX(c, mc, width);
            grabDy = my - e.screenY(c, mc, height);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null && button == 0) {
            Minecraft mc = Minecraft.getInstance();
            CompanionConfig.Element c = ClientBootstrap.CONFIG.element(dragging.id);
            int w = Math.round(dragging.width(mc) * c.scale);
            int h = Math.round(dragging.height(mc) * c.scale);
            double freeW = Math.max(1, width - w);
            double freeH = Math.max(1, dragging.freeHeight(c, mc, height));
            double nx = mx - grabDx;
            double ny = my - grabDy;
            // finom illesztés a szélekhez és a középvonalhoz
            if (Math.abs(nx) < 4) nx = 0;
            if (Math.abs(nx - freeW) < 4) nx = freeW;
            if (Math.abs(nx - freeW / 2) < 4) nx = freeW / 2;
            if (Math.abs(ny) < 4) ny = 0;
            if (Math.abs(ny - freeH) < 4) ny = freeH;
            c.x = Mth.clamp(nx / freeW, 0.0, 1.0);
            c.y = Mth.clamp(ny / freeH, 0.0, 1.0);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        HudElement e = elementAt(mx, my);
        if (e == null) return super.mouseScrolled(mx, my, delta);
        CompanionConfig.Element c = ClientBootstrap.CONFIG.element(e.id);
        c.scale = Mth.clamp(Math.round((c.scale + (float) delta * 0.1f) * 10f) / 10f, 0.5f, 3.0f);
        return true;
    }

    @Override
    public void onClose() {
        ClientBootstrap.CONFIG.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
