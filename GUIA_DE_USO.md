# 📖 Guía de uso — MediaVerse

## Tabla de contenidos
1. [Primeros pasos](#primeros-pasos)
2. [Buscar contenido](#buscar-contenido)
3. [Favoritos](#favoritos)
4. [Listas de seguimiento](#listas-de-seguimiento)
5. [Dashboard (Mi MediaVerse)](#mi-mediaverse)
6. [Detalle de contenido](#detalle-de-contenido)
7. [Plataformas de streaming](#plataformas-de-streaming)
8. [Progreso y valoraciones](#progreso-y-valoraciones)
9. [FAQ](#faq)

---

## Primeros pasos

### Crear cuenta
1. Abre MediaVerse
2. Haz clic en "¿No tienes cuenta?"
3. Elige un usuario (mínimo 3 caracteres) y una contraseña (mínimo 4 caracteres)
4. Confirma la contraseña y regístrate

### Cambiar contraseña o nombre de usuario
Menú (☰ arriba a la derecha) → Perfil → Cambiar Contraseña / Cambiar Usuario

### Foto de perfil
Menú (☰) → Perfil → haz clic sobre tu avatar → elige una imagen PNG o JPG

---

## Buscar contenido

La app soporta 5 categorías de búsqueda:

| Tipo | Fuente | Qué encuentras |
|------|--------|----------------|
| Series | TMDB | Series, documentales, miniseries |
| Películas | TMDB | Cine de todos los géneros y épocas |
| Anime | AniList | Anime y temporadas |
| Videojuegos | IGDB | Juegos para PC, consolas y móvil |
| Música | Deezer | Canciones y álbumes |

Escribe en la barra de búsqueda y haz clic en cualquier resultado para ver su detalle completo.

---

## Favoritos

### Agregar y eliminar
Desde el detalle de cualquier contenido, pulsa **⭐ Agregar a Favoritos** o **❌ Eliminar de Favoritos**.

### Carpetas personalizadas
1. En la pestaña Favoritos, pulsa **✏️ Editar**
2. Pulsa **+ Crear Carpeta** y dale un nombre
3. Desde el detalle de cualquier favorito, pulsa el icono de carpeta 📁 para asignarlo

### Filtrar por tipo
En la pestaña Favoritos puedes filtrar por Series, Películas, Anime, Juegos o Música.

---

## Listas de seguimiento

Mantén un registro de lo que planeas consumir, separado de tus favoritos:

- **Pienso Ver** — series y películas pendientes
- **Pienso Jugar** — juegos en lista de deseos
- **Pienso Escuchar** — música por descubrir

Desde el detalle de cualquier contenido, pulsa **"+ Pienso Ver/Jugar/Escuchar"** para añadirlo.

También puedes crear carpetas dentro de cada lista (ej: "Diciembre", "Trending") desde el botón ✏️ Editar.

---

## Mi MediaVerse

Tu dashboard personalizado que aparece al abrir la app tras login. Puedes elegir qué favoritos destacados aparecen en cada sección.

### Secciones disponibles
- Juegos (12 slots)
- Series (12 slots)
- Anime (12 slots)
- Música (5 slots)
- Películas (5 slots)

### Personalizar
Pulsa **✏️** en la esquina de cualquier sección → selecciona qué favoritos mostrar → Listo.

Las secciones sin contenido se ocultan automáticamente.

---

## Detalle de contenido

Al abrir el detalle de cualquier resultado verás:

- Portada, título, año y puntuación
- Géneros y descripción completa
- Episodios (para series y anime) con checkboxes individuales
- Duración y pistas (para música)
- Plataformas de streaming disponibles (para series y películas)
- Tráiler en YouTube (para series y películas)
- Recomendaciones similares
- Tu valoración personal (1-5 estrellas)

---

## Plataformas de streaming

En el detalle de series y películas, si hay plataformas disponibles (Netflix, Prime, Disney+, Crunchyroll, etc.), aparece el botón **▶ Ver Ahora**. Si hay varias opciones, se abre un selector para elegir.

El botón abre una búsqueda directa dentro de esa plataforma en tu navegador.

---

## Progreso y valoraciones

### Marcar episodios
En el detalle de una serie o anime, marca los episodios que has visto uno a uno, o usa los botones rápidos: **Marcar todos**, **Desmarcar todos**, o **Hasta el episodio X**.

### Valorar
Haz clic en las estrellas de "Mi Valoración" en el detalle. Es independiente de la puntuación de la comunidad (TMDB/AniList/IGDB).

---

## FAQ

**¿Dónde se guardan mis datos?**
En tu carpeta personal: `~/Documents/MediaVerse/userdata/<tu_usuario>/`. Puedes hacer backup o sincronizarla con Drive/OneDrive.

**¿Necesito conexión a Internet?**
Sí, para las búsquedas. Una vez guardado en favoritos, puedes ver la información guardada sin conexión (excepto las imágenes).

**¿Cuántos favoritos puedo tener?**
Ilimitados. En el dashboard, el máximo por sección es 12 (juegos/series/anime) o 5 (música/películas).

**¿Puedo cambiar mi nombre de usuario?**
Sí, desde Menú → Perfil → Cambiar Usuario. Los datos se renombran automáticamente.

**Los géneros no aparecen en mis favoritos**
Los géneros se obtienen al buscar desde la app. Si guardaste contenido de otra fuente, vuelve a buscarlo en MediaVerse y guárdalo de nuevo.

**¿Puedo exportar mis datos?**
Los JSON están en `~/Documents/MediaVerse/userdata/`. Puedes copiar esa carpeta completa como backup en cualquier momento.

**¿Hay versión web o móvil?**
Solo escritorio (Windows por ahora). Mac y Linux requieren compilar desde código fuente.

**La app no arranca**
Comprueba que el fichero `.env` está en la carpeta `app\` con las claves de TMDB e IGDB rellenas. Ver [SETUP.md](./SETUP.md).
