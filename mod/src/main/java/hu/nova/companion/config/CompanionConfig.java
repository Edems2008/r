package hu.nova.companion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/novacompanion.json – a launcher is ezt a fájlt írja/olvassa,
 * így a párosító kódot a launcher a játék indítása előtt meg tudja mutatni.
 */
public final class CompanionConfig {

    public static final class Element {
        public boolean enabled = true;
        /** Pozíció a szabad képernyőterület arányában (0..1), így átméretezésnél sem csúszik ki. */
        public double x;
        public double y;
        public float scale = 1.0f;

        public Element() {}

        public Element(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /** Telefonos kapcsolat be/ki. */
    public boolean companionEnabled = true;
    /** Helyi (LAN) HTTP szerver portja. */
    public int port = 25599;
    /** Párosító kód – a telefonnak ezt kell megadnia. */
    public String code = "";
    /** Opcionális relay szerver internetes kapcsolathoz, pl. https://nova-relay.onrender.com */
    public String relayUrl = "";
    /** Titkos kulcs a relay felé (csak a mod és a relay ismeri). */
    public String relayKey = "";
    /** Hány chunk sugarú körben készüljön élő térkép. */
    public int mapRadius = 10;
    /** Vízcseppek a képernyőn, amikor kijössz a vízből. */
    public boolean waterDrops = true;
    /** Esőcseppek a képernyőn, ha esik rád az eső. */
    public boolean rainDrops = true;
    /** Égés effekt (tűz / láva) és utána lassan elhalványuló hő. */
    public boolean fireEffect = true;
    /** Kékes színezés víz alatt. */
    public boolean underwaterTint = true;
    /** Valós (gép szerinti) idő az Esc menüben. */
    public boolean pauseClock = true;
    /** Méreg: szédülés (a kamera imbolyog). */
    public boolean poisonDizzy = true;
    /** Éhség / szomjúság: hallucinált kaja és vizes üvegek a képernyőn. */
    public boolean hallucinations = true;
    /** Falu hőse: ugráló falusiak az első percben. */
    public boolean villagerParty = true;
    /** Gyorsaság + futás: a HUD megnyúlik, sebesség-csíkok a szélén. */
    public boolean speedStretch = true;
    /** Ugrásfokozó: a HUD ugráskor felfelé lebeg. */
    public boolean jumpFloat = true;
    /** HUD elemek pozíciói. */
    public Map<String, Element> hud = new LinkedHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private transient Path file;

    public static CompanionConfig load(Path file) {
        CompanionConfig cfg = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, CompanionConfig.class);
            } catch (Exception e) {
                System.err.println("[NovaCompanion] Hibás config, alapértelmezett lesz: " + e);
            }
        }
        if (cfg == null) cfg = new CompanionConfig();
        cfg.file = file;
        cfg.fillDefaults();
        cfg.save();
        return cfg;
    }

    private void fillDefaults() {
        if (hud == null) hud = new LinkedHashMap<>();
        if (code == null || code.isBlank()) code = randomCode(6);
        if (relayKey == null || relayKey.isBlank()) relayKey = randomCode(24);
        if (relayUrl == null) relayUrl = "";
        if (port <= 0 || port > 65535) port = 25599;
        if (mapRadius < 2) mapRadius = 2;
        if (mapRadius > 24) mapRadius = 24;
        hud.putIfAbsent("compass", new Element(0.5, 0.0));
        hud.putIfAbsent("fps", new Element(0.0, 0.0));
        hud.putIfAbsent("coords", new Element(0.0, 0.06));
        hud.putIfAbsent("clock", new Element(1.0, 0.0));
        hud.putIfAbsent("vitals", new Element(0.5, 1.0));
        hud.putIfAbsent("weather", new Element(1.0, 0.075));
        hud.putIfAbsent("nightvision", new Element(1.0, 0.2));
        if (!hud.containsKey("realclock")) {
            Element rc = new Element(1.0, 0.09);
            rc.enabled = false;
            hud.put("realclock", rc);
        }
    }

    public Element element(String id) {
        return hud.computeIfAbsent(id, k -> new Element(0, 0));
    }

    public void resetHud() {
        hud.clear();
        fillDefaults();
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[NovaCompanion] Config mentése sikertelen: " + e);
        }
    }

    public static String randomCode(int len) {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
