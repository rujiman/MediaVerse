# 📜 Changelog — MediaVerse

---

## [1.1.0] — 2026-06-30

### 🔨 Cambios técnicos

- **Distribución portable con jpackage**: el zip incluye Java 25 y JavaFX embebidos. Los usuarios ya no necesitan tener Java instalado.
- **Rutas de datos absolutas**: `userdata/` ya no se guarda relativo al ejecutable, sino en `~/Documents/MediaVerse/userdata/`. Los datos sobreviven a reinstalaciones y se pueden sincronizar con Google Drive/OneDrive.
- **Ubicación del `.env`**: `ConfigLoader` ahora localiza el `.env` junto al jar en ejecución en vez de usar una ruta relativa al directorio de trabajo. Funciona igual en desarrollo y en el portable.
- **Foto de perfil en `userdata/`**: la foto de perfil se guarda en `~/Documents/MediaVerse/userdata/<usuario>/` junto al resto de datos del usuario, no dentro de la carpeta de la app.
- **Eliminados artefactos obsoletos**: se eliminan del repo los ficheros de launch4j e Inno Setup (`Mediaverse.exe`, `Mediaverse.xml`, `launch4j.log`, `mediaverse-installer.iss`, `run-mediaverse.bat`, `javafx-vm-options.txt`, `output.txt`).

---

## [1.0.0] — 2025-06-23 (Lanzamiento inicial)

### ✨ Características

- Búsqueda de series y películas (TMDB), anime (AniList), videojuegos (IGDB) y música (Deezer)
- Sistema de favoritos con carpetas personalizadas
- Listas de seguimiento: Pienso Ver, Pienso Jugar, Pienso Escuchar
- Dashboard personalizado "Mi MediaVerse"
- Progreso por episodios con marcado individual y en bloque
- Valoración personal (1-5 estrellas) independiente de la puntuación de la comunidad
- Plataformas de streaming integradas (Netflix, Prime, Disney+, Crunchyroll y más)
- Tráileres en YouTube y recomendaciones similares
- Sistema de login/registro local (sin servidor remoto)
- Foto de perfil por usuario
- Interfaz oscura "Constelaciones" con paleta por tipo de contenido
- Persistencia 100% local en JSON, sin envío de datos a ningún servidor

### 📦 Stack
- Java 25, JavaFX 25, Gson 2.10.1, OkHttp 4.11.0, Maven

---

## Roadmap

### v1.2 (ideas)
- Estadísticas de uso (horas vistas, juegos completados, etc.)
- Modo claro
- Búsqueda avanzada con filtros por género, año, plataforma
- Mejores recomendaciones basadas en historial personal

---

**Última actualización**: 2026-06-30
