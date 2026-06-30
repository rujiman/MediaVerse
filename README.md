# 🎬 MediaVerse

**Tu universo de entretenimiento en una sola app.** Gestiona y organiza todo lo que ves, juegas y escuchas.

---

## ✨ Características

- **Series y Películas** — búsqueda y detalle desde TMDB, con plataformas de streaming incluidas
- **Anime** — catálogo completo desde AniList vía GraphQL
- **Videojuegos** — base de datos de IGDB con géneros y plataformas
- **Música** — canciones y álbumes desde Deezer con previsualización de 30 segundos
- **Sistema de favoritos** con carpetas personalizadas y valoración personal (1-5 estrellas)
- **Listas de seguimiento** — "Pienso Ver", "Pienso Jugar", "Pienso Escuchar"
- **Dashboard personalizado** — elige qué favoritos aparecen en tu pantalla de inicio
- **Progreso por episodios** — marca capítulos vistos uno a uno o de golpe
- **Datos 100% locales** — nada sale de tu ordenador

---

## 🚀 Instalación rápida (Windows)

1. Descarga el `.zip` desde [Releases](https://github.com/rujiman/MediaVerse/releases)
2. Descomprime donde quieras (Escritorio, Documentos, USB...)
3. Copia `.env.example` y renómbralo a `.env`
4. Rellena tus claves de API en `.env` (ver [SETUP.md](./SETUP.md))
5. Doble clic en `MediaVerse.exe`

**No necesitas instalar Java ni ninguna otra cosa.** El runtime está incluido dentro del zip.

> El `.env` debe colocarse en la subcarpeta `app\` del zip descomprimido, junto al `MediaVerse.jar`. Ver [INSTALL.md](./INSTALL.md) para el detalle paso a paso.

---

## 🔧 Compilar desde código fuente

```bash
git clone https://github.com/rujiman/MediaVerse.git
cd MediaVerse
cp .env.example .env   # rellena tus claves
mvn javafx:run
```

Requiere Java 25 y Maven instalados.

---

## 🔐 Tus datos son tuyos

MediaVerse guarda todo en local, en tu carpeta personal:

```
~/Documents/MediaVerse/userdata/
├── users.json
├── profile.json
└── <usuario>/
    ├── favoritos/
    ├── dashboard/
    ├── progreso/
    ├── plan/
    └── carpetas/
```

Puedes hacer backup, mover o sincronizar esta carpeta con Google Drive o OneDrive cuando quieras. Ningún dato se envía a ningún servidor.

---

## 🛠️ APIs utilizadas

| Servicio | Uso | Requiere registro |
|----------|-----|-------------------|
| TMDB | Series, películas, plataformas | Sí (gratuito) |
| AniList | Anime | No |
| IGDB | Videojuegos | Sí (gratuito) |
| Deezer | Música | No |

---

## 📚 Documentación

- [INSTALL.md](./INSTALL.md) — Instalación detallada paso a paso
- [SETUP.md](./SETUP.md) — Cómo obtener y configurar las claves de API
- [GUIA_DE_USO.md](./GUIA_DE_USO.md) — Manual de uso completo
- [DEPENDENCIES.md](./DEPENDENCIES.md) — Librerías y stack técnico
- [BUILD_EXE.md](./BUILD_EXE.md) — Cómo generar el portable para desarrolladores
- [CHANGELOG.md](./CHANGELOG.md) — Historial de versiones

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)
Trabajo Final de Grado — DAM 2025
[github.com/rujiman/MediaVerse](https://github.com/rujiman/MediaVerse)
