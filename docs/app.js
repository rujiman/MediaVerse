// MediaVerse: página de descarga.
// 1. Botones de Windows y Linux (en móvil, aviso; en Linux abre su pestaña).
// 2. Muestra la versión actual, leída de la última Release de GitHub.
// 3. Pestañas de instalación, tráiler y cabecera.

const REPO = "rujiman/MediaVerse";

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

  if (os === "linux") selectTab("linux");
  if (os === "mac") {
    alt.textContent = "Aún no hay versión para Mac.";
    alt.hidden = false;
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
