# Játékstúdió weboldal

Statikus weboldal egy játékfejlesztő csapatnak: játékkatalógus, játék-adatlapok
(leírás, trailer, képgaléria), kosár, Steam linkek és csapatbemutató.
Nincs build lépés és nincs függőség — az `index.html` megnyitásával fut.

## Három dizájn-változat

Ugyanaz a tartalom és működés, három teljesen eltérő stílusban:

- `index.html` — **Neon**: sötét, RGB fényekkel, glitch címmel
- `v2/index.html` — **Pixel**: retró 8-bites játéktermi stílus
- `v3/index.html` — **Magazin**: világos, letisztult, nagy betűs editorial stílus

Mindhárom a közös `data.js` és `app.js` fájlt használja, így a tartalmat elég egy helyen
átírni. Ha választottál, a másik kettő mappája/fájljai törölhetők (a láblécben lévő
„Dizájn:” váltó linkekkel együtt).

## Tartalom szerkesztése

Minden szöveg és adat a **`data.js`** fájlban van:

- `STUDIO` — stúdió neve, szlogen, „Rólunk” szöveg, statisztikák, csapattagok, e-mail, közösségi linkek
- `GAMES` — játékok: cím, leírás, ár / akciós ár, Steam link, trailer, borító, képek, színek

Trailer megadása: `trailer: { youtube: "VIDEO_ID" }` vagy `trailer: { mp4: "videok/trailer.mp4" }`.
Képek: tedd őket pl. egy `kepek/` mappába, és add meg az útvonalat a `cover` és `screenshots` mezőkben.
Amíg nincs saját kép, az oldal generált helykitöltő grafikát mutat.

## Fizetés

Az oldalon belüli vásárlás (kosár + megrendelés) jelenleg **demo**: valódi fizetés nem történik.
Éles használathoz az `app.js` `processOrder()` függvényében kell bekötni egy fizetési
szolgáltatót (pl. Stripe, Barion, SimplePay), amihez szerveroldali rész is szükséges.

## Fájlok

- `index.html` — az oldal váza
- `styles.css` — dizájn, RGB effektek, animációk
- `app.js` — működés (kosár, adatlapok, galéria, animációk)
- `data.js` — tartalom
