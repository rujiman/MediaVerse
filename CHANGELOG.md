# 📜 Changelog — MediaVerse

---

## [1.2.1] — 2026-11-18

### ✨ Novedades
- **Versión para Linux** (`MediaVerse-linux.tar.gz`), con Java incluido y actualización automática igual que en Windows
- Los enlaces (tráileres, plataformas, descargas) se abren también en escritorios Linux sin soporte de `java.awt.Desktop`, usando `xdg-open`

### 🔨 Cambios técnicos
- **Releases automáticas con GitHub Actions**: al subir una etiqueta `vX.Y.Z`, GitHub compila Windows y Linux y publica la Release con sus archivos y las notas de este CHANGELOG
- En Linux el `.env` puede ir en la raíz de la carpeta (`MediaVerse/.env`)

---

## [1.2.0] — 2026-11-18

### ✨ Novedades

- **Manga** (AniList, progreso por capítulos) y **libros** (Google Books, con Open Library como respaldo sin clave)
- **Inicio rediseñado como vitrina de perfil**: favorito absoluto + ranking 2–5 por categoría, contadores, portada personalizable (favorito, imagen propia o color sólido; desenfoque/degradado con intensidad ajustable), cambio de foto desde la propia vitrina, reordenar arrastrando y **exportar la vitrina como PNG**
- **Continúa donde lo dejaste** en Inicio (máx. 3)
- **Mi biblioteca**: Pendiente · En curso · Terminado, filtro por tipo y carpetas con arrastrar y soltar. Sustituye a las 12 entradas antiguas del menú (Pienso ver/jugar/escuchar, …vistos, …viendo)
- **Estado "Estoy jugando / viendo / escuchando / leyendo"** para lo que no va por episodios
- **Favoritos rediseñados** con el mismo sistema de carpetas arrastrando (las carpetas existentes se conservan)
- **Detalle tipo ficha**: banda con el color del tipo y el póster desenfocado, una acción principal, barra de progreso, episodios en cuadrícula por bloques de 50 con Mayús + clic
- **Login y registro** nuevos, en pantalla partida con mosaico animado
- **Menú lateral** de 4 entradas y cabecera siempre visible (logo clicable para volver a Inicio)
- **Actualizaciones automáticas** desde GitHub Releases con un botón
- Las listas *En curso / Terminado* incluyen también títulos que no son favoritos

### 🎨 Diseño
- Tipografía **Inter** incluida en la app, iconos vectoriales, sistema de animaciones (`Motion`) con curvas tipo iOS, contraste de texto mejorado

### 🐛 Correcciones
- Las descripciones ya no muestran restos de HTML (`<br>`, `&quot;`…)
- Búsquedas de TMDB con `&`, `#` o `?` en el título
- Títulos iguales de distinto tipo (p. ej. el anime y el manga de *Berserk*) ya no comparten favorito ni progreso
- Los diálogos ya no mandan la app al fondo al cerrarse
- Portadas y fotos de perfil sin deformar

### 🔨 Cambios técnicos
- Eliminado código muerto (pantallas y clases sin uso, ~1.500 líneas) y las dependencias sin usar (SQLite, Hibernate)
- Código compartido en `ui/` (`CollectionUI`, `Dialogs`, `Palette`, `Layout`, `Motion`, `Icons`) en vez de copias por controlador
- `AppInfo` con la versión y `UpdateService` para las actualizaciones

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

### Próximas versiones (ideas)
- MediaVerse Wrapped: resumen anual de lo que has visto, jugado y leído
- Calendario de estrenos de tus series y animes
- Logros y rachas
- Búsqueda global rápida con Ctrl+K
- Contraseñas cifradas (hash) en el login local
- Página web de descarga con tráiler de la app
- Perfil público para compartir tu vitrina (red social)

---

**Última actualización**: 2026-11-18 (1.2.1)
