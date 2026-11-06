package hu.nova.companion.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import hu.nova.companion.ClientBootstrap;
import hu.nova.companion.hud.ThirstCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * „Hallucinációk”:
 *  - Éhség effekt (vagy majdnem üres kaja): lebegő, pulzáló ételek a képernyőn
 *  - Szomjúság effekt (vagy majdnem üres víz, szomjúság moddal): lebegő, pulzáló vizes üvegek
 *  - Falu hőse: az effekt megszerzése utáni első percben vidám, ugráló falusiak a képernyő alján
 */
public final class Hallucinations {

    private static final class Floater {
        ItemStack stack;
        float x, y, vx, vy, life, maxLife, phase, size, rot, rotVel;
    }

    private static final class Dancer {
        Villager villager;
        float x, y, vx, vy, scale, dir, phase;
    }

    private static final ItemStack[] FOODS = {
            new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.BREAD), new ItemStack(Items.APPLE),
            new ItemStack(Items.COOKED_CHICKEN), new ItemStack(Items.GOLDEN_CARROT), new ItemStack(Items.CAKE),
            new ItemStack(Items.COOKED_PORKCHOP), new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.COOKIE)
    };
    private static ItemStack waterBottle;

    private static final VillagerProfession[] PROFESSIONS = {
            VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.CLERIC,
            VillagerProfession.ARMORER, VillagerProfession.FISHERMAN, VillagerProfession.BUTCHER,
            VillagerProfession.CARTOGRAPHER, VillagerProfession.SHEPHERD, VillagerProfession.NONE
    };

    private final List<Floater> floaters = new ArrayList<>();
    private final List<Dancer> dancers = new ArrayList<>();
    private final List<Floater> emeralds = new ArrayList<>();
    private final Random rnd = new Random();
    private boolean hadHero;
    private float partyLeft;
    private float hungerAmt, thirstAmt;
    private float time;
    private boolean villagerFailed;

    public void render(GuiGraphics g, int w, int h, float dt) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null) return;
        time += dt;
        var cfg = ClientBootstrap.CONFIG;

        // ---------------- éhség / szomjúság ----------------
        boolean hungry = cfg.hallucinations && (p.hasEffect(MobEffects.HUNGER) || p.getFoodData().getFoodLevel() <= 4);
        int thirst = ThirstCompat.thirst(p);
        boolean thirsty = cfg.hallucinations && (hasThirstEffect(p) || (thirst >= 0 && thirst <= 4));
        hungerAmt = approach(hungerAmt, hungry ? 1f : 0f, dt * 0.7f);
        thirstAmt = approach(thirstAmt, thirsty ? 1f : 0f, dt * 0.7f);

        if (hungerAmt > 0.01f || thirstAmt > 0.01f) {
            // halvány, lüktető színezés a szélein
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * 2.4);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            FxTextures.ensure(mc);
            if (hungerAmt > 0.01f) tint(g, w, h, 0.75f, 0.45f, 0.15f, 0.30f * hungerAmt * pulse);
            if (thirstAmt > 0.01f) tint(g, w, h, 0.25f, 0.55f, 1.0f, 0.30f * thirstAmt * pulse);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
        if (hungry && floaters.size() < 9 && rnd.nextFloat() < dt * 1.3f) spawnFloater(w, h, FOODS[rnd.nextInt(FOODS.length)]);
        if (thirsty && floaters.size() < 9 && rnd.nextFloat() < dt * 1.3f) spawnFloater(w, h, water());
        renderFloaters(g, w, h, dt, floaters, !hungry && !thirsty);

        // ---------------- falu hőse ----------------
        boolean hero = p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero && !hadHero && cfg.villagerParty) startParty(mc, w, h);
        hadHero = hero;
        if (!dancers.isEmpty()) renderParty(g, w, h, dt);
        if (!emeralds.isEmpty()) renderFloaters(g, w, h, dt, emeralds, false);
    }

    private static void tint(GuiGraphics g, int w, int h, float r, float gr, float b, float a) {
        g.setColor(r, gr, b, a);
        g.blit(FxTextures.VIGNETTE, 0, 0, w, h, 0, 0, FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE,
                FxTextures.VIGNETTE_SIZE, FxTextures.VIGNETTE_SIZE);
    }

    private static boolean hasThirstEffect(Player p) {
        for (MobEffectInstance e : p.getActiveEffects()) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect());
            if (id != null && id.getPath().contains("thirst")) return true;
        }
        return false;
    }

    private static ItemStack water() {
        if (waterBottle == null) waterBottle = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        return waterBottle;
    }

    // ---------------------------------------------------------------- lebegő tárgyak

    private void spawnFloater(int w, int h, ItemStack stack) {
        Floater f = new Floater();
        f.stack = stack;
        f.x = w * (0.1f + rnd.nextFloat() * 0.8f);
        f.y = h * (0.15f + rnd.nextFloat() * 0.6f);
        f.vx = (rnd.nextFloat() - 0.5f) * 25f;
        f.vy = (rnd.nextFloat() - 0.5f) * 15f;
        f.maxLife = f.life = 3.5f + rnd.nextFloat() * 3f;
        f.phase = rnd.nextFloat() * 6.28f;
        f.size = 22f + rnd.nextFloat() * 22f;
        f.rot = (rnd.nextFloat() - 0.5f) * 40f;
        f.rotVel = (rnd.nextFloat() - 0.5f) * 30f;
        floaters.add(f);
    }

    private void renderFloaters(GuiGraphics g, int w, int h, float dt, List<Floater> list, boolean fadeAll) {
        Iterator<Floater> it = list.iterator();
        while (it.hasNext()) {
            Floater f = it.next();
            f.life -= fadeAll ? dt * 3f : dt;
            if (f.life <= 0 || f.y > h + 40) {
                it.remove();
                continue;
            }
            f.x += (f.vx + (float) Math.sin(time * 1.3f + f.phase) * 10f) * dt;
            f.y += (f.vy + (float) Math.cos(time * 1.1f + f.phase) * 6f) * dt;
            f.rot += f.rotVel * dt;
            // megjelenés / eltűnés méretezéssel (tárgyaknál nincs átlátszóság), közben lüktetés
            float in = Math.min(1f, (f.maxLife - f.life) / 0.6f);
            float out = Math.min(1f, f.life / 0.8f);
            float pulse = 1f + 0.22f * (float) Math.sin(time * 3.2f + f.phase);
            float s = f.size * in * out * pulse / 16f;
            if (s <= 0.02f) continue;
            g.pose().pushPose();
            g.pose().translate(f.x, f.y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(f.rot + (float) Math.sin(time * 2f + f.phase) * 8f));
            g.pose().scale(s, s, 1f);
            g.renderItem(f.stack, -8, -8);
            g.pose().popPose();
        }
    }

    // ---------------------------------------------------------------- falusiak

    private void startParty(Minecraft mc, int w, int h) {
        if (villagerFailed) return;
        dancers.clear();
        partyLeft = 60f;
        try {
            int n = 6 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                Villager v = EntityType.VILLAGER.create(mc.level);
                if (v == null) return;
                v.setVillagerData(v.getVillagerData().setProfession(PROFESSIONS[rnd.nextInt(PROFESSIONS.length)]));
                Dancer d = new Dancer();
                d.villager = v;
                d.x = w * (0.08f + 0.84f * (i + 0.5f) / n);
                d.y = h - 34;
                d.vx = (rnd.nextBoolean() ? 1 : -1) * (12f + rnd.nextFloat() * 18f);
                d.vy = -rnd.nextFloat() * 120f;
                d.scale = 16f + rnd.nextFloat() * 6f;
                d.dir = Math.signum(d.vx);
                d.phase = rnd.nextFloat() * 6.28f;
                dancers.add(d);
            }
        } catch (Throwable t) {
            villagerFailed = true;
            dancers.clear();
            System.err.println("[NovaCompanion] Falusiak megjelenítése nem sikerült: " + t);
        }
    }

    private void renderParty(GuiGraphics g, int w, int h, float dt) {
        partyLeft -= dt;
        if (partyLeft <= 0) {
            dancers.clear();
            return;
        }
        float ground = h - 34; // a hotbar fölött
        float shrink = Math.min(1f, partyLeft / 2f); // az utolsó 2 mp-ben összezsugorodnak
        try {
            for (Dancer d : dancers) {
                d.vy += 420f * dt;
                d.x += d.vx * dt;
                d.y += d.vy * dt;
                if (d.y >= ground) {
                    d.y = ground;
                    d.vy = -(110f + rnd.nextFloat() * 130f); // újabb ugrás
                    if (rnd.nextFloat() < 0.25f) d.vx = -d.vx;
                    if (rnd.nextFloat() < 0.3f) spawnEmerald(d.x, d.y - d.scale * 2);
                }
                if (d.x < 15) { d.x = 15; d.vx = Math.abs(d.vx); }
                if (d.x > w - 15) { d.x = w - 15; d.vx = -Math.abs(d.vx); }
                d.dir += (Math.signum(d.vx) - d.dir) * Math.min(1f, dt * 6f);

                Villager v = d.villager;
                float body = 180f - d.dir * 55f; // kicsit oldalra, a haladási irányba néz
                v.yBodyRot = v.yBodyRotO = body;
                v.yHeadRot = v.yHeadRotO = body + (float) Math.sin(time * 4f + d.phase) * 20f;
                v.setYRot(body);
                v.setXRot((float) Math.sin(time * 5f + d.phase) * 10f);
                v.xRotO = v.getXRot();
                v.walkAnimation.update(0.8f, 0.4f); // lábak mozgása
                int scale = Math.max(1, Math.round(d.scale * shrink));
                InventoryScreen.renderEntityInInventory(g, Math.round(d.x), Math.round(d.y), scale,
                        new Quaternionf().rotateZ((float) Math.PI), null, v);
            }
        } catch (Throwable t) {
            villagerFailed = true;
            dancers.clear();
            System.err.println("[NovaCompanion] Falusiak megjelenítése nem sikerült: " + t);
        }
    }

    private void spawnEmerald(float x, float y) {
        if (emeralds.size() > 12) return;
        Floater f = new Floater();
        f.stack = new ItemStack(Items.EMERALD);
        f.x = x;
        f.y = y;
        f.vx = (rnd.nextFloat() - 0.5f) * 30f;
        f.vy = -30f - rnd.nextFloat() * 25f;
        f.maxLife = f.life = 1.2f + rnd.nextFloat() * 0.6f;
        f.size = 9f + rnd.nextFloat() * 4f;
        f.phase = rnd.nextFloat() * 6.28f;
        f.rotVel = (rnd.nextFloat() - 0.5f) * 180f;
        emeralds.add(f);
    }

    private static float approach(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }
}
