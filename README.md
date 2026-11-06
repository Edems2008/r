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

#### Interneten át (nem ugyanazon a WiFi-n)

Alapból a telefon ugyanazon a WiFi-n közvetlenül a játékhoz csatlakozik (25599-es port). Mobilnetről vagy máshonnan a **Nova Relay** kell:

1. Hozz létre egy ingyenes **Web Service**-t a Render.com-on ebből a repóból. Start command: `node relay/server.js` (minta: `relay/render.yaml`).
2. A launcher *Telefon* fülén add meg a relay címét (pl. `https://nova-relay.onrender.com`).
3. Az appban a cím legyen a relay címe, a kód ugyanaz.

A mod másodpercenként feltölti az állapotot és a megváltozott térkép-darabokat, a telefon pedig a relay-ről olvassa őket. A kódhoz tartozó titkos kulcsot csak a mod ismeri, így más nem tud a te kódodra adatot küldeni.

## Technikai részletek

- **Launcher**: Electron, [minecraft-launcher-core](https://github.com/Pierce01/MinecraftLauncher-core) (Forge-ot ForgeWrapperrel indít), [msmc](https://github.com/Hanro50/MSMC) a Microsoft-bejelentkezéshez. A refresh token titkosítva van tárolva (Electron safeStorage). Java: Eclipse Temurin 17 JRE, automatikusan letöltve. Adatmappa: `%APPDATA%/.novalauncher`.
- **Automatikus belépés**: `--quickPlayMultiplayer host:port` (Minecraft 1.20+).
- **Mod**: kliensoldali Forge 47.x mod. A térképhez a kliens által betöltött chunkokból a legfelső blokk `MapColor` színét veszi, magasság szerinti árnyalással. Tickenként csak 6 chunkot dolgoz fel, hogy ne vigye le az FPS-t. A HTTP szerver a JDK beépített `HttpServer`-e, CORS-szal és kódos azonosítással. A webes felületet a mod is kiszolgálja.
- **API**: `GET /api/state?code=…` és `GET /api/tiles?code=…&since=REV&epoch=E`. Ugyanez a formátum a modon és a relay-en is.

## Megjegyzések

- A launcher csak **eredeti, megvásárolt** Minecraft Java fiókkal működik (Microsoft-bejelentkezés).
- A beépített modok a saját licencük alatt állnak. Az Embeddium és az Oculus LGPL, a többi a Modrinth-on található licenccel. A shaderpackokat a launcher a felhasználó gépén tölti le, nem kerülnek a telepítőbe.
- Windows tűzfal: első indításkor engedélyezd a Java-nak a **magánhálózatot**, különben a telefon nem éri el a játékot WiFi-n.
