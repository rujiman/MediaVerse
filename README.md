# 🎬 MediaVerse

**Tu universo de entretenimiento en una sola app.** Gestiona y organiza todo lo que ves, juegas y escuchas.

## ✨ Características

### 📺 Búsqueda y Descubrimiento
- **Series y Películas**: Búsqueda integrada en TMDB (The Movie Database)
- **Anime**: Catalogado desde AniList
- **Videojuegos**: Base de datos completa de IGDB
- **Música**: Canciones y álbumes desde Deezer

### ⭐ Sistema de Favoritos
- Guarda contenido favorito de cualquier tipo
- Crea carpetas personalizadas para organizar
- Marca episodios o temas vistos/escuchados
- Valoración personal (1-5 estrellas)

### 📊 Dashboards Personalizados
- Tu propia pantalla de inicio "Mi MediaVerse"
- Secciones fijas: Juegos, Series, Anime, Películas, Música
- Selector visual para elegir qué mostrar
- Acceso directo a tus favoritos más importantes

### 🎯 Listas de Seguimiento
- "Pienso ver" — series y películas en tu lista de deseos
- "Pienso jugar" — juegos por probar
- "Pienso escuchar" — música para descubrir
- Carpetas dentro de cada lista para mayor organización

### 📱 Plataformas de Streaming
- Ve directamente desde Netflix, Crunchyroll, Disney+, Prime Video, etc.
- Selector automático de plataformas disponibles
- Compatible con todos los proveedores de streaming en España

### 🎨 Diseño Moderno
- Interfaz oscura con paleta "Constelaciones"
- Responsive y fluida
- Temas por tipo de contenido (colores únicos para series, anime, películas)

## 🚀 Requisitos del Sistema

- **Java 21** o superior
- **Conexión a Internet** (para búsquedas y APIs)
- **Windows, macOS o Linux**
- Mínimo 2 GB de RAM
- 100 MB de espacio en disco

## 📦 Instalación

### Opción 1: Ejecutable (.exe) — Recomendado para Windows
Descarga el instalador desde [Releases](https://github.com/rujiman/mediaverse/releases) y ejecuta. Incluye Java 21 integrado, sin necesidad de instalación previa.

### Opción 2: Desde Código Fuente
```bash
git clone https://github.com/rujiman/mediaverse.git
cd mediaverse
mvn clean javafx:run
```

Requiere Java 21 y Maven instalados.

## 🔧 Configuración

Antes de usar la app, debes configurar tus claves de API:

1. Copia `.env.example` a `.env`
2. Rellena con tus claves (ver [SETUP.md](./SETUP.md)):
   - `TMDB_API_KEY` (obligatorio)
   - `IGDB_CLIENT_ID` y `IGDB_CLIENT_SECRET` (obligatorio)

3. ¡Listo! Inicia la app.

## 📖 Guía Rápida

1. **Crear cuenta**: Primera vez que abres la app, regístrate
2. **Buscar contenido**: Usa la barra de búsqueda
3. **Agregar a favoritos**: Pulsa ⭐ desde el detalle
4. **Crear carpetas**: Desde Favoritos (botón ✏️)
5. **Personalizar inicio**: En "Mi MediaVerse", elige favoritos con ✏️

Ver [GUÍA DE USO COMPLETA](./GUIA_DE_USO.md) para más detalles.

## 🛠️ APIs Utilizadas

| Servicio | Uso | Gratuito |
|----------|-----|----------|
| **TMDB** | Series, películas, plataformas | ✅ Sí |
| **AniList** | Anime (con GraphQL) | ✅ Sí |
| **IGDB** | Videojuegos | ✅ Sí |
| **Deezer** | Música, previsualizaciones | ✅ Sí |

Todas tienen planes gratuitos sin límites de uso personal.

## 🔐 Seguridad de Datos

### ⚠️ **TUS DATOS NUNCA SALEN DE TU ORDENADOR**

**MediaVerse es 100% local.** Todo se guarda en archivos JSON en tu disco duro:

```
userdata/
├── users.json
├── profile.json
└── <usuario>/
    ├── favoritos/favorites.json
    ├── dashboard/dashboard.json
    ├── progreso/progress.json
    ├── plan/{plan_watch,plan_play,plan_listen}.json
    └── carpetas/folders_*.json
```

✅ **Ningún servidor remoto**  
✅ **Nadie puede acceder a tus datos**  
✅ **Completo control local**  
✅ **Privacidad garantizada**  

Solo tú tienes acceso a tu carpeta `userdata/`. Los datos no se sincronizan, no se envían, no se analizan. **Punto.**

## 🐛 Problemas Comunes

**"TMDB_API_KEY no configurada"**
→ Falta crear/rellenar el archivo `.env` con tus claves

**"No se cargan los géneros en películas"**
→ Vuelve a guardar la película desde una búsqueda nueva de TMDB

**La app se cierra al abrir detalles**
→ Comprueba que tienes conexión a Internet y que tus claves de API son válidas

Ver [FAQ](./GUIA_DE_USO.md#faq) para más.

## 📚 Documentación

- [Guía de Uso Completa](./GUIA_DE_USO.md) — Paso a paso para usuarios
- [Configuración de APIs](./SETUP.md) — Cómo obtener claves
- [Instalación Detallada](./INSTALL.md) — Para cada sistema operativo
- [Dependencias y Librerías](./DEPENDENCIES.md) — Qué usa MediaVerse
- [Crear .exe](./BUILD_EXE.md) — Empaquetar para distribución
- [Changelog](./CHANGELOG.md) — Historial de versiones

## 🤝 Contribuciones

Este es un **Trabajo Final de Grado (TFG)** en el ciclo DAM (Desarrollo de Aplicaciones Multiplataforma). No aceptamos contribuciones externas en este momento, pero tus sugerencias son bienvenidas.

## 📄 Licencia

Proyecto de educación. Libre para uso personal y educativo.

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Desarrollado como Trabajo Final de Grado (TFG) en el ciclo DAM — 2025

---

**¿Necesitas ayuda?** Consulta la [Guía de Uso](./GUIA_DE_USO.md) o abre un issue en GitHub.
