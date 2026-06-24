# 📜 Changelog — MediaVerse

Todas las versiones notables de MediaVerse están documentadas aquí. El formato sigue [Keep a Changelog](https://keepachangelog.com/).

---

## [1.0.0] — 2025-06-23 (Lanzamiento Oficial)

### ✨ Características Principales

#### Sistema de Autenticación
- Registro y login local (sin servidor remoto)
- Cambio de contraseña y nombre de usuario
- Foto de perfil personalizada por usuario

#### Búsqueda y Descubrimiento
- Series desde TMDB (The Movie Database)
- Películas desde TMDB
- Anime desde AniList (GraphQL)
- Videojuegos desde IGDB
- Música desde Deezer

#### Sistema de Favoritos
- Agregar/eliminar favoritos
- Crear carpetas personalizadas
- Organizar contenido por categorías
- Vista por tipo (series, películas, anime, juegos, música)

#### Listas de Seguimiento
- "Pienso Ver" — series y películas
- "Pienso Jugar" — videojuegos
- "Pienso Escuchar" — música
- Carpetas dentro de cada lista
- Marcar como completado

#### Dashboard Personalizado ("Mi MediaVerse")
- 3 columnas grandes (Juegos, Series, Anime) — 12 slots cada una
- 2 filas (Música, Películas) — 5 slots cada una
- Selector visual para elegir favoritos
- Oculta secciones vacías automáticamente

#### Detalle de Contenido
- Información completa (título, año, puntuación, géneros, descripción)
- Plataformas de streaming (series/películas)
- Información técnica (episodios, duración, etc.)
- Tráiler integrado (YouTube embed)
- Recomendaciones similares
- Valoración personal (1-5 estrellas)

#### Progreso y Episodios
- Marcar episodios individuales como vistos
- Botones rápidos (marcar todos, desmarcar todos, "hasta episodio X")
- Contador automático de progreso

#### Plataformas de Streaming
- 11+ plataformas soportadas (Netflix, Prime, Disney+, Crunchyroll, etc.)
- Selector automático si hay múltiples opciones
- Links de búsqueda directa dentro de cada plataforma
- Compatible con todas las regiones

#### Persistencia de Datos
- JSON local (sin servidor)
- Estructura organizada en `userdata/<usuario>/` con subcarpetas por tipo
- Automigración de estructura antigua

#### Interfaz
- Tema oscuro "Constelaciones"
- Paleta de colores específica por tipo (Series, Anime, Películas, Juegos, Música)
- Responsive y fluida
- Hover effects con glow
- Diálogos oscuros (tema coherente)

#### APIs Integradas
- TMDB (series, películas, plataformas)
- AniList (anime, mediante GraphQL)
- IGDB (videojuegos)
- Deezer (música, sin registro requerido)

### 🔨 Cambios Técnicos Importantes

#### Reorganización de Persistencia
- Migración de raíz a `userdata/` centralizado
- Subcarpetas por usuario: `userdata/<usuario>/<tipo>/`
- Fotos de perfil: `userdata/<usuario>/<usuario>_profile.jpg`
- Helper centralizado: `AppPaths.java`

#### Cargas Bajo Demanda
- Plataformas de películas/series se cargan al abrir detalle si faltan
- Géneros de TMDB mediante mapa fijo (30 géneros mapeados)
- Tráileres bajo demanda (no en búsqueda)
- Recomendaciones bajo demanda (hasta 10 por item)

#### Selector de Plataformas
- Para series/películas: `StreamingLinkResolver.java`
- 11+ plataformas soportadas con URLs de búsqueda
- ChoiceDialog si hay múltiples opciones
- Fallback a TMDB si no hay plataformas reconocidas

#### Mecanismo de Géneros
- Mapa fijo TMDB `genre_id → nombre` (GENRE_MAP)
- Aplicado en `parseSeries()` y `parseMovies()`
- Géneros para anime: automático desde AniList
- Generos para juegos: desde IGDB

#### Mejoras de UI
- `renderPlatformsLoading()` muestra estado "Buscando..." mientras carga
- Generos como chips (tags) en detalle
- Mejores mensajes de error
- Protección contra carreras de condición (guardia `currentItem != itemAtRequestTime`)

### 📦 Dependencias
- **Java 21** (compilación y runtime)
- **JavaFX 25** (UI)
- **Gson 2.10.1** (JSON)
- **OkHttp 4.11.0** (HTTP)
- **SQLite JDBC 3.44.0** (aunque no en uso actual)
- **Hibernate 6.3.1** (aunque no en uso actual)

### 🐛 Bugs Conocidos / Limitaciones
- Los géneros solo aparecen si se busca desde la app (no si se importan de otra fuente)
- TMDB no proporciona deep links directos a plataformas (acuerdo con JustWatch) — se usan URLs de búsqueda
- No hay sincronización en la nube (datos 100% locales)
- No hay exportación de datos automática (solo acceso directo a JSONs)

### 🎨 Paleta de Colores ("Constelaciones")
- Fondo oscuro: `#100c1c`
- Paneles: `#1c1730`
- Acentos:
  - Anime: `#ec4dc0` (rosa)
  - Series: `#8b5cf6` (violeta)
  - Películas: `#ecb14d` (amarillo)
  - Juegos: `#4dd9ec` (cian)
  - Música: `#4dec9e` (verde)

---

## [0.9.0] — 2025-06-15 (Beta)

### ✨ Adiciones
- Soporte básico para todas las categorías
- Sistema de login/registro
- Búsqueda en TMDB y AniList
- Persistencia en JSON

### 🐛 Bugs Corregidos
- Errores de parsing de JSON en importación

---

## [0.5.0] — 2025-06-01 (Alpha)

### ✨ Características Iniciales
- Interfaz básica de búsqueda
- Soporte TMDB inicial

---

## Roadmap Futuro

### 🚀 Versión 1.1
- [ ] Mejores recomendaciones (basadas en historial)
- [ ] Modo claro (además del oscuro)
- [ ] Estadísticas (horas vistas, películas completadas, etc.)
- [ ] Búsqueda avanzada (filtros)

---

## Convenciones de Versionado

MediaVerse sigue **Semantic Versioning** ([semver.org](https://semver.org/)):

- **MAJOR** (1.0.0 → 2.0.0): Cambios incompatibles, restructuraciones
- **MINOR** (1.0.0 → 1.1.0): Nuevas features, retrocompatibles
- **PATCH** (1.0.0 → 1.0.1): Bugs corregidos

---

## Cómo Contribuir al Changelog

Si contribuyes código:
1. Agrega tu cambio bajo "Unreleased" (si aún no existe, créalo)
2. Sigue el formato: `- [Tipo] Descripción`
3. Tipos: `✨ Feature`, `🐛 Bug`, `📦 Refactor`, `📚 Docs`, `🎨 UI`

Ejemplo:
```markdown
## [Unreleased]

### ✨ Features
- Nuevo selector de idioma

### 🐛 Bugs
- Crash al cambiar usuario
```

---

**Última actualización**: 2025-06-23

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Trabajo Final de Grado (TFG) en el ciclo DAM — 2025
