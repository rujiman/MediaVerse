# 📜 Changelog — MediaVerse

---

## [1.3.7] — 2026-11-23

### ✨ Novedades
- **Estrenos**: nueva sección en el menú con los próximos episodios y lanzamientos de lo que sigues (series y anime en curso o en favoritos, y lo que tienes en Pendiente), agrupados en Hoy, Mañana, Esta semana, Próximas semanas, Más adelante y Sin fecha todavía. Funciona sin conexión con lo último que se supo
- **Aviso de estrenos nuevos**: un número junto a Estrenos y un puntito en el menú cuando sale algo nuevo esta semana; se apaga al abrir la sección. En "Continúa donde lo dejaste" verás "Nuevo ep. vie" en lo que estrena pronto
- **Reseñas**: escribe lo que te ha parecido cualquier título, debajo de tus estrellas, y marca si tiene spoilers. Todas tus reseñas están en **Mis reseñas**, desde el menú o desde tu perfil, ordenadas por fecha o por nota
- **Episodios por temporadas** en las series: chips T1, T2… con tu progreso en cada una, "Marcar temporada" y "Siguiente: T2 · E4". Lo que ya tenías marcado se conserva
- **Editor de la vitrina más rápido**: al quitar un título puedes elegir otro en el mismo paso
- **Estadísticas en tu perfil**: episodios vistos, capítulos leídos, terminados, en curso y tu nota media
- **Copia de seguridad**: exporta e importa todos tus datos en un .zip desde el menú del perfil
- **Acerca de MediaVerse**: versión, enlaces y créditos
- **Ctrl+K** te lleva a Buscar desde cualquier pantalla; la barra de búsqueda solo aparece en Buscar

---

## [1.3.6] — 2026-11-22

### 🐛 Correcciones
- **Al actualizar ya no aparece una ventana de terminal.** El paso que cambia la app por la versión nueva se ejecuta oculto (se nota a partir de la siguiente actualización, porque esta todavía la instala la versión anterior)

---

## [1.3.5] — 2026-11-22

### ✨ Novedades
- **El asistente de claves se puede saltar entero.** Cada paso tiene "Saltar este paso" y, la primera vez, "Saltar todo": quien solo quiere anime, manga, música o libros no necesita ninguna clave. Una vez saltado no vuelve a aparecer; las claves se añaden cuando se quiera en perfil → Claves de API
- Sin clave de TMDB o de Twitch, la búsqueda simplemente no consulta esos servicios, y al filtrar por series, películas o juegos explica cómo conectarlos

### 🐛 Correcciones
- La 1.3.4 se publicó con el número de versión antiguo dentro, así que la app volvía a ofrecer la misma actualización después de instalarla. Ahora el proceso de publicación comprueba que la versión de la app coincide con la etiqueta antes de compilar

---

## [1.3.3] — 2026-11-18

### ✨ Novedades
- **Sin ventana de terminal en Windows** al abrir la app (en las descargas nuevas; si ya la tienes instalada, desaparece al reinstalar con el zip)
- Los mensajes internos de la app se guardan en `Documentos/MediaVerse/userdata/mediaverse.log`, por si hay que averiguar un fallo

---

## [1.3.2] — 2026-11-18

### 🔨 Cambios técnicos
- Versión de prueba para comprobar la actualización automática en Windows y Linux. No cambia nada de la app

---

## [1.3.1] — 2026-11-18

### 🐛 Correcciones
- **La actualización automática en Windows no terminaba**: la app se cerraba pero no se actualizaba ni se volvía a abrir. El script de actualización ahora espera de verdad a que la app se cierre, se ejecuta en su propia ventana (antes Windows lo cerraba junto con la app) y funciona con rutas con tildes o ñ. Si algo falla, vuelve a abrir la versión anterior y deja un registro en `%TEMP%\mediaverse-update.log`

> Si tienes la 1.2 o la 1.3.0 en Windows, instala esta versión a mano una última vez (descarga el zip y sustituye la carpeta; tus datos y claves no se pierden). A partir de aquí, el botón de actualizar funciona.

---

## [1.3.0] — 2026-11-18

### ✨ Novedades
- **Asistente de claves de API**: la primera vez que abres la app, antes del login, te guía paso a paso para conectar TMDB, Twitch (IGDB) y, si quieres, Google Books. Cada paso abre la página del servicio, explica qué copiar y **comprueba la clave al momento** antes de seguir
- **Ya no hace falta crear ni editar el `.env`**: las claves se guardan en `Documentos/MediaVerse/userdata/claves_api.env` y sobreviven a actualizaciones y reinstalaciones
- **Perfil → Claves de API** para cambiarlas cuando quieras
- Mensajes de error claros: token de TMDB equivocado, secret de Twitch caducado, Books API sin activar…

### 🔨 Cambios técnicos
- `ApiKeyChecker` valida cada clave contra su servicio; `ConfigLoader` combina el `.env` (opcional) con las claves guardadas desde la app, que tienen prioridad

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
- Logros y rachas
- Tarjeta para compartir tu perfil en redes
- Contraseñas cifradas (hash) en el login local
- Perfil público para compartir tu vitrina y ver reseñas de otros (red social)

---

**Última actualización**: 2026-11-23 (1.3.7)
