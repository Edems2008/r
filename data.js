/* ============================================================
   TARTALOM — ITT SZERKESZD AZ OLDAL MINDEN SZÖVEGÉT ÉS JÁTÉKÁT
   ============================================================
   Minden, ami az oldalon megjelenik (stúdió neve, csapat, játékok,
   árak, linkek, képek, trailerek), ebből a fájlból jön.
   A jelenlegi adatok HELYKITÖLTŐK — cseréld le a sajátjaidra.
*/

window.STUDIO = {
  name: "VOIDPULSE",
  nameSuffix: "STUDIOS",
  tagline: "Játékok, amik nem engednek el.",
  heroText:
    "Független játékfejlesztő csapat vagyunk. Gyors, hangulatos, emlékezetes játékokat építünk — a saját szabályaink szerint.",
  email: "hello@voidpulse.example",
  socials: [
    { label: "Steam", url: "https://store.steampowered.com/" },
    { label: "Discord", url: "https://discord.com/" },
    { label: "YouTube", url: "https://www.youtube.com/" },
    { label: "X", url: "https://x.com/" },
  ],

  // "Rólunk" szekció
  about: [
    "Ide jön a csapat története: hogyan indultatok, mi hajt titeket, milyen játékokat szerettek készíteni. Két-három rövid bekezdés bőven elég.",
    "Írd le, mitől más a ti stúdiótok: a művészeti stílus, a játékmenet-filozófia, a közösséggel való kapcsolat — bármi, amire büszkék vagytok.",
  ],
  stats: [
    { value: "3", label: "Megjelent játék" },
    { value: "5", label: "Csapattag" },
    { value: "2021", label: "Alapítás éve" },
    { value: "120k+", label: "Játékos" },
  ],
  team: [
    { name: "Csapattag Neve", role: "Game Director", bio: "Rövid bemutatkozás: mivel foglalkozik, mi a kedvenc játéka.", avatar: "" },
    { name: "Csapattag Neve", role: "Lead Programmer", bio: "Rövid bemutatkozás: mivel foglalkozik, mi a kedvenc játéka.", avatar: "" },
    { name: "Csapattag Neve", role: "Art Director", bio: "Rövid bemutatkozás: mivel foglalkozik, mi a kedvenc játéka.", avatar: "" },
    { name: "Csapattag Neve", role: "Sound Designer", bio: "Rövid bemutatkozás: mivel foglalkozik, mi a kedvenc játéka.", avatar: "" },
    { name: "Csapattag Neve", role: "Community Manager", bio: "Rövid bemutatkozás: mivel foglalkozik, mi a kedvenc játéka.", avatar: "" },
  ],
};

/*
  JÁTÉK MEZŐK
  -----------
  id          egyedi azonosító (URL-ben szerepel, ékezet és szóköz nélkül)
  title       a játék címe
  tagline     egysoros szlogen
  genres      műfajok listája
  price       ár forintban
  salePrice   akciós ár (ha nincs akció: null)
  steamUrl    a játék Steam oldala
  status      "released" vagy "upcoming" (upcoming = még nem vásárolható)
  release     megjelenés dátuma (szöveg)
  platforms   platformok listája
  description hosszú leírás, bekezdésenként
  features    kiemelt jellemzők listája
  trailer     { youtube: "VIDEO_ID" }  vagy  { mp4: "videok/trailer.mp4" }  vagy  null
  cover       borítókép elérési útja (pl. "kepek/jatek1/cover.jpg"); ha üres, generált borító jelenik meg
  screenshots képek elérési útjai; ha üres, generált helykitöltők jelennek meg
  colors      a játék két fő színe (a kártya és az oldal RGB fényeihez)
*/
window.GAMES = [
  {
    id: "neon-drift",
    title: "NEON DRIFT",
    tagline: "Száguldj át egy városon, ami sosem alszik.",
    genres: ["Arcade", "Verseny"],
    price: 5990,
    salePrice: 3990,
    steamUrl: "https://store.steampowered.com/",
    status: "released",
    release: "2024. március 14.",
    platforms: ["Windows", "Steam Deck"],
    description: [
      "Ide jön a játék részletes leírása. Meséld el, miről szól, mi a játékos célja, és miért fogja imádni.",
      "A második bekezdésben írhatsz a játékmenetről, a világról vagy a történetről. Annyi bekezdést adhatsz hozzá, amennyit szeretnél.",
    ],
    features: ["Jellemző 1 — pl. 24 pálya", "Jellemző 2 — pl. online többjátékos mód", "Jellemző 3 — pl. eredeti synthwave zene", "Jellemző 4 — pl. kontroller támogatás"],
    trailer: null,
    cover: "",
    screenshots: [],
    colors: ["#00f0ff", "#ff00e5"],
  },
  {
    id: "hollow-signal",
    title: "HOLLOW SIGNAL",
    tagline: "Valami válaszolt a mélyűrből.",
    genres: ["Horror", "Kaland"],
    price: 7490,
    salePrice: null,
    steamUrl: "https://store.steampowered.com/",
    status: "released",
    release: "2025. október 31.",
    platforms: ["Windows", "Linux"],
    description: [
      "Ide jön a játék részletes leírása. Meséld el, miről szól, mi a játékos célja, és miért fogja imádni.",
      "A második bekezdésben írhatsz a játékmenetről, a világról vagy a történetről.",
    ],
    features: ["Jellemző 1", "Jellemző 2", "Jellemző 3", "Jellemző 4"],
    trailer: null,
    cover: "",
    screenshots: [],
    colors: ["#7dff00", "#00b3ff"],
  },
  {
    id: "ember-crown",
    title: "EMBER CROWN",
    tagline: "Minden birodalom hamuból épül.",
    genres: ["Akció-RPG", "Roguelike"],
    price: 8990,
    salePrice: null,
    steamUrl: "https://store.steampowered.com/",
    status: "released",
    release: "2026. június 5.",
    platforms: ["Windows"],
    description: [
      "Ide jön a játék részletes leírása. Meséld el, miről szól, mi a játékos célja, és miért fogja imádni.",
      "A második bekezdésben írhatsz a játékmenetről, a világról vagy a történetről.",
    ],
    features: ["Jellemző 1", "Jellemző 2", "Jellemző 3", "Jellemző 4"],
    trailer: null,
    cover: "",
    screenshots: [],
    colors: ["#ff7a00", "#ff0055"],
  },
  {
    id: "project-zenith",
    title: "PROJECT ZENITH",
    tagline: "A következő nagy dobásunk. Hamarosan.",
    genres: ["Sci-fi", "Shooter"],
    price: 0,
    salePrice: null,
    steamUrl: "https://store.steampowered.com/",
    status: "upcoming",
    release: "2027",
    platforms: ["Windows"],
    description: [
      "Ide jön a készülő játék leírása. Add hozzá kívánságlistához Steamen, hogy elsőként értesülj a megjelenésről!",
    ],
    features: ["Jellemző 1", "Jellemző 2", "Jellemző 3"],
    trailer: null,
    cover: "",
    screenshots: [],
    colors: ["#a855f7", "#00f0ff"],
  },
];
