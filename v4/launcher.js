/* Konzol dizájn — indítóképernyő: a kiválasztott játék tölti ki a képernyőt */
(() => {
  "use strict";
  const A = window.APP, GAMES = window.GAMES, esc = A.esc;
  const $ = (s) => document.querySelector(s);
  const rail = $("#rail"), info = $("#stage-info"), stage = $("#top"), home = $("#home-view");
  const layers = [$("#stage-bg-a"), $("#stage-bg-b")];
  const progress = $("#progress");
  const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const AUTO_MS = 9000;
  let current = -1, layer = 0, timer = 0, paused = false;

  rail.innerHTML = GAMES.map((g, i) =>
    `<button class="tile" role="option" data-i="${i}" aria-selected="false" style="--g1:${esc(g.colors[0])};--g2:${esc(g.colors[1])}">
      <img src="${esc(A.bgOf(g))}" alt=""><span>${esc(g.title)}</span>
    </button>`).join("");
  const tiles = [...rail.children];

  function select(i, user) {
    i = (i + GAMES.length) % GAMES.length;
    if (i === current) return;
    current = i;
    const g = GAMES[i], released = g.status === "released";
    document.documentElement.style.setProperty("--g1", g.colors[0]);
    document.documentElement.style.setProperty("--g2", g.colors[1]);

    layer = 1 - layer;
    layers[layer].src = A.bgOf(g);
    layers[layer].classList.add("on");
    layers[1 - layer].classList.remove("on");

    tiles.forEach((t, j) => { t.classList.toggle("active", j === i); t.setAttribute("aria-selected", String(j === i)); });
    const t = tiles[i];
    rail.scrollTo({ left: t.offsetLeft - (rail.clientWidth - t.offsetWidth) / 2, behavior: reduced ? "auto" : "smooth" });

    info.innerHTML = `
      <p class="stage-genres">${g.genres.map(esc).join(" · ")}${released ? "" : ` <em>Hamarosan</em>`}</p>
      <h1>${esc(g.title)}</h1>
      <p class="stage-tag">${esc(g.tagline)}</p>
      <div class="stage-price price">${A.priceHTML(g)}</div>
      <div class="stage-actions">
        ${released ? `<button class="btn btn-rgb" data-add="${esc(g.id)}"><span>Kosárba teszem</span></button>` : ""}
        <a class="btn btn-steam" href="${esc(g.steamUrl)}" target="_blank" rel="noopener">${released ? "Steam" : "Kívánságlista Steamen"}</a>
        <a class="btn btn-ghost" href="#/game/${esc(g.id)}">Részletek, trailer, képek →</a>
      </div>`;
    info.classList.remove("in"); void info.offsetWidth; info.classList.add("in");
    A.renderCart();
    if (user) paused = true; // ha a látogató maga választ, nem lapozunk tovább helyette
    restart();
  }

  function restart() {
    clearTimeout(timer);
    progress.style.transition = "none";
    progress.style.width = "0";
    if (reduced || paused || GAMES.length < 2) return;
    void progress.offsetWidth;
    progress.style.transition = `width ${AUTO_MS}ms linear`;
    progress.style.width = "100%";
    timer = setTimeout(() => select(current + 1), AUTO_MS);
  }

  rail.addEventListener("click", (e) => {
    const t = e.target.closest(".tile");
    if (!t) return;
    const i = +t.dataset.i;
    if (i === current) location.hash = "#/game/" + GAMES[i].id; // második kattintás: megnyitás
    else select(i, true);
  });

  document.addEventListener("keydown", (e) => {
    if (home.hidden || document.body.classList.contains("locked")) return;
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
    if (window.scrollY > window.innerHeight * 0.5) return;
    if (e.key === "ArrowRight") { e.preventDefault(); select(current + 1, true); }
    else if (e.key === "ArrowLeft") { e.preventDefault(); select(current - 1, true); }
    else if (e.key === "Enter" && !e.target.closest("a,button")) location.hash = "#/game/" + GAMES[current].id;
  });

  // érintőképernyőn: húzás balra/jobbra a háttéren
  let x0 = null;
  stage.addEventListener("touchstart", (e) => { x0 = e.target.closest(".rail") ? null : e.touches[0].clientX; }, { passive: true });
  stage.addEventListener("touchend", (e) => {
    if (x0 === null) return;
    const dx = e.changedTouches[0].clientX - x0;
    if (Math.abs(dx) > 60) select(current + (dx < 0 ? 1 : -1), true);
    x0 = null;
  }, { passive: true });

  const clock = $("#clock");
  const tick = () => { clock.textContent = new Date().toLocaleTimeString("hu-HU", { hour: "2-digit", minute: "2-digit" }); };
  tick();
  setInterval(tick, 20000);

  const first = GAMES.findIndex((g) => g.status === "released");
  select(first < 0 ? 0 : first);
})();
