// MediaVerse: página de descarga.
// 1. Elige el botón de descarga según el sistema (Windows / Linux / móvil).
// 2. Muestra la versión actual, leída de la última Release de GitHub.
// 3. Pestañas de instalación, tráiler y cabecera.

const REPO = "rujiman/MediaVerse";
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
    "library:progress": [{ name: "Con amigos", ids: [1, 9] }],
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
