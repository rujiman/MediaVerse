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

// --- Demo de Mi biblioteca / Favoritos (filtros, carpetas y arrastrar)
(function setupDemo() {
  const root = document.querySelector("[data-demo]");
  if (!root) return;

  const TYPES = {
    game: "Juego", anime: "Anime", series: "Serie", movie: "Película",
    music: "Música", book: "Libro", manga: "Manga",
  };
  const ITEMS = [
    { id: 1,  t: "Forza Horizon 6",            type: "game",   st: "progress", fav: true,  meta: "Jugando" },
    { id: 2,  t: "The Last of Us Part I",      type: "game",   st: "done",     fav: true,  meta: "Jugado" },
    { id: 3,  t: "Horizon Zero Dawn",          type: "game",   st: "pending",  fav: true },
    { id: 4,  t: "Uncharted 4",                type: "game",   st: "done",     fav: true,  meta: "Jugado" },
    { id: 5,  t: "Boku dake ga Inai Machi",    type: "anime",  st: "progress", fav: true,  meta: "Ep. 2 de 12" },
    { id: 6,  t: "Mushoku Tensei",             type: "anime",  st: "progress", fav: true,  meta: "Ep. 8 de 23" },
    { id: 7,  t: "Death Note",                 type: "anime",  st: "done",     fav: true,  meta: "Visto" },
    { id: 8,  t: "Code Geass",                 type: "anime",  st: "pending",  fav: true },
    { id: 9,  t: "The Walking Dead",           type: "series", st: "progress", fav: true,  meta: "Ep. 51 de 177" },
    { id: 10, t: "The Handmaid's Tale",        type: "series", st: "progress", fav: true,  meta: "Ep. 2 de 66" },
    { id: 11, t: "Invincible",                 type: "series", st: "pending",  fav: true },
    { id: 12, t: "Rocky",                      type: "movie",  st: "done",     fav: true,  meta: "Vista" },
    { id: 13, t: "Tokyo Drift",                type: "movie",  st: "done",     fav: true,  meta: "Vista" },
    { id: 14, t: "Demon Slayer: Infinity Castle", type: "movie", st: "pending", fav: false },
    { id: 15, t: "Unravel",                    type: "music",  st: "done",     fav: true,  meta: "Escuchada" },
    { id: 16, t: "Harry Potter",               type: "book",   st: "done",     fav: true,  meta: "Leído" },
    { id: 17, t: "La quinta ola",              type: "book",   st: "progress", fav: true,  meta: "Leyendo" },
    { id: 18, t: "Juego de tronos",            type: "book",   st: "pending",  fav: true },
    { id: 19, t: "Vinland Saga",               type: "manga",  st: "progress", fav: true,  meta: "Cap. 40 de 214" },
    { id: 20, t: "Jujutsu Kaisen",             type: "manga",  st: "pending",  fav: false },
  ];
  // Carpetas: en la app, cada estado de la biblioteca y Favoritos tiene las suyas
  const folders = {
    "library:progress": [{ name: "Con amigos", ids: [1, 9] }],
    "library:pending":  [{ name: "Para el verano", ids: [8, 11] }],
    "library:done":     [],
    "favorites":        [{ name: "Lo mejor de lo mejor", ids: [2, 7, 12] }],
  };
  const state = { view: "library", status: "progress", type: "all", folder: null };
  let created = 0;

  const grid = root.querySelector("[data-grid]");
  const folderBar = root.querySelector("[data-folders]");
  const statusBar = root.querySelector("[data-status-bar]");
  const key = () => (state.view === "favorites" ? "favorites" : "library:" + state.status);
  const FOLDER_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h6l2 2h10v11H3z"/></svg>';

  function visibleItems() {
    let list = ITEMS.filter((i) => (state.view === "favorites" ? i.fav : i.st === state.status));
    if (state.folder !== null) {
      const f = folders[key()][state.folder];
      list = list.filter((i) => f.ids.includes(i.id));
    }
    if (state.type !== "all") list = list.filter((i) => i.type === state.type);
    return list;
  }

  function renderFolders() {
    const all = document.createElement("button");
    all.className = "folder";
    all.setAttribute("aria-pressed", state.folder === null);
    all.textContent = "Todo";
    all.onclick = () => { state.folder = null; render(); };
    folderBar.replaceChildren(all);

    folders[key()].forEach((f, idx) => {
      const b = document.createElement("button");
      b.className = "folder";
      b.setAttribute("aria-pressed", state.folder === idx);
      b.innerHTML = `${FOLDER_ICON}<span></span><small>${f.ids.length}</small>`;
      b.querySelector("span").textContent = f.name;
      b.onclick = () => { state.folder = idx; render(); };
      b.addEventListener("dragover", (e) => { e.preventDefault(); b.classList.add("drop-target"); });
      b.addEventListener("dragleave", () => b.classList.remove("drop-target"));
      b.addEventListener("drop", (e) => {
        e.preventDefault();
        b.classList.remove("drop-target");
        const id = Number(e.dataTransfer.getData("text/plain"));
        if (id && !f.ids.includes(id)) f.ids.push(id);
        render();
      });
      folderBar.appendChild(b);
    });
  }

  function renderGrid() {
    const list = visibleItems();
    if (!list.length) {
      const li = document.createElement("li");
      li.className = "demo-empty";
      li.textContent = state.folder !== null
        ? "Carpeta vacía. Vuelve a «Todo» y arrastra tarjetas encima de ella."
        : "No hay nada de este tipo aquí.";
      grid.replaceChildren(li);
      return;
    }
    grid.replaceChildren(...list.map((i) => {
      const li = document.createElement("li");
      li.className = "card";
      li.draggable = true;
      li.style.setProperty("--c", `var(--c-${i.type})`);
      li.innerHTML = `<div class="card-poster"></div><span class="card-title"></span><span class="card-meta"></span>`;
      li.querySelector(".card-poster").textContent = TYPES[i.type];
      li.querySelector(".card-title").textContent = i.t;
      li.querySelector(".card-meta").textContent =
        state.view === "favorites" ? "★ Favorito" : (i.meta || "Pendiente");
      li.addEventListener("dragstart", (e) => {
        e.dataTransfer.setData("text/plain", String(i.id));
        li.classList.add("dragging");
      });
      li.addEventListener("dragend", () => li.classList.remove("dragging"));
      return li;
    }));
  }

  function render() {
    root.querySelectorAll("[data-view]").forEach((b) => b.setAttribute("aria-selected", b.dataset.view === state.view));
    statusBar.hidden = state.view === "favorites";
    root.querySelectorAll("[data-status]").forEach((b) => b.setAttribute("aria-pressed", b.dataset.status === state.status));
    root.querySelectorAll("[data-type]").forEach((b) => b.setAttribute("aria-pressed", b.dataset.type === state.type));
    renderFolders();
    renderGrid();
  }

  root.querySelectorAll("[data-view]").forEach((b) => b.addEventListener("click", () => {
    state.view = b.dataset.view; state.folder = null; render();
  }));
  root.querySelectorAll("[data-status]").forEach((b) => b.addEventListener("click", () => {
    state.status = b.dataset.status; state.folder = null; render();
  }));
  root.querySelectorAll("[data-type]").forEach((b) => b.addEventListener("click", () => {
    state.type = b.dataset.type; render();
  }));
  root.querySelector("[data-new-folder]").addEventListener("click", () => {
    created += 1;
    folders[key()].push({ name: created === 1 ? "Mi carpeta" : `Mi carpeta ${created}`, ids: [] });
    state.folder = null; // se queda en "Todo" para poder arrastrar tarjetas a la nueva carpeta
    render();
  });

  render();
})();
