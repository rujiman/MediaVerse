// MediaVerse: página de descarga.
// 1. Elige el botón de descarga según el sistema (Windows / Linux / móvil).
// 2. Muestra la versión actual, leída de la última Release de GitHub.
// 3. Pestañas de instalación, tráiler y cabecera.

const REPO = "rujiman/MediaVerse";

// --- Entrada del hero (un solo momento al cargar) y parallax suave de las tarjetas
document.documentElement.classList.add("js");
(function setupHero() {
  const hero = document.querySelector("[data-hero]");
  if (!hero) return;
  const go = () => requestAnimationFrame(() => hero.classList.add("is-in"));
  const img = hero.querySelector(".app img");
  Promise.all([
    document.fonts ? document.fonts.ready : Promise.resolve(),
    img && !img.complete ? new Promise((r) => { img.onload = img.onerror = r; }) : Promise.resolve(),
  ]).then(go);
  setTimeout(go, 1500); // por si algo tarda: nunca se queda en blanco

  // Parallax con inercia (solo ratón y sin "reducir movimiento")
  const fine = matchMedia("(hover: hover) and (pointer: fine)").matches;
  const calm = matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!fine || calm) return;
  const floats = [...hero.querySelectorAll(".float")];
  let tx = 0, ty = 0, x = 0, y = 0, raf = 0;
  const loop = () => {
    x += (tx - x) * 0.08; y += (ty - y) * 0.08;
    floats.forEach((f) => {
      const d = Number(f.dataset.depth || 12);
      f.style.transform = `translate3d(${x * d}px, ${y * d}px, 0)`;
    });
    raf = Math.abs(tx - x) + Math.abs(ty - y) > 0.001 ? requestAnimationFrame(loop) : 0;
  };
  hero.addEventListener("pointermove", (e) => {
    const r = hero.getBoundingClientRect();
    tx = (e.clientX - r.left) / r.width - 0.5;
    ty = (e.clientY - r.top) / r.height - 0.5;
    if (!raf) raf = requestAnimationFrame(loop);
  });
  hero.addEventListener("pointerleave", () => { tx = 0; ty = 0; if (!raf) raf = requestAnimationFrame(loop); });
})();
const DL = `https://github.com/${REPO}/releases/latest/download/`;
const FILES = {
  windows: { url: DL + "MediaVerse-portable.zip", label: "Descargar para Windows" },
  linux:   { url: DL + "MediaVerse-linux.tar.gz", label: "Descargar para Linux" },
};

function detectOS() {
  const ua = navigator.userAgent;
  if (/Android|iPhone|iPad|iPod/i.test(ua) || (navigator.maxTouchPoints > 1 && /Macintosh/.test(ua))) return "mobile";
  if (/Linux|X11|CrOS/i.test(ua)) return "linux";
  if (/Mac/i.test(ua)) return "mac";
  return "windows";
}

// --- Botón de descarga
(function setupDownload() {
  const os = detectOS();
  const primary = document.querySelector("[data-primary]");
  const label = document.querySelector("[data-primary-label]");
  const alt = document.querySelector("[data-alt]");
  const download = document.querySelector("[data-download]");
  const mobile = document.querySelector("[data-mobile]");

  if (os === "mobile") {
    download.hidden = true;
    mobile.hidden = false;
    document.querySelector("[data-copy]").addEventListener("click", async (e) => {
      try {
        await navigator.clipboard.writeText(location.href);
        e.target.textContent = "Enlace copiado";
      } catch {
        e.target.textContent = location.href;
      }
    });
    return;
  }

  if (os === "linux") {
    primary.href = FILES.linux.url;
    label.textContent = FILES.linux.label;
    alt.innerHTML = `¿Usas Windows? <a href="${FILES.windows.url}">Descarga la versión para Windows</a>.`;
    selectTab("linux");
  } else if (os === "mac") {
    alt.innerHTML = `Aún no hay versión para Mac. También está <a href="${FILES.linux.url}">la versión para Linux</a>.`;
  }
})();

// --- Versión actual desde GitHub
(async function showVersion() {
  const el = document.querySelector("[data-version]");
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`);
    if (!res.ok) return;
    const data = await res.json();
    if (data.tag_name) el.textContent = "Versión " + data.tag_name.replace(/^v/i, "");
  } catch { /* sin red: se queda "Última versión" */ }
})();

// --- Pestañas Windows / Linux
function selectTab(name) {
  document.querySelectorAll("[data-tab]").forEach((tab) => {
    const on = tab.dataset.tab === name;
    tab.setAttribute("aria-selected", on);
    tab.tabIndex = on ? 0 : -1;
    document.getElementById(tab.getAttribute("aria-controls")).hidden = !on;
  });
}
document.querySelectorAll("[data-tab]").forEach((tab, i, all) => {
  tab.addEventListener("click", () => selectTab(tab.dataset.tab));
  tab.addEventListener("keydown", (e) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    const next = all[(i + (e.key === "ArrowRight" ? 1 : all.length - 1)) % all.length];
    selectTab(next.dataset.tab);
    next.focus();
  });
});

// --- Tráiler: se enseña solo cuando assets/trailer.mp4 existe
(function setupTrailer() {
  const frame = document.querySelector("[data-video]");
  const video = frame.querySelector("video");
  video.addEventListener("loadedmetadata", () => frame.classList.add("ready"));
})();

// --- Capturas: si aún no hay imagen, se queda el fondo de color
document.querySelectorAll(".shot img").forEach((img) => {
  img.addEventListener("error", () => img.remove());
  if (img.complete && img.naturalWidth === 0) img.remove();
});

// --- Línea bajo la cabecera al hacer scroll
const header = document.querySelector(".top");
addEventListener("scroll", () => header.classList.toggle("scrolled", scrollY > 8), { passive: true });

// --- Demo de Mi biblioteca / Favoritos: misma pinta y comportamiento que la app
//     (estado, filtros por tipo, carpetas con mosaico, "Sin carpeta" y arrastrar).
(function setupDemo() {
  const root = document.querySelector("[data-demo]");
  if (!root) return;

  const TYPES = {
    game:   { badge: "VIDEOJUEGO", plural: "Juegos" },
    anime:  { badge: "ANIME",      plural: "Anime" },
    series: { badge: "SERIE",      plural: "Series" },
    movie:  { badge: "PELÍCULA",   plural: "Películas" },
    music:  { badge: "MÚSICA",     plural: "Música" },
    book:   { badge: "LIBRO",      plural: "Libros" },
    manga:  { badge: "MANGA",      plural: "Manga" },
  };
  const ITEMS = [
    { id: 1,  t: "Forza Horizon 6",           type: "game",   st: "progress", fav: true,  meta: "Jugando" },
    { id: 2,  t: "The Last of Us Part I",     type: "game",   st: "done",     fav: true,  meta: "Jugado" },
    { id: 3,  t: "Horizon Zero Dawn",         type: "game",   st: "pending",  fav: true },
    { id: 4,  t: "Uncharted 4",               type: "game",   st: "done",     fav: true,  meta: "Jugado" },
    { id: 5,  t: "Boku dake ga Inai Machi",   type: "anime",  st: "progress", fav: true,  meta: "Episodio 2 de 12" },
    { id: 6,  t: "Mushoku Tensei",            type: "anime",  st: "progress", fav: true,  meta: "Episodio 8 de 23" },
    { id: 7,  t: "Death Note",                type: "anime",  st: "done",     fav: true,  meta: "Visto" },
    { id: 8,  t: "Code Geass",                type: "anime",  st: "pending",  fav: true },
    { id: 9,  t: "The Walking Dead",          type: "series", st: "progress", fav: true,  meta: "Episodio 51 de 177" },
    { id: 10, t: "The Handmaid's Tale",       type: "series", st: "progress", fav: true,  meta: "Episodio 2 de 66" },
    { id: 11, t: "Invincible",                type: "series", st: "pending",  fav: true },
    { id: 12, t: "Rocky",                     type: "movie",  st: "done",     fav: true,  meta: "Vista" },
    { id: 13, t: "Tokyo Drift",               type: "movie",  st: "done",     fav: true,  meta: "Vista" },
    { id: 14, t: "Demon Slayer: Infinity Castle", type: "movie", st: "pending", fav: true },
    { id: 15, t: "Unravel",                   type: "music",  st: "done",     fav: true,  meta: "Escuchada" },
    { id: 16, t: "Harry Potter",              type: "book",   st: "done",     fav: true,  meta: "Leído" },
    { id: 17, t: "La quinta ola",             type: "book",   st: "progress", fav: true,  meta: "Leyendo" },
    { id: 18, t: "Juego de tronos",           type: "book",   st: "pending",  fav: false },
    { id: 19, t: "Vinland Saga",              type: "manga",  st: "progress", fav: true,  meta: "Capítulo 40 de 214" },
    { id: 20, t: "Jujutsu Kaisen",            type: "manga",  st: "pending",  fav: false },
  ];
  const STATES = { pending: "Pendiente", progress: "En curso", done: "Terminado" };
  const HINTS = {
    pending: "Lo que quieres ver, jugar, escuchar o leer más adelante (botón de marcador en cada detalle).",
    progress: "Lo que tienes a medias, sea favorito o no.",
    done: "Todo lo que ya has terminado.",
  };
  // Como en la app: cada estado de la biblioteca y Favoritos tienen sus propias carpetas
  const folders = {
    "library:progress": [{ name: "Para el finde", ids: [1, 9] }],
    "library:pending":  [{ name: "Para el verano", ids: [8, 11, 14] }],
    "library:done":     [],
    "favorites":        [{ name: "anime", ids: [7, 5] }, { name: "Lo mejor de lo mejor", ids: [2, 12] }],
  };
  const state = { view: "library", status: "progress", type: "all", folder: null };
  let created = 0;

  const ICON = {
    folder: '<svg class="d-ico" viewBox="0 0 24 24"><path d="M3 6h6l2 2h10v11H3z"/></svg>',
    star:   '<svg class="d-ico" viewBox="0 0 24 24"><path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/></svg>',
    edit:   '<svg class="d-ico" viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>',
    back:   '<svg class="d-ico" viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></svg>',
  };
  const key = () => (state.view === "favorites" ? "favorites" : "library:" + state.status);
  const el = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html) n.innerHTML = html; return n; };
  const text = (tag, cls, s) => { const n = el(tag, cls); n.textContent = s; return n; };

  function pool() {
    return ITEMS.filter((i) => (state.view === "favorites" ? i.fav : i.st === state.status));
  }
  const byType = (list) => (state.type === "all" ? list : list.filter((i) => i.type === state.type));

  function header() {
    const head = el("div", "d-head");
    const titleBox = el("div");
    const title = text("span", "d-title", state.view === "favorites" ? "Favoritos" : "Mi biblioteca");
    titleBox.appendChild(title);
    if (state.view === "favorites") {
      const n = pool().length;
      const count = text("span", "d-count", `  ${n} títulos`);
      titleBox.appendChild(count);
    }
    head.appendChild(titleBox);
    head.appendChild(el("span", "d-spacer"));
    if (state.view === "library") {
      const seg = el("div", "d-seg");
      Object.entries(STATES).forEach(([k, label]) => {
        const b = text("button", "", label);
        b.setAttribute("aria-pressed", k === state.status);
        b.onclick = () => { state.status = k; state.folder = null; render(); };
        seg.appendChild(b);
      });
      head.appendChild(seg);
    }
    const nb = el("button", "d-btn", ICON.folder + "<span>Nueva carpeta</span>");
    nb.onclick = () => {
      created += 1;
      folders[key()].push({ name: created === 1 ? "Mi carpeta" : `Mi carpeta ${created}`, ids: [] });
      state.folder = null;
      render();
    };
    head.appendChild(nb);
    return head;
  }

  function chips() {
    const box = el("div", "d-chips");
    const all = text("button", "", "Todo");
    all.dataset.type = "all";
    all.setAttribute("aria-pressed", state.type === "all");
    all.onclick = () => { state.type = "all"; render(); };
    box.appendChild(all);
    Object.entries(TYPES).forEach(([k, v]) => {
      const b = text("button", "", v.plural);
      b.dataset.type = k;
      b.style.setProperty("--c", `var(--c-${k})`);
      b.setAttribute("aria-pressed", state.type === k);
      b.onclick = () => { state.type = k; render(); };
      box.appendChild(b);
    });
    return box;
  }

  function folderCard(f, idx) {
    const card = el("button", "d-folder");
    const mosaic = el("div", "d-mosaic");
    const inside = pool().filter((i) => f.ids.includes(i.id));
    for (let s = 0; s < 4; s++) {
      const tile = el("span");
      if (inside[s]) tile.style.setProperty("--c", `var(--c-${inside[s].type})`);
      mosaic.appendChild(tile);
    }
    const foot = el("div", "d-folder-foot");
    const info = el("div");
    info.appendChild(text("span", "d-folder-name", f.name));
    info.appendChild(text("span", "d-folder-count", inside.length + (inside.length === 1 ? " título" : " títulos")));
    foot.appendChild(info);
    foot.appendChild(el("span", "d-pencil", ICON.edit));
    card.append(mosaic, foot);
    card.onclick = () => { state.folder = idx; render(); };
    card.addEventListener("dragover", (e) => { e.preventDefault(); card.classList.add("drop-target"); });
    card.addEventListener("dragleave", () => card.classList.remove("drop-target"));
    card.addEventListener("drop", (e) => {
      e.preventDefault();
      const id = Number(e.dataTransfer.getData("text/plain"));
      folders[key()].forEach((other) => { other.ids = other.ids.filter((x) => x !== id); });
      if (id) f.ids.push(id);
      render();
    });
    return card;
  }

  function itemCard(i) {
    const li = el("li", "d-card");
    li.draggable = true;
    li.style.setProperty("--c", `var(--c-${i.type})`);
    const poster = el("div", "d-poster");
    poster.appendChild(text("span", "d-badge", TYPES[i.type].badge));
    poster.appendChild(el("div", "d-actions", `<span>${ICON.folder}</span>${i.fav ? `<span class="star">${ICON.star}</span>` : ""}`));
    poster.appendChild(text("b", "", i.t));
    li.appendChild(poster);
    const meta = state.view === "favorites" ? (i.meta || STATES[i.st]) : (i.meta || "Pendiente");
    li.appendChild(text("span", "d-meta", meta));
    li.addEventListener("dragstart", (e) => { e.dataTransfer.setData("text/plain", String(i.id)); li.classList.add("dragging"); });
    li.addEventListener("dragend", () => li.classList.remove("dragging"));
    return li;
  }

  function grid(list, emptyText) {
    if (!list.length) return text("p", "d-empty", emptyText);
    const ul = el("ul", "d-grid");
    list.forEach((i) => ul.appendChild(itemCard(i)));
    return ul;
  }

  function render() {
    document.querySelectorAll("[data-view]").forEach((b) => b.setAttribute("aria-selected", b.dataset.view === state.view));
    const nodes = [header()];
    const fs = folders[key()];
    if (state.folder !== null && fs[state.folder]) {
      const f = fs[state.folder];
      const back = el("button", "d-back", ICON.back + `<span>${state.view === "favorites" ? "Favoritos" : "Mi biblioteca"}</span>`);
      back.onclick = () => { state.folder = null; render(); };
      nodes.push(back, text("div", "d-folder-title", f.name), chips());
      nodes.push(el("div", "d-section"));
      nodes.push(grid(byType(pool().filter((i) => f.ids.includes(i.id))), "Esta carpeta está vacía. Vuelve atrás y arrastra tarjetas encima de ella."));
    } else {
      nodes.push(chips());
      if (state.view === "library") nodes.push(text("p", "d-hint", HINTS[state.status]));
      if (fs.length) {
        nodes.push(text("h3", "d-section", "Carpetas"));
        const fwrap = el("div", "d-folders");
        fs.forEach((f, idx) => fwrap.appendChild(folderCard(f, idx)));
        nodes.push(fwrap);
      }
      const inFolders = new Set(fs.flatMap((f) => f.ids));
      nodes.push(text("h3", "d-section", fs.length ? "Sin carpeta" : "Títulos"));
      nodes.push(grid(byType(pool().filter((i) => !inFolders.has(i.id))), "No hay nada de este tipo aquí."));
    }
    root.replaceChildren(...nodes);
  }

  document.querySelectorAll("[data-view]").forEach((b) => b.addEventListener("click", () => {
    state.view = b.dataset.view; state.folder = null; state.type = "all"; render();
  }));
  render();
})();

// --- Novedades 1.3.7 – 1.4: progreso por temporadas, logros y su aviso
(function setupSeasons() {
  const box = document.querySelector("[data-seasons]");
  if (!box) return;
  const seasons = [10, 8, 12];                 // episodios por temporada
  const total = seasons.reduce((a, b) => a + b, 0);
  const starts = seasons.map((_, i) => seasons.slice(0, i).reduce((a, b) => a + b, 0) + 1);
  const watched = new Set(Array.from({ length: 13 }, (_, i) => i + 1)); // T1 entera y 3 de la T2
  let current = null;

  const seasonOf = (n) => starts.findLastIndex((s) => n >= s);
  const label = (n) => `T${seasonOf(n) + 1} · E${n - starts[seasonOf(n)] + 1}`;
  const next = () => { for (let i = 1; i <= total; i++) if (!watched.has(i)) return i; return -1; };

  function render() {
    if (current === null) current = next() > 0 ? seasonOf(next()) : seasons.length - 1;
    const n = next();
    const chips = seasons.map((count, i) => {
      let done = 0;
      for (let e = starts[i]; e < starts[i] + count; e++) if (watched.has(e)) done++;
      const text = done === count ? "✓" : done ? `${done}/${count}` : count;
      return `<button type="button" aria-pressed="${i === current}" class="${done === count ? "is-done" : ""}" data-s="${i}">T${i + 1}<small>${text}</small></button>`;
    }).join("");
    let grid = "";
    for (let e = starts[current]; e < starts[current] + seasons[current]; e++) {
      grid += `<button type="button" class="${watched.has(e) ? "is-on" : ""}" data-e="${e}" aria-label="${label(e)}${watched.has(e) ? ", visto" : ""}">${e - starts[current] + 1}</button>`;
    }
    box.innerHTML = `
      <div class="s-head"><b>Tu progreso</b><small>${watched.size} de ${total} episodios</small></div>
      <div class="s-bar"><i style="width:${(watched.size / total) * 100}%"></i></div>
      <div class="s-chips">${chips}</div>
      <div class="s-grid">${grid}</div>
      <button type="button" class="s-main" data-next ${n < 0 ? "disabled" : ""}>${n < 0 ? "Completado" : "Marcar " + label(n)}</button>`;
  }

  box.addEventListener("click", (ev) => {
    const b = ev.target.closest("button");
    if (!b) return;
    if (b.dataset.s) current = +b.dataset.s;
    else if (b.dataset.e) {
      const e = +b.dataset.e;
      if (ev.shiftKey) { watched.clear(); for (let i = 1; i <= e; i++) watched.add(i); }
      else watched.has(e) ? watched.delete(e) : watched.add(e);
    } else if (b.hasAttribute("data-next")) {
      const n = next();
      if (n > 0) { watched.add(n); current = seasonOf(n); }
    }
    render();
  });
  render();
})();

(function setupAchievements() {
  const root = document.querySelector("[data-ach]");
  if (!root) return;
  const ICONS = {
    tv: "M4 7h16v11H4z M9 3l3 4 3-4",
    flame: "M12 3c.5 3 5 5.5 5 10.5a5 5 0 0 1-10 0c0-2.6 1.6-3.9 2.2-5.6.9 1 1.6 2 1.8 3.1 1-2.3 1.4-5.2 1-8z",
    book: "M5 4.5h10a3 3 0 0 1 3 3V20H8a3 3 0 0 1-3-3z M5 17a3 3 0 0 1 3-3h10",
    film: "M4 5h16v14H4z M8 5v14 M16 5v14 M4 9.5h4 M4 14.5h4 M16 9.5h4 M16 14.5h4",
    game: "M7 8h10a4 4 0 0 1 4 4v1a3 3 0 0 1-5.4 1.8L14.5 14h-5l-1.1.8A3 3 0 0 1 3 13v-1a4 4 0 0 1 4-4z M8 10.5v3 M6.5 12h3",
    moon: "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z",
    lock: "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3",
    star: "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z",
  };
  const NAMES = ["", "Fácil", "Normal", "Difícil", "Épico", "Legendario"];
  const LIST = [
    { n: "Sofá reservado", d: "Ve 500 episodios", lv: 2, c: "#8b5cf6", i: "tv", done: "3 nov" },
    { n: "Maratoniano", d: "Ve 10 episodios en un mismo día", lv: 3, c: "#8b5cf6", i: "tv", done: "23 nov" },
    { n: "Noctámbulo", d: "Marca algo entre las 2 y las 5", lv: 2, c: "#ec4d80", i: "moon", done: "19 nov" },
    { n: "Una semana", d: "Ten actividad 7 días seguidos", lv: 1, c: "#ec4d80", i: "flame", done: "20 nov" },
    { n: "Mando gastado", d: "Ve 2.000 episodios", lv: 3, c: "#8b5cf6", i: "tv", p: [1781, 2000] },
    { n: "Tomo tras tomo", d: "Lee 1.000 capítulos de manga", lv: 2, c: "#ef4444", i: "book", p: [500, 1000] },
    { n: "Director de culto", d: "Ve 1.000 películas", lv: 5, c: "#ecb14d", i: "film", p: [212, 1000] },
    { n: "???", d: "Nadie llega antes que tú", lv: 3, c: "#ec4d80", i: "lock", secret: true },
    { n: "Medio año", d: "Ten actividad 180 días seguidos", lv: 4, c: "#ec4d80", i: "flame", p: [12, 180] },
    { n: "Final boss", d: "Termina 500 juegos", lv: 5, c: "#4dd9ec", i: "game", p: [41, 500] },
    { n: "Racha infinita", d: "Ten actividad un año entero seguido. Cada año más, sube de nivel", lv: 5, c: "#ec4d80", i: "flame", p: [12, 365] },
    { n: "???", d: "Las dos caras de una historia", lv: 3, c: "#ec4d80", i: "lock", secret: true },
  ];
  const svg = (k) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[k]}"/></svg>`;
  const pips = (lv, c) => `<span class="pips" style="--c:${c}">${[1, 2, 3, 4, 5].map((i) => `<i class="${i <= lv ? "on" : ""}"></i>`).join("")}</span>`;
  const fmt = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, "."); // 1.781, como en la app
  let level = 0;

  // Mapa de actividad: 18 semanas, siempre igual (semilla fija)
  const heat = root.querySelector("[data-heat]");
  let seed = 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  let cells = "";
  for (let i = 0; i < 18 * 7; i++) {
    const r = rnd(), recent = i > 18 * 7 - 14;
    const l = recent ? 1 + Math.floor(r * 3) : r < 0.45 ? 0 : 1 + Math.floor(r * 2.9);
    cells += `<i data-l="${l}"></i>`;
  }
  heat.innerHTML = cells;

  const filters = root.querySelector("[data-ach-filters]");
  function renderFilters() {
    filters.innerHTML = NAMES.map((name, i) => `<button type="button" aria-pressed="${level === i}" data-lv="${i}">${i ? pips(i, "#ec4d80") : ""}${i ? name : "Todas"}</button>`).join("");
  }
  const grid = root.querySelector("[data-ach-grid]");
  function renderGrid() {
    grid.innerHTML = LIST.filter((a) => !level || a.lv === level).slice(0, 8).map((a) => {
      const foot = a.done ? `<span class="a-foot">Conseguido el ${a.done}</span>`
        : a.p ? `<span class="a-bar"><i style="width:${(a.p[0] / a.p[1]) * 100}%"></i></span><span class="a-foot">${fmt(a.p[0])} / ${fmt(a.p[1])}</span>`
        : `<span class="a-foot">Secreto</span>`;
      return `<li class="${a.done ? "is-done" : ""} ${a.secret ? "secret" : ""}" style="--c:${a.done ? a.c : "#a59fc0"}">
        <div class="a-top"><span class="a-icon">${svg(a.i)}</span><span class="a-diff">${pips(a.lv, a.done ? a.c : "#3a3160")}${NAMES[a.lv]}</span></div>
        <b>${a.n}</b><p>${a.d}</p>${foot}</li>`;
    }).join("") || `<li><p>Ningún logro de este nivel en el ejemplo.</p></li>`;
  }
  filters.addEventListener("click", (ev) => {
    const b = ev.target.closest("button");
    if (!b) return;
    level = +b.dataset.lv;
    renderFilters(); renderGrid();
  });
  renderFilters(); renderGrid();

  // El aviso aparece una vez al llegar a la sección y se va a los 5 segundos
  const toast = root.querySelector("[data-toast]");
  const show = () => { toast.classList.add("is-in"); setTimeout(() => toast.classList.remove("is-in"), 5000); };
  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) { io.disconnect(); setTimeout(show, 700); }
    }, { threshold: 0.5 });
    io.observe(root.closest("article") || root); // la sección, por si la captura real sustituye al panel
  } else {
    show();
  }
})();

// --- Secciones principales: eliges 4 de 7 para verlas en grande y las ordenas
(function setupLayout() {
  const box = document.querySelector("[data-layout]");
  if (!box) return;
  const T = {
    game: ["Juegos", "#4dd9ec"], anime: ["Anime", "#ec4dc0"], series: ["Series", "#8b5cf6"], movie: ["Películas", "#ecb14d"],
    music: ["Música", "#4dec9e"], book: ["Libros", "#f97316"], manga: ["Manga", "#ef4444"],
  };
  // Empieza con Manga como principal, para que se vea la gracia
  const order = ["manga", "anime", "series", "game", "movie", "music", "book"];
  const main = new Set(["manga", "anime", "series", "game"]);
  let fresh = new Set();
  const check = '<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';

  function render() {
    let pos = 0;
    const rows = order.map((t, i) => {
      const on = main.has(t);
      if (on) pos++;
      return `<li class="ly-row ${on ? "is-on" : ""}" style="--c:${T[t][1]}" data-t="${t}" role="checkbox" aria-checked="${on}" tabindex="0">
        <span class="ly-check">${on ? check : ""}</span><span class="ly-dot"></span>
        <span class="ly-name">${T[t][0]}</span><span class="ly-tag">${on ? "Principal " + pos : "Compacta"}</span>
        <button type="button" data-up="${i}" ${i === 0 ? "disabled" : ""} aria-label="Subir ${T[t][0]}">▲</button>
        <button type="button" data-down="${i}" ${i === order.length - 1 ? "disabled" : ""} aria-label="Bajar ${T[t][0]}">▼</button></li>`;
    }).join("");
    const tile = (t) => `<div class="ly-tile ${fresh.has(t) ? "is-new" : ""}" style="--c:${T[t][1]}"><i></i>${T[t][0]}</div>`;
    const big = order.filter((t) => main.has(t)), small = order.filter((t) => !main.has(t));
    const ok = main.size === 4;
    box.innerHTML = `
      <div class="ly-head"><b>Secciones principales</b><small class="${ok ? "ok" : ""}">${ok ? "✓ 4 principales elegidas" : "Elegidas " + main.size + " de 4"}</small></div>
      <ul class="ly-rows">${rows}</ul>
      <div class="ly-prev" aria-hidden="true">
        <div class="ly-prev-big">${ok ? big.map(tile).join("") : ""}</div>
        <div class="ly-prev-small">${ok ? small.map(tile).join("") : ""}</div>
      </div>`;
    fresh = new Set();
  }

  function toggle(t) {
    if (main.has(t)) main.delete(t);
    else if (main.size < 4) { main.add(t); fresh.add(t); }
    render();
  }
  box.addEventListener("click", (ev) => {
    const up = ev.target.closest("[data-up]"), down = ev.target.closest("[data-down]");
    if (up || down) {
      const i = +(up ? up.dataset.up : down.dataset.down), j = up ? i - 1 : i + 1;
      [order[i], order[j]] = [order[j], order[i]];
      fresh = new Set([order[i], order[j]]);
      render();
      return;
    }
    const row = ev.target.closest("[data-t]");
    if (row) toggle(row.dataset.t);
  });
  box.addEventListener("keydown", (ev) => {
    const row = ev.target.closest("[data-t]");
    if (row && (ev.key === " " || ev.key === "Enter")) { ev.preventDefault(); toggle(row.dataset.t); box.querySelector(`[data-t="${row.dataset.t}"]`)?.focus(); }
  });
  render();
})();

// --- Barra lateral: pestaña activa que se desliza y se pliega con el ☰
(function setupRail() {
  const box = document.querySelector("[data-rail]");
  if (!box) return;
  const P = {
    menu: "M4 7h16M4 12h16M4 17h16",
    home: "M3 11l9-7 9 7 M5 10v10h14V10",
    search: "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z M20 20l-4-4",
    star: "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z",
    library: "M5 4v16 M10 4v16 M15 4.5l4.5 15",
    calendar: "M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5",
    review: "M4 5h16v11H10l-5 4v-4H4z M8 9h8 M8 12h5",
    trophy: "M8 4h8v5a4 4 0 0 1-8 0z M8 6H5c0 2.5 1.3 4 3 4.2 M16 6h3c0 2.5-1.3 4-3 4.2 M12 13v4 M8.5 20h7 M10 17h4",
  };
  const ico = (k) => `<svg class="rd-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="${P[k]}"/></svg>`;
  const ITEMS = [
    ["home", "Inicio", "Tu vitrina y lo que tienes a medias", "#ec4d80"],
    ["search", "Buscar", "Series, pelis, anime, manga, juegos, música y libros", "#8b5cf6"],
    ["star", "Favoritos", "Todo lo que te gusta, con tus carpetas", "#ecb14d"],
    ["library", "Mi biblioteca", "Pendiente, en curso y terminado", "#4dd9ec"],
    ["calendar", "Estrenos", "Lo que sale esta semana de lo que sigues", "#ec4dc0"],
    ["review", "Mis reseñas", "Todas tus reseñas, por fecha o por nota", "#4dec9e"],
  ];
  const TYPES = [["Juegos", "#4dd9ec", 8], ["Anime", "#ec4dc0", 5], ["Series", "#8b5cf6", 8], ["Películas", "#ecb14d", 6],
    ["Música", "#4dec9e", 5], ["Libros", "#f97316", 4], ["Manga", "#ef4444", 6]];

  box.innerHTML = `
    <div class="rd-side">
      <div class="rd-top"><button type="button" data-toggle aria-label="Plegar o desplegar la barra" aria-expanded="true">${ico("menu")}</button><b>MediaVerse</b></div>
      <div class="rd-items"><span class="rd-tab"></span>
        ${ITEMS.map(([k, n], i) => `<button type="button" class="rd-item" data-i="${i}" title="${n}">${ico(k)}<span class="rd-lbl">${n}</span>${k === "calendar" ? '<span class="rd-badge">2</span>' : ""}</button>`).join("")}
      </div>
      <p class="rd-sec-title">TUS SECCIONES</p>
      ${TYPES.map(([n, c, k]) => `<div class="rd-type" style="--c:${c}"><i></i><span>${n}</span><span>${k}</span></div>`).join("")}
      <div class="rd-ach"><span class="rd-ring"><span>${ico("trophy")}</span></span><div><b>Logros</b><small>15 de 67</small></div></div>
    </div>
    <div class="rd-main"></div>`;

  const tab = box.querySelector(".rd-tab"), main = box.querySelector(".rd-main");
  const items = [...box.querySelectorAll(".rd-item")];
  function select(i) {
    items.forEach((b, j) => b.classList.toggle("is-on", j === i));
    tab.style.top = items[i].offsetTop + "px";
    const [, n, d, c] = ITEMS[i];
    main.style.setProperty("--c", c);
    main.innerHTML = `<h3>${n}</h3><p>${d}</p><div class="rd-cards"><i></i><i></i><i></i><i></i></div>`;
  }
  box.addEventListener("click", (ev) => {
    const item = ev.target.closest("[data-i]");
    if (item) return select(+item.dataset.i);
    const t = ev.target.closest("[data-toggle]");
    if (t) t.setAttribute("aria-expanded", String(!box.classList.toggle("is-col")));
  });
  select(0);
})();

// --- Capturas reales: si existe la imagen de un hueco [data-shot], sustituye a la réplica.
// Basta con dejar el archivo en assets/shots/ con ese nombre; si no está, se queda la réplica.
(function setupRealShots() {
  document.querySelectorAll("[data-shot]").forEach((el) => {
    const img = new Image();
    img.onload = () => {
      const fig = document.createElement("figure");
      fig.className = "shot shot--auto shot--real";
      fig.style.setProperty("--c", el.dataset.c || "#ec4d80");
      img.alt = el.dataset.alt || "";
      img.width = img.naturalWidth; img.height = img.naturalHeight;
      img.loading = "lazy";
      fig.appendChild(img);
      // Lo que debe seguir encima de la captura (el aviso de logro)
      el.querySelectorAll("[data-keep]").forEach((k) => fig.appendChild(k));
      if (el.hasAttribute("data-ach")) fig.setAttribute("data-ach-real", "");
      el.replaceWith(fig);
    };
    img.src = el.dataset.shot + "?v=" + (document.querySelector('script[src^="app.js"]')?.src.split("v=")[1] || "1");
  });
})();
