# Nova Launcher – egyedi Minecraft launcher (Forge 1.20.1)

Egyedi Minecraft launcher, ami:

- **beépített modokkal** érkezik: letöltöd, telepíted, és a modok már benne vannak
- **automatikusan belép** arra a szerverre, aminek beírod az IP-jét (Minecraft Quick Play)
- **Forge 1.20.1**-re épül, és **shadereket** is tud: Oculus (Iris Forge-ra) + Embeddium
- saját HUD-ot ad: **valós FPS számláló**, **iránytű sáv**, **koordináták**, **Minecraft óra napszámlálóval**, és ezeket **oda húzhatod a képernyőn, ahová akarod**
- **realisztikusabb állapotsávok** (HP, páncél, kaja, víz/szomjúság, levegő), **képernyő-effektek** (lefolyó vízcseppek, égés), és **valós idő az Esc menüben**
- van hozzá **Android app**, amit neten keresztül összekötsz a launcherrel. A telefonon látod a **skinedet**, és **élő térképként** is használhatod, ami szinkronban mutatja a Minecraftod térképét

```
┌──────────────┐  indít   ┌─────────────────────────────┐  LAN (HTTP)   ┌──────────────┐
│ Nova Launcher│ ───────► │ Minecraft 1.20.1 + Forge    │ ────────────► │ Android app  │
│  (Electron)  │          │  + Nova Companion mod       │               │ (Nova        │
└──────────────┘          │  + Embeddium + Oculus + ... │ ──► Relay ──► │  Companion)  │
                          └─────────────────────────────┘  (internet)   └──────────────┘
```

## Mappák

| Mappa | Mi ez |
|---|---|
| `launcher/` | A launcher (Electron): Microsoft-bejelentkezés, Java 17 automatikus letöltése, Forge telepítése, modok szinkronja, shaderek, telefonos párosítás QR-kóddal |
| `mod/` | **Nova Companion** Forge 1.20.1 mod: mozgatható HUD, élő térkép és állapot küldése a telefonra |
| `android/` | **Nova Companion** Android app (Kotlin, WebView) |
| `companion-web/` | A telefonos felület (térkép + skin). Ugyanezt használja az app, a mod és a relay is |
| `relay/` | **Nova Relay**: kicsi Node.js szerver az interneten át történő kapcsolathoz (mobilnet) |

## Letöltés / build

Nem kell semmit kézzel fordítanod: a **GitHub Actions** (`.github/workflows/build.yml`) minden push után lefordít mindent:

1. `novacompanion-mod`: a mod `.jar` fájlja
2. `launcher-windows-latest`: **Windows telepítő (.exe)** és hordozható változat. Benne van a Nova Companion mod és a `modpack.json` összes modja
3. `launcher-ubuntu-latest`: Linux AppImage
4. `android-apk`: **NovaCompanion.apk** (közvetlenül telepíthető)

A fájlokat a repó **Actions** fülén, a futás alján (*Artifacts*) találod. Ha egy `v1.0.0` formájú taget pusholsz, egy GitHub Release is készül az összes fájllal.

Helyi build:

```bash
# mod (Java 17)
cd mod && ./gradlew build                 # -> mod/build/libs/novacompanion-1.20.1-1.0.0.jar

# launcher (Node 20)
cp mod/build/libs/novacompanion-*.jar launcher/build-resources/mods/
cd launcher && npm ci && npm run fetch-mods && npm run dist:win

# android (Android SDK kell)
cd android && ./gradlew assembleRelease
```

## Használat

1. Telepítsd és indítsd el a **Nova Launchert**, majd bal alul **jelentkezz be Microsoft fiókkal** (eredeti Minecraft Java kell).
2. A *Játék* fülön írd be a **szerver IP-jét** (pl. `valami.aternos.me:12345`), és hagyd bepipálva az *automatikus belépést*.
3. Nyomd meg a **JÁTÉK** gombot. Első indításkor a launcher letölti a Java 17-et, a Minecraftot és a Forge-ot, telepíti a modokat, majd egyből belép a szerverre.

> A szervernek Forge 1.20.1-esnek kell lennie, ha szerveroldali modokat is használsz. A csomag modjai (Embeddium, Oculus, Nova Companion, FerriteCore, ModernFix, EntityCulling) kliensoldaliak, így vanilla 1.20.1 szerverre is fel lehet lépni velük.

### Indítás a hivatalos Minecraft Launcherből (bejelentkezés nélkül a Nova Launcherben)

Ha most nem tudsz bejelentkezni a Nova Launcherben, de a **hivatalos Minecraft Launcherben be vagy jelentkezve** a saját fiókoddal:

1. A Nova Launcher *Játék* fülén írd be a szerver IP-jét, majd kattints a **„Telepítés a hivatalos launcherbe”** gombra.
   A launcher telepíti a Forge 1.20.1-et a hivatalos launcher mappájába, előkészíti a modokat/shadereket/beállításokat, és létrehoz egy **„Nova Pack”** telepítést.
2. Nyisd meg (vagy indítsd újra) a hivatalos Minecraft Launchert, a *Játék* gomb melletti listából válaszd a **„Nova Pack”**-ot, és indítsd.

Minden ugyanúgy működik (modok, shaderek, HUD, effektek, telefonos térkép), az automatikus szerverbelépést pedig ilyenkor a Nova Companion mod végzi a főmenüből. A hivatalos launcher többi telepítéséhez nem nyúl.

> Tipp: a Microsoft-bejelentkezéshez nem kell hozzáférni a Gmailhez – az e-mail cím csak a felhasználónév, elég a jelszó. Az e-mail csak jelszó-visszaállításhoz kell.

### Saját modok hozzáadása

- **Modrinth-ról**: írd be a mod azonosítóját (slug) a `launcher/modpack.json` `modrinth` listájába. A build letölti és SHA1-gyel ellenőrzi.
- **Saját .jar**: tedd a `launcher/build-resources/mods/` mappába (helyben buildeléshez), vagy commitold oda a jar-t, ha a CI-nak is bele kell tennie.
- A launcher csak a saját maga által telepített modokat cseréli/törli. Ha a játékos kézzel tesz modot a `mods` mappába, azt nem bántja.

### Shaderek (Forge 1.20.1)

Az OptiFine nem kell: a shadereket az **Oculus** futtatja, az **Embeddium** pedig a gyors renderelést adja. A launcher *Shaderek* fülén:

- egy kattintással letöltheted a Complementary Reimagined, BSL vagy Sildur's shadert (Modrinth-ról)
- kiválaszthatod az aktív shaderpackot, és be- vagy kikapcsolhatod. Ezt a launcher a `config/oculus.properties` fájlba írja
- játékon belül: *Videóbeállítások → Shader Packs* (vagy az **O** billentyű)

### HUD (FPS, iránytű, koordináták)

Játék közben nyomd meg a **H** billentyűt, ez megnyitja a HUD szerkesztőt:
- **húzás**: mozgatás (a széleknél és középen magától igazodik)
- **jobb klikk**: elem be/ki
- **görgő**: méret (50–300%)

A pozíciókat a képernyőmérethez arányosan menti (`config/novacompanion.json`), így ablakméret-váltáskor sem csúsznak ki. A billentyű az *Irányítás* menüben átállítható.

### Óra és napszámláló

- **Minecraft óra** (HUD elem, alapból jobb felül): játékbeli idő (☀ nappal / ☾ éjjel), **hányadik napnál tartasz**, és **hány nap telt el**. Az időt a szerver küldi a kliensnek, így szerveren játszva is pontos.
- **Esc menü**: a jobb felső sarokban a **valós, gép szerinti idő** (óra:perc:másodperc, dátum, a hét napja), alatta a Minecraft-idő.
- Kérésre a valós idő a HUD-on is megjelenhet: a szerkesztőben jobb klikk a „Valós idő” elemre, vagy a launcher HUD fülén.

### Képernyő-effektek

Csak első személyű nézetben jelennek meg:
- **Vízcseppek**: ha kijössz a vízből, a képernyőre cseppek tapadnak. A nagyobbak egy idő után nedves csíkot húzva lefolynak, a kisebbek elpárolognak.
- **Eső**: esőben időnként egy-egy csepp jelenik meg a képernyőn.
- **Égés**: tűzben vibráló, izzó szélek és felszálló parázs, lávában erősebben. Kijutás után a hő lassan elhalványul.
- **Víz alatt**: enyhe kékes szín.

Mindegyik kapcsolható a HUD szerkesztő (**H**) gombjaival vagy a launcher *HUD* fülén. A textúrákat a mod futásidőben, kódból generálja.

### Időjárás és éjjellátás

- **Időjárás** (HUD elem, jobb felül az óra alatt): nap (nappal) vagy hold (éjjel) derült időben, esőfelhő esőben, hófelhő havas biomban, sötét felhő villámmal viharban. Ha esik, de a biomban nincs csapadék (pl. sivatag), felhő látszik. Viharban az ikon időnként felvillan. A szerver szinkronizálja, a telefonos appban is látszik.
- **Éjjellátó szemüveg**: amíg rajtad van az éjjellátás, jobb felül egy zölden izzó szemüveg látszik, alatta a hátralévő idő. Az utolsó 10 másodpercben villog.

### Effekt-hatások

- **Méreg → szédülés**: a kép lassan imbolyog és dől, a szélek zöldesen lüktetnek. Csak vizuális, a célzást nem befolyásolja. Erősebb méregnél erősebb.
- **Falu hőse**: az effekt megszerzése utáni első percben vidám, ugráló, különböző foglalkozású 3D falusiak jelennek meg a képernyő alján, néha smaragd pattan ki belőlük.
- **Éhség → hallucináció**: ételek (sült hús, kenyér, alma, torta…) lebegnek és lüktetnek a képernyőn, a szélek barnásan pulzálnak. Kiváltja az Éhség effekt, vagy ha szinte üres a kaja sáv (≤ 2 comb).
- **Szomjúság → hallucináció**: vizes üvegek lebegnek és lüktetnek, a szélek kékesen pulzálnak. Kiváltja bármelyik „thirst” effekt (pl. Tough As Nails), vagy ha szomjúság moddal szinte üres a víz sáv.
- **Gyorsaság + futás**: az egész HUD vízszintesen megnyúlik (erősebb effektnél jobban), a képernyő szélén sebesség-csíkok suhannak.
- **Ugrásfokozó**: ugráskor az egész HUD felfelé lebeg, majd rugalmasan visszaáll.

Mind kapcsolható a HUD szerkesztő **Effektek…** gombjával, vagy a launcher *HUD* fülén.

### Realisztikus állapotsávok

A vanilla szívek, combok, páncél és buborékok helyett:
- fényes, színátmenetes sávok számértékkel és simított animációval
- sebzéskor egy világos „sebzés-csík” lassan húzódik vissza; alacsony HP-nál pulzál a keret
- a HP sáv színe jelzi a hatásokat: méreg (zöld), wither (fekete), fagyás (jégkék), regeneráció (rózsaszín); az elnyelés (absorption) aranyszínű
- a kaja sávon a telítettség vékony arany csíkként látszik
- **víz sáv**: ha fent van a **Tough As Nails** vagy a **Thirst Was Taken** szomjúság mod, a mod saját vízkijelzése helyett egy kék sáv látszik
- a levegő sáv csak víz alatt jelenik meg

Az egész egy mozgatható HUD elem („Állapot sávok”, alapból a hotbar fölött). Ha a szerkesztőben kikapcsolod, visszajönnek a vanilla kijelzések.

### Telefon (élő térkép + skin)

1. Telepítsd a **NovaCompanion.apk**-t. Ha a telefon kéri, engedélyezd az ismeretlen forrásból származó telepítést.
2. A launcherben nyisd meg a **Telefon** fület: ott látod a címet, a párosító kódot és egy QR-kódot.
3. Indítsd el a játékot, és az appban add meg a címet és a kódot. A QR-kódot a telefon kamerájával is beolvashatod, ilyenkor app nélkül, böngészőben nyílik meg ugyanez.

Az appban:
- **Térkép**: élő felülnézeti térkép a vanilla térkép színeivel. Látszik rajta a pozíciód, a nézési irányod és a közeli játékosok. Kezelés: követés, ujjal mozgatás, csípéssel zoom, és forgatható is, hogy a nézési irányod legyen felfelé. Netherben barlang módban rajzol (a plafon alatt).
- **Skin**: 3D-s, forgatható skin (sétáló animációval), plusz életerő, éhség, biom, dimenzió, játékidő, FPS és szerver.

Az app ébren tartja a kijelzőt, így a telefon mellékképernyős térképként használható.

#### Bárhonnan – interneten át (alapértelmezett, nincs mit beállítani)

A telefonnak és a gépnek **nem kell ugyanazon a hálózaton lennie** (mobilnet, másik WiFi, másik város – mindegy), csak internet kell mindkettőn.

1. A launcher *Telefon* fülén látod az **online kódot** (pl. `K7QX2-M9PLA`) és egy QR-kódot.
2. Olvasd be a QR-kódot a telefon kamerájával (megnyitja az appot), **vagy** az appban a **„Bárhonnan”** fülön írd be a kódot.
3. Indítsd el a játékot – pár másodperc múlva megjelenik a térkép és a skin.

Hogyan működik: a mod és az app egy ingyenes, nyilvános MQTT szerveren (EMQX / HiveMQ / Mosquitto, ebben a sorrendben, automatikus átváltással) keresztül üzen egymásnak. Minden üzenet **AES-256-GCM-mel titkosított**, a kulcs és a csatorna azonosítója a kódból származik (PBKDF2, 20 000 kör) – maga a kód sosem kerül a hálózatra, a szerver üzemeltetője sem látja az adatokat. Ha valaki megkapná a kódodat, generálj újat a launcherben („Új kódok generálása”).

#### Ugyanazon a WiFi-n (közvetlen)

Az appban az **„Azonos WiFi”** fül: a gép helyi címe (pl. `192.168.1.10:25599`) és a 6 jegyű kód. Ez internet nélkül is megy.

#### Saját relay szerver (haladó, nem szükséges)

Ha a nyilvános szerverek helyett sajátot szeretnél: hozz létre egy ingyenes **Web Service**-t a Render.com-on ebből a repóból (Start command: `node relay/server.js`, minta: `relay/render.yaml`), add meg a címét a launcher *Telefon* fülén, és az appban az „Azonos WiFi” fülön a relay címét írd be címnek.

## Technikai részletek

- **Launcher**: Electron, [minecraft-launcher-core](https://github.com/Pierce01/MinecraftLauncher-core) (Forge-ot ForgeWrapperrel indít), [msmc](https://github.com/Hanro50/MSMC) a Microsoft-bejelentkezéshez. A refresh token titkosítva van tárolva (Electron safeStorage). Java: Eclipse Temurin 17 JRE, automatikusan letöltve. Adatmappa: `%APPDATA%/.novalauncher`.
- **Automatikus belépés**: `--quickPlayMultiplayer host:port` (Minecraft 1.20+).
- **Mod**: kliensoldali Forge 47.x mod. A térképhez a kliens által betöltött chunkokból a legfelső blokk `MapColor` színét veszi, magasság szerinti árnyalással. Tickenként csak 6 chunkot dolgoz fel, hogy ne vigye le az FPS-t. A HTTP szerver a JDK beépített `HttpServer`-e, CORS-szal és kódos azonosítással. A webes felületet a mod is kiszolgálja.
- **API**: `GET /api/state?code=…` és `GET /api/tiles?code=…&since=REV&epoch=E`. Ugyanez a formátum a modon és a relay-en is.

## Megjegyzések

- A launcher csak **eredeti, megvásárolt** Minecraft Java fiókkal működik (Microsoft-bejelentkezés).
- A beépített modok a saját licencük alatt állnak. Az Embeddium és az Oculus LGPL, a többi a Modrinth-on található licenccel. A shaderpackokat a launcher a felhasználó gépén tölti le, nem kerülnek a telepítőbe.
- Windows tűzfal: első indításkor engedélyezd a Java-nak a **magánhálózatot**, különben a telefon nem éri el a játékot WiFi-n.
