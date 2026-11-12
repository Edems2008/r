/* Konzol dizájn — saját játék-adatlap: teljes képernyős fejezetek, vízszintes
   képsáv és alul rögzített vásárlósáv (nincs oldalsó oszlop). */
(() => {
  "use strict";
  const T = (window.THEME = window.THEME || {});
  let observer = null;

  T.renderGame = (g, A) => {
    const e = A.esc;
    const chapters = [
      ["gp-trailer", "Trailer"],
      ["gp-story", "A játékról"],
      ["gp-gallery", "Képek"],
      ["gp-specs", "Adatok"],
    ];
    const head = (i) => `<header class="gp-head"><span class="gp-num">0${i + 1}</span><h2>${chapters[i][1]}</h2></header>`;
    const specs = [
      ["Megjelenés", g.release],
      ["Platform", g.platforms.join(", ")],
      ["Műfaj", g.genres.join(", ")],
      ["Fejlesztő", A.studio],
    ];
    return `
      <section class="gp-cover">
        <div class="gp-bg"><img src="${e(A.bgOf(g))}" alt=""></div>
        <a class="back" href="#top">← Könyvtár</a>
        <div class="gp-cover-text">
          <p class="stage-genres">${g.genres.map(e).join(" · ")}${A.released ? "" : " <em>Hamarosan</em>"}</p>
          <h1>${e(g.title)}</h1>
          <p class="gp-tag">${e(g.tagline)}</p>
        </div>
        <button class="gp-scroll" data-goto="gp-trailer" aria-label="Tovább a trailerhez"><span></span></button>
      </section>

      <nav class="gp-chapters" aria-label="Fejezetek">
        ${chapters.map(([id, label], i) => `<button data-goto="${id}"><i>0${i + 1}</i><span>${label}</span></button>`).join("")}
      </nav>

      <section class="gp-chapter gp-cinema" id="gp-trailer">
        ${head(0)}
        <div class="gp-screen"><div class="trailer-frame">${A.trailer}</div></div>
      </section>

      <section class="gp-chapter" id="gp-story">
        ${head(1)}
        <div class="gp-lead">${g.description.map((p) => `<p>${e(p)}</p>`).join("")}</div>
        ${g.features && g.features.length ? `<ol class="gp-features">${g.features.map((f) => `<li>${e(f)}</li>`).join("")}</ol>` : ""}
      </section>

      <section class="gp-chapter gp-wide" id="gp-gallery">
        <div class="gp-head-row">
          ${head(2)}
          <div class="gp-arrows"><button class="icon-btn" data-strip="-1" aria-label="Előző képek">‹</button><button class="icon-btn" data-strip="1" aria-label="Következő képek">›</button></div>
        </div>
        <div class="gp-strip">
          ${A.shots.map((s, i) => `<button data-shot="${i}" aria-label="${i + 1}. kép megnyitása"><img src="${e(s)}" alt="${e(g.title)} — ${i + 1}. kép" loading="lazy"></button>`).join("")}
        </div>
      </section>

      <section class="gp-chapter" id="gp-specs">
        ${head(3)}
        <dl class="gp-specs">${specs.map(([k, v]) => `<div><dt>${k}</dt><dd>${e(v)}</dd></div>`).join("")}</dl>
      </section>

      <div class="gp-dock">
        <img src="${e(A.coverOf(g))}" alt="">
        <div class="gp-dock-text"><b>${e(g.title)}</b><div class="price">${A.priceHTML(g)}</div></div>
        <div class="gp-dock-actions">
          ${A.released ? `<button class="btn btn-rgb" data-add="${e(g.id)}"><span>Kosárba teszem</span></button>` : ""}
          <a class="btn btn-steam" href="${e(g.steamUrl)}" target="_blank" rel="noopener">${A.released ? "Steam" : "Kívánságlista Steamen"}</a>
        </div>
      </div>`;
  };

  // aktív fejezet kiemelése az oldalsó fejezetjelzőn
  T.afterRenderGame = (view) => {
    if (observer) observer.disconnect();
    if (!("IntersectionObserver" in window)) return;
    const buttons = [...view.querySelectorAll(".gp-chapters button")];
    observer = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (en.isIntersecting) buttons.forEach((b) => b.classList.toggle("active", b.dataset.goto === en.target.id));
      });
    }, { rootMargin: "-45% 0px -45% 0px" });
    view.querySelectorAll(".gp-chapter").forEach((s) => observer.observe(s));
  };

  document.addEventListener("click", (ev) => {
    const smooth = matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
    const go = ev.target.closest("[data-goto]");
    if (go) {
      const el = document.getElementById(go.dataset.goto);
      if (el) el.scrollIntoView({ behavior: smooth, block: "start" });
      return;
    }
    const arrow = ev.target.closest("[data-strip]");
    if (arrow) {
      const strip = document.querySelector(".gp-strip");
      if (strip) strip.scrollBy({ left: +arrow.dataset.strip * strip.clientWidth * 0.8, behavior: smooth });
    }
  });
})();
