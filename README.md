# 🎬 MediaVerse

**Tu universo de entretenimiento en una sola app.** Gestiona, organiza y presume de todo lo que ves, juegas, escuchas y lees.

---

## ✨ Características

- **Vitrina de perfil** — tu pantalla de inicio es un perfil para *flexear* tus gustos: favorito absoluto + ranking por categoría, portada personalizable (imagen, color sólido o degradado, con desenfoque ajustable) y exportación como imagen PNG
- **7 tipos de contenido** — series y películas (TMDB), anime y manga (AniList), videojuegos (IGDB), música (Deezer) y libros (Google Books / Open Library)
- **Mi biblioteca** — todo lo tuyo en un sitio: *Pendiente · En curso · Terminado*, con filtro por tipo y carpetas (arrastrando las tarjetas)
- **Favoritos** con carpetas (arrastrar y soltar) y valoración personal (1-5 estrellas)
- **Progreso** por episodios o capítulos (clic, Mayús + clic para marcar hasta uno, bloques de 50 en series largas) y estado *"Estoy jugando / viendo / leyendo"* para el resto
- **Continúa donde lo dejaste** en la pantalla de inicio, con aviso de **"Nuevo ep."** cuando sale uno esta semana
- **Estadísticas** en tu perfil: episodios vistos, capítulos leídos, terminados, en curso y tu nota media
- **Episodios por temporadas** en las series, con tu progreso en cada una
- **Estrenos** — próximos episodios y lanzamientos de lo que sigues, agrupados por fecha, con aviso de novedades
- **Reseñas** personales con aviso de spoilers, y **Mis reseñas** para verlas todas
- **Logros y rachas** — 67 logros en 5 dificultades (con 7 secretos), rachas de hasta un año, mapa de actividad, aviso con sonido y medallas en tu perfil
- **Ficha de detalle** con póster, progreso, tráiler, plataformas de streaming, tu reseña y recomendaciones
- **Copia de seguridad** de todos tus datos en un `.zip`
- **Actualizaciones automáticas** — la app avisa cuando hay versión nueva y se actualiza sola con un botón
- **Diseño moderno** — tipografía Inter, animaciones suaves y paleta "Constelaciones" con un color por tipo
- **Datos 100% locales** — nada sale de tu ordenador

---

## 🚀 Instalación rápida (Windows)

> ¿Usas Linux? Mira [Linux](#-linux) más abajo.

1. Descarga el `.zip` desde [Releases](https://github.com/rujiman/MediaVerse/releases)
2. Descomprime donde quieras (Escritorio, Documentos, USB...)
3. Doble clic en `MediaVerse.exe`
4. La primera vez, un asistente te guía para conectar tus claves de API gratuitas (TMDB y Twitch). Tarda unos minutos y solo se hace una vez

**No necesitas instalar Java ni ninguna otra cosa.** El runtime está incluido dentro del zip.

### 🐧 Linux

1. Descarga `MediaVerse-linux.tar.gz` desde [Releases](https://github.com/rujiman/MediaVerse/releases/latest)
2. Descomprime: `tar xzf MediaVerse-linux.tar.gz`
3. Ejecuta `MediaVerse/bin/MediaVerse`
4. El asistente de la primera vez te ayuda con las claves de API

También lleva Java incluido. Hace falta un escritorio con GTK 3 (cualquier Ubuntu, Fedora, Mint… lo trae).

### 🔄 Actualizar

A partir de la versión 1.2, la app comprueba sola si hay una versión nueva en GitHub: aparece el botón **"Actualizar a X"** arriba a la derecha y, al pulsarlo, se descarga, se reinicia y listo. También puedes comprobarlo desde el menú de perfil → *Buscar actualizaciones*.

En Windows y en Linux funciona igual.

Si vienes de la **1.0 o la 1.1** (no tienen actualizador), actualiza a mano una vez: descarga el zip nuevo, copia tu `app\.env` a la carpeta nueva y borra la vieja. **Tus datos no se pierden**: viven en `Documentos\MediaVerse`, fuera de la app.

> El `.env` ya es opcional: si lo tienes, sigue funcionando; si no, la app te pide las claves con el asistente. Ver [INSTALL.md](./INSTALL.md).

---

## 🔧 Compilar desde código fuente

```bash
git clone https://github.com/rujiman/MediaVerse.git
cd MediaVerse
cp .env.example .env   # opcional: o configúralas con el asistente al arrancar
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
    ├── carpetas/
    ├── resenas/       (tus reseñas)
    ├── actividad/     (qué hiciste y cuándo: rachas)
    ├── logros/        (logros conseguidos)
    ├── cache/         (estrenos y temporadas; se puede borrar)
    └── <usuario>_banner_*.png   (portada de la vitrina, si subes una)
```

Puedes hacer backup (perfil → **Copia de seguridad**), mover o sincronizar esta carpeta con Google Drive o OneDrive cuando quieras. Ningún dato se envía a ningún servidor.

---

## 🛠️ APIs utilizadas

| Servicio | Uso | Requiere registro |
|----------|-----|-------------------|
| TMDB | Series, películas, plataformas | Sí (gratuito) |
| AniList | Anime y manga | No |
| IGDB | Videojuegos | Sí (gratuito) |
| Deezer | Música | No |
| Google Books | Libros (con sinopsis) | Opcional (gratuito) |
| Open Library | Libros (respaldo, sin clave) | No |
| GitHub Releases | Comprobar actualizaciones | No |

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
