(() => {
  "use strict";

  const S = window.STUDIO;
  const GAMES = window.GAMES;
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const ft = (n) => new Intl.NumberFormat("hu-HU").format(n) + " Ft";
  const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const priceOf = (g) => g.salePrice ?? g.price;
  const byId = (id) => GAMES.find((g) => g.id === id);

  /* ---------- Generált helykitöltő grafika (ha nincs saját kép) ---------- */
  function art(game, variant = 0, withTitle = true) {
    const [a, b] = game.colors || ["#00f0ff", "#ff00e5"];
    let seed = [...game.id].reduce((n, c) => n + c.charCodeAt(0), 0) + variant * 97;
    const rnd = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    let shapes = "";
    for (let i = 0; i < 9; i++) {
      const x = rnd() * 800, y = rnd() * 500, s = 40 + rnd() * 160, c = rnd() > 0.5 ? a : b;
      shapes += rnd() > 0.5
        ? `<polygon points="${x},${y - s} ${x + s},${y + s * 0.6} ${x - s},${y + s * 0.6}" fill="none" stroke="${c}" stroke-width="2" opacity="${0.15 + rnd() * 0.4}"/>`
        : `<circle cx="${x}" cy="${y}" r="${s * 0.6}" fill="${c}" opacity="${0.05 + rnd() * 0.14}"/>`;
    }
    let lines = "";
    for (let i = 0; i <= 12; i++) lines += `<line x1="${400 + (i - 6) * 26}" y1="300" x2="${400 + (i - 6) * 190}" y2="500" stroke="${a}" stroke-width="1" opacity=".35"/>`;
    for (let i = 0; i < 6; i++) lines += `<line x1="0" y1="${300 + i * i * 7}" x2="800" y2="${300 + i * i * 7}" stroke="${b}" stroke-width="1" opacity=".3"/>`;
    const title = withTitle
      ? `<text x="400" y="235" text-anchor="middle" font-family="Orbitron,Arial Black,sans-serif" font-weight="900" font-size="${Math.min(74, 900 / game.title.length)}" fill="#fff" letter-spacing="4" filter="url(#g)">${esc(game.title)}</text>`
      : `<text x="400" y="262" text-anchor="middle" font-family="Arial,sans-serif" font-weight="700" font-size="22" fill="#fff" opacity=".55" letter-spacing="6">KÉP ${variant}</text>`;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 500">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1" gradientTransform="rotate(${variant * 40} .5 .5)"><stop offset="0" stop-color="#0a0a18"/><stop offset=".55" stop-color="${b}" stop-opacity=".35"/><stop offset="1" stop-color="${a}" stop-opacity=".5"/></linearGradient>
        <radialGradient id="sun" cx=".5" cy=".6" r=".4"><stop offset="0" stop-color="${a}" stop-opacity=".7"/><stop offset="1" stop-color="${a}" stop-opacity="0"/></radialGradient>
        <filter id="g"><feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
      </defs>
      <rect width="800" height="500" fill="#07070d"/><rect width="800" height="500" fill="url(#bg)"/><rect width="800" height="500" fill="url(#sun)"/>
      ${shapes}${lines}${title}</svg>`;
    return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
  }
  const coverOf = (g) => g.cover || art(g, 0);
  const shotsOf = (g) => (g.screenshots && g.screenshots.length ? g.screenshots : [1, 2, 3, 4, 5, 6].map((i) => art(g, i, false)));

  /* ---------- Stúdió adatok ---------- */
  const full = `${S.name} ${S.nameSuffix || ""}`.trim();
  document.title = `${full} — ${S.tagline}`;
  $$('[data-studio="name"]').forEach((e) => (e.textContent = S.name));
  $$('[data-studio="suffix"]').forEach((e) => (e.textContent = S.nameSuffix || ""));
  $$('[data-studio="full"]').forEach((e) => (e.textContent = full));
  $$('[data-studio="tagline"]').forEach((e) => (e.textContent = S.tagline));
  $$('[data-studio="heroText"]').forEach((e) => (e.textContent = S.heroText));
  const heroTitle = $("#hero-title");
  heroTitle.textContent = S.name;
  heroTitle.dataset.text = S.name;
  $("#year").textContent = new Date().getFullYear();
  $("#about-text").innerHTML = S.about.map((p) => `<p>${esc(p)}</p>`).join("");
  $("#stats").innerHTML = S.stats.map((s) => `<div class="stat"><b>${esc(s.value)}</b><span>${esc(s.label)}</span></div>`).join("");
  $("#team-grid").innerHTML = S.team
    .map((m) => {
      const initials = m.name.split(/\s+/).map((w) => w[0]).slice(0, 2).join("").toUpperCase();
      const av = m.avatar ? `<img src="${esc(m.avatar)}" alt="${esc(m.name)}" loading="lazy">` : `<span>${esc(initials)}</span>`;
      return `<article class="member reveal"><div class="avatar">${av}</div><h3>${esc(m.name)}</h3><p class="role">${esc(m.role)}</p><p>${esc(m.bio)}</p></article>`;
    })
    .join("");
  $("#contact-mail").href = `mailto:${S.email}`;
  $("#socials").innerHTML = S.socials.map((s) => `<a href="${esc(s.url)}" target="_blank" rel="noopener">${esc(s.label)}</a>`).join("");
  const words = [...GAMES.map((g) => g.title), "Indie", "Made with ♥", S.name];
  $("#marquee").innerHTML = [...words, ...words, ...words, ...words].map((w) => `<span>${esc(w)}</span>`).join("");

  /* ---------- Kosár ---------- */
  const KEY = "studio-cart-v1";
  let cart = [];
  try { cart = JSON.parse(localStorage.getItem(KEY) || "[]").filter((id) => byId(id)); } catch { cart = []; }
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(cart)); } catch {} };
  const total = () => cart.reduce((n, id) => n + priceOf(byId(id)), 0);

  let toastTimer;
  function toast(msg) {
    const t = $("#toast");
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove("show"), 2400);
  }

  function addToCart(id) {
    const g = byId(id);
    if (!g || g.status !== "released") return;
    if (cart.includes(id)) return openCart();
    cart.push(id);
    save();
    renderCart(true);
    toast(`${g.title} a kosárba került`);
  }
  function removeFromCart(id) {
    cart = cart.filter((x) => x !== id);
    save();
    renderCart();
  }

  function renderCart(bump) {
    const count = $("#cart-count");
    count.textContent = cart.length;
    count.hidden = cart.length === 0;
    if (bump) { count.classList.remove("bump"); void count.offsetWidth; count.classList.add("bump"); }
    $("#cart-items").innerHTML = cart.length
      ? cart.map((id) => {
          const g = byId(id);
          return `<div class="cart-item"><img src="${esc(coverOf(g))}" alt=""><div><h4>${esc(g.title)}</h4><div class="p">${ft(priceOf(g))}</div></div><button class="icon-btn" data-remove="${esc(id)}" aria-label="${esc(g.title)} eltávolítása">✕</button></div>`;
        }).join("")
      : `<div class="empty"><b>A kosarad üres</b>Nézz körül a játékaink között!</div>`;
    $("#cart-total").textContent = ft(total());
    $("#checkout-open").disabled = cart.length === 0;
    // kártyák és adatlap gombjainak frissítése
    $$("[data-add]").forEach((b) => {
      const inCart = cart.includes(b.dataset.add);
      if (b.classList.contains("add-btn")) {
        b.classList.toggle("in-cart", inCart);
        b.textContent = inCart ? "✓" : "+";
        b.setAttribute("aria-label", inCart ? "Már a kosárban — kosár megnyitása" : "Kosárba");
      } else {
        $("span", b).textContent = inCart ? "✓ A kosárban — megnyitás" : "Kosárba teszem";
      }
    });
  }

  const drawer = $("#cart-drawer"), overlay = $("#overlay");
  function openCart() {
    drawer.classList.add("open");
    drawer.setAttribute("aria-hidden", "false");
    overlay.hidden = false;
    document.body.classList.add("locked");
    $("#cart-close").focus();
  }
  function closeCart() {
    drawer.classList.remove("open");
    drawer.setAttribute("aria-hidden", "true");
    overlay.hidden = true;
    document.body.classList.remove("locked");
  }
  $("#cart-open").addEventListener("click", openCart);
  $("#cart-close").addEventListener("click", closeCart);
  overlay.addEventListener("click", closeCart);

  /* ---------- Fizetés ----------
     FONTOS: ez jelenleg DEMO. Valódi fizetéshez kösd be itt a fizetési
     szolgáltatót (pl. Stripe Checkout, Barion, SimplePay, PayPal):
     a processOrder() függvényben küldd el a rendelést a saját szerverednek,
     ami létrehozza a fizetési munkamenetet, majd irányítsd oda a vásárlót. */
  async function processOrder(order) {
    console.info("Rendelés (demo):", order);
    return { ok: true, demo: true };
  }

  const modal = $("#checkout-modal");
  function openCheckout() {
    if (!cart.length) return;
    closeCart();
    $("#checkout-content").innerHTML = `
      <h3 id="checkout-title">Fizetés</h3>
      <p class="sub">A játék(ok) letöltőkulcsát e-mailben küldjük el.</p>
      <ul class="order-list">
        ${cart.map((id) => `<li><span>${esc(byId(id).title)}</span><span>${ft(priceOf(byId(id)))}</span></li>`).join("")}
        <li><b>Összesen</b><span class="sum">${ft(total())}</span></li>
      </ul>
      <form id="checkout-form">
        <div class="field"><label for="co-name">Név</label><input id="co-name" name="name" required autocomplete="name"></div>
        <div class="field"><label for="co-email">E-mail cím</label><input id="co-email" name="email" type="email" required autocomplete="email"></div>
        <button class="btn btn-rgb btn-block" type="submit"><span>Megrendelés — ${ft(total())}</span></button>
        <p class="demo-note">⚠ Demo mód: fizetési szolgáltató még nincs bekötve, valódi terhelés nem történik.</p>
      </form>`;
    modal.hidden = false;
    document.body.classList.add("locked");
    $("#co-name").focus();
    $("#checkout-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const data = new FormData(e.target);
      const order = { name: data.get("name"), email: data.get("email"), items: [...cart], total: total() };
      const res = await processOrder(order);
      if (!res.ok) return toast("Hiba történt, próbáld újra.");
      cart = [];
      save();
      renderCart();
      $("#checkout-content").innerHTML = `
        <div class="success">
          <div class="check">✓</div>
          <h3 style="padding:0">Köszönjük, ${esc(order.name)}!</h3>
          <p class="sub">A visszaigazolást ide küldenénk: <b>${esc(order.email)}</b></p>
          ${res.demo ? `<p class="demo-note">Ez egy demo rendelés volt — valódi fizetés és e-mail küldés nem történt.</p>` : ""}
          <button class="btn btn-ghost" data-close-modal style="margin-top:1.2rem">Bezárás</button>
        </div>`;
    });
  }
  function closeModal() {
    modal.hidden = true;
    document.body.classList.remove("locked");
  }
  $("#checkout-open").addEventListener("click", openCheckout);
  modal.addEventListener("click", (e) => {
    if (e.target === modal || e.target.closest("[data-close-modal]")) closeModal();
  });

  /* ---------- Játék lista ---------- */
  let activeGenre = "Mind";
  const genres = ["Mind", ...new Set(GAMES.flatMap((g) => g.genres))];

  function priceHTML(g) {
    if (g.status !== "released") return `<span class="now">Hamarosan</span>`;
    if (g.salePrice != null && g.salePrice < g.price) {
      const off = Math.round((1 - g.salePrice / g.price) * 100);
      return `<span class="now">${ft(g.salePrice)}</span><s>${ft(g.price)}</s><span class="off">-${off}%</span>`;
    }
    return `<span class="now">${g.price === 0 ? "Ingyenes" : ft(g.price)}</span>`;
  }

  function renderFilters() {
    $("#filters").innerHTML = genres
      .map((g) => `<button class="chip${g === activeGenre ? " active" : ""}" data-genre="${esc(g)}" aria-pressed="${g === activeGenre}">${esc(g)}</button>`)
      .join("");
  }
  function renderGames() {
    const list = GAMES.filter((g) => activeGenre === "Mind" || g.genres.includes(activeGenre));
    $("#games-grid").innerHTML = list
      .map((g) => {
        const sale = g.status === "released" && g.salePrice != null && g.salePrice < g.price;
        const badge = g.status !== "released" ? `<span class="badge soon">Hamarosan</span>` : sale ? `<span class="badge">Akció</span>` : "";
        const action = g.status === "released"
          ? `<button class="add-btn" data-add="${esc(g.id)}" aria-label="Kosárba">+</button>`
          : `<a class="btn btn-steam" style="padding:.6em 1em;font-size:.65rem" href="${esc(g.steamUrl)}" target="_blank" rel="noopener">Kívánságlista</a>`;
        return `<article class="game-card reveal" style="--g1:${esc(g.colors[0])};--g2:${esc(g.colors[1])}">
          <div class="card-inner">
            <div class="card-cover">${badge}<img src="${esc(coverOf(g))}" alt="${esc(g.title)} borítókép" loading="lazy"></div>
            <div class="card-body">
              <div class="card-genres">${g.genres.map(esc).join(" · ")}</div>
              <h3><a href="#/game/${esc(g.id)}">${esc(g.title)}</a></h3>
              <p class="card-tag">${esc(g.tagline)}</p>
              <div class="card-foot"><div class="price">${priceHTML(g)}</div>${action}</div>
            </div>
          </div>
        </article>`;
      })
      .join("");
    renderCart();
    observeReveals();
    bindTilt();
  }
  $("#filters").addEventListener("click", (e) => {
    const b = e.target.closest("[data-genre]");
    if (!b) return;
    activeGenre = b.dataset.genre;
    renderFilters();
    renderGames();
  });

  /* ---------- Játék adatlap ---------- */
  let lightboxShots = [], lightboxIndex = 0;

  function trailerHTML(g) {
    const t = g.trailer;
    if (t && t.youtube) return `<iframe src="https://www.youtube-nocookie.com/embed/${encodeURIComponent(t.youtube)}?rel=0" title="${esc(g.title)} trailer" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen loading="lazy"></iframe>`;
    if (t && t.mp4) return `<video src="${esc(t.mp4)}" controls preload="metadata" poster="${esc(coverOf(g))}"></video>`;
    return `<img src="${esc(coverOf(g))}" alt=""><div class="trailer-empty"><div class="play"></div><b>Trailer hamarosan</b><small>Ide kerül a játék trailer videója (YouTube vagy MP4).</small></div>`;
  }

  function renderGame(g) {
    const view = $("#game-view");
    view.style.setProperty("--g1", g.colors[0]);
    view.style.setProperty("--g2", g.colors[1]);
    const released = g.status === "released";
    const shots = shotsOf(g);
    lightboxShots = shots;
    view.innerHTML = `
      <section class="game-hero">
        <div class="game-hero-bg"><img src="${esc(coverOf(g))}" alt=""></div>
        <div class="game-hero-inner">
          <a class="back" href="#games">← Vissza a játékokhoz</a>
          <h1>${esc(g.title)}</h1>
          <p class="tag">${esc(g.tagline)}</p>
          <div class="meta">${g.genres.map((x) => `<span>${esc(x)}</span>`).join("")}</div>
        </div>
      </section>
      <div class="game-layout">
        <div class="game-main">
          <section class="block"><h2>Trailer</h2><div class="trailer rgb-border"><div class="trailer-frame">${trailerHTML(g)}</div></div></section>
          <section class="block desc"><h2>A játékról</h2>${g.description.map((p) => `<p>${esc(p)}</p>`).join("")}</section>
          ${g.features && g.features.length ? `<section class="block"><h2>Jellemzők</h2><ul class="features">${g.features.map((f) => `<li>${esc(f)}</li>`).join("")}</ul></section>` : ""}
          <section class="block"><h2>Képek</h2><div class="gallery">${shots.map((s, i) => `<button data-shot="${i}" aria-label="${i + 1}. kép megnyitása"><img src="${esc(s)}" alt="${esc(g.title)} — ${i + 1}. kép" loading="lazy"></button>`).join("")}</div></section>
        </div>
        <aside class="buy-box rgb-border">
          <img class="buy-cover" src="${esc(coverOf(g))}" alt="">
          <div class="buy-price">${priceHTML(g)}</div>
          ${released ? `<button class="btn btn-rgb btn-block" data-add="${esc(g.id)}"><span>Kosárba teszem</span></button><div class="or">vagy</div>` : ""}
          <a class="btn btn-steam btn-block" href="${esc(g.steamUrl)}" target="_blank" rel="noopener">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true"><path d="M12 2a10 10 0 0 0-9.96 9.1l5.36 2.2a2.8 2.8 0 0 1 1.75-.5l2.4-3.46v-.05a3.78 3.78 0 1 1 3.78 3.78h-.09l-3.4 2.43a2.84 2.84 0 0 1-5.63.6L2.4 14.5A10 10 0 1 0 12 2Zm-3.7 15.17-1.22-.5a2.13 2.13 0 1 0 1.17-2.9l1.27.52a1.57 1.57 0 0 1-1.21 2.9Zm9.55-7.88a2.52 2.52 0 1 0-2.52 2.52 2.52 2.52 0 0 0 2.52-2.52Zm-4.4 0a1.89 1.89 0 1 1 1.88 1.89 1.89 1.89 0 0 1-1.89-1.9Z"/></svg>
            ${released ? "Megveszem Steamen" : "Kívánságlistára Steamen"}
          </a>
          <ul class="buy-info">
            <li><span>Megjelenés</span><b>${esc(g.release)}</b></li>
            <li><span>Platform</span><b>${g.platforms.map(esc).join(", ")}</b></li>
            <li><span>Műfaj</span><b>${g.genres.map(esc).join(", ")}</b></li>
            <li><span>Fejlesztő</span><b>${esc(full)}</b></li>
          </ul>
        </aside>
      </div>`;
    renderCart();
  }

  /* ---------- Képnézegető ---------- */
  const lightbox = $("#lightbox");
  function showShot(i) {
    lightboxIndex = (i + lightboxShots.length) % lightboxShots.length;
    $("#lightbox-img").src = lightboxShots[lightboxIndex];
    $("#lightbox-img").alt = `${lightboxIndex + 1}. kép`;
  }
  function openLightbox(i) { showShot(i); lightbox.hidden = false; document.body.classList.add("locked"); }
  function closeLightbox() { lightbox.hidden = true; document.body.classList.remove("locked"); }
  $("#lightbox-close").addEventListener("click", closeLightbox);
  $("#lightbox-prev").addEventListener("click", () => showShot(lightboxIndex - 1));
  $("#lightbox-next").addEventListener("click", () => showShot(lightboxIndex + 1));
  lightbox.addEventListener("click", (e) => { if (e.target === lightbox) closeLightbox(); });

  /* ---------- Globális kattintás- és billentyűkezelés ---------- */
  document.addEventListener("click", (e) => {
    const add = e.target.closest("[data-add]");
    if (add) return addToCart(add.dataset.add);
    const rem = e.target.closest("[data-remove]");
    if (rem) return removeFromCart(rem.dataset.remove);
    const shot = e.target.closest("[data-shot]");
    if (shot) return openLightbox(+shot.dataset.shot);
  });
  document.addEventListener("keydown", (e) => {
    if (!lightbox.hidden) {
      if (e.key === "Escape") closeLightbox();
      if (e.key === "ArrowLeft") showShot(lightboxIndex - 1);
      if (e.key === "ArrowRight") showShot(lightboxIndex + 1);
    } else if (e.key === "Escape") {
      if (!modal.hidden) closeModal();
      else if (drawer.classList.contains("open")) closeCart();
    }
  });

  /* ---------- Útvonalkezelés (#/game/<id>) ---------- */
  const home = $("#home-view"), gameView = $("#game-view");
  function route() {
    const h = location.hash;
    const m = h.match(/^#\/game\/(.+)$/);
    const g = m && byId(decodeURIComponent(m[1]));
    closeMenu();
    if (g) {
      home.hidden = true;
      gameView.hidden = false;
      renderGame(g);
      document.title = `${g.title} — ${full}`;
      window.scrollTo(0, 0);
    } else {
      const wasGame = !gameView.hidden;
      gameView.hidden = true;
      gameView.innerHTML = ""; // leállítja a futó trailert
      home.hidden = false;
      document.title = `${full} — ${S.tagline}`;
      if (m) return history.replaceState(null, "", "#games");
      if (wasGame) {
        const target = h.length > 1 && document.getElementById(h.slice(1));
        if (target) target.scrollIntoView({ behavior: "auto" });
        else window.scrollTo(0, 0);
      }
    }
  }
  window.addEventListener("hashchange", route);

  /* ---------- Navigáció ---------- */
  const nav = $("#nav"), burger = $("#burger"), links = $("#nav-links");
  const onScroll = () => nav.classList.toggle("scrolled", window.scrollY > 20);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
  function closeMenu() { links.classList.remove("open"); burger.setAttribute("aria-expanded", "false"); }
  burger.addEventListener("click", () => {
    const open = links.classList.toggle("open");
    burger.setAttribute("aria-expanded", String(open));
  });
  links.addEventListener("click", (e) => { if (e.target.closest("a")) closeMenu(); });

  /* ---------- Megjelenési animációk ---------- */
  const io = "IntersectionObserver" in window
    ? new IntersectionObserver((entries) => entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); } }), { threshold: 0.12 })
    : null;
  function observeReveals() {
    $$(".reveal:not(.in)").forEach((el, i) => {
      if (!io) return el.classList.add("in");
      el.style.transitionDelay = `${(i % 4) * 70}ms`;
      io.observe(el);
    });
  }

  /* ---------- 3D kártya döntés + kurzorfény ---------- */
  const fine = matchMedia("(hover: hover) and (pointer: fine)").matches;
  function bindTilt() {
    if (!fine || reduced) return;
    $$(".game-card").forEach((card) => {
      card.addEventListener("pointermove", (e) => {
        const r = card.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        card.style.transform = `perspective(900px) rotateY(${x * 9}deg) rotateX(${-y * 9}deg) translateY(-6px)`;
      });
      card.addEventListener("pointerleave", () => (card.style.transform = ""));
    });
  }
  if (fine) {
    const glow = $(".cursor-glow");
    window.addEventListener("pointermove", (e) => {
      glow.style.setProperty("--mx", e.clientX + "px");
      glow.style.setProperty("--my", e.clientY + "px");
    }, { passive: true });
  }

  /* ---------- Hero részecske-háló ---------- */
  (function heroCanvas() {
    const c = $("#hero-canvas"), ctx = c.getContext("2d");
    let w, h, pts = [], raf = 0, hue = 180;
    const mouse = { x: -999, y: -999 };
    function resize() {
      const dpr = Math.min(devicePixelRatio || 1, 2);
      w = c.clientWidth; h = c.clientHeight;
      c.width = w * dpr; c.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const n = Math.min(110, Math.floor((w * h) / 14000));
      pts = Array.from({ length: n }, () => ({ x: Math.random() * w, y: Math.random() * h, vx: (Math.random() - 0.5) * 0.5, vy: (Math.random() - 0.5) * 0.5 }));
    }
    function frame() {
      ctx.clearRect(0, 0, w, h);
      hue = (hue + 0.3) % 360;
      for (let i = 0; i < pts.length; i++) {
        const p = pts[i];
        p.x += p.vx; p.y += p.vy;
        if (p.x < 0 || p.x > w) p.vx *= -1;
        if (p.y < 0 || p.y > h) p.vy *= -1;
        const dm = Math.hypot(p.x - mouse.x, p.y - mouse.y);
        if (dm < 140) { p.x += ((p.x - mouse.x) / dm) * 1.2; p.y += ((p.y - mouse.y) / dm) * 1.2; }
        const ph = (hue + (p.x / w) * 160) % 360;
        ctx.fillStyle = `hsla(${ph},100%,65%,.9)`;
        ctx.beginPath(); ctx.arc(p.x, p.y, 1.8, 0, 6.283); ctx.fill();
        for (let j = i + 1; j < pts.length; j++) {
          const q = pts[j], d = Math.hypot(p.x - q.x, p.y - q.y);
          if (d < 130) {
            ctx.strokeStyle = `hsla(${ph},100%,60%,${(1 - d / 130) * 0.35})`;
            ctx.lineWidth = 1;
            ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(q.x, q.y); ctx.stroke();
          }
        }
      }
      if (!reduced) raf = requestAnimationFrame(frame);
    }
    const hero = c.parentElement;
    hero.addEventListener("pointermove", (e) => { const r = c.getBoundingClientRect(); mouse.x = e.clientX - r.left; mouse.y = e.clientY - r.top; });
    hero.addEventListener("pointerleave", () => { mouse.x = mouse.y = -999; });
    window.addEventListener("resize", () => { resize(); if (reduced) frame(); });
    // csak akkor fut, ha látszik
    if ("IntersectionObserver" in window && !reduced) {
      new IntersectionObserver(([en]) => {
        cancelAnimationFrame(raf);
        if (en.isIntersecting) raf = requestAnimationFrame(frame);
      }).observe(c);
    }
    resize();
    frame();
  })();

  /* ---------- Indítás ---------- */
  renderFilters();
  renderGames();
  renderCart();
  observeReveals();
  route();
})();
