# 🔧 Configuración de APIs — MediaVerse

## ⚠️ IMPORTANTE: Las APIs Son Necesarias

**MediaVerse necesita 2 claves API para funcionar** (ambas gratuitas, sin tarjeta de crédito):

| API | Necesaria | Uso |
|-----|-----------|-----|
| **TMDB** | ✅ **SÍ** | Series, películas, plataformas de streaming |
| **IGDB** | ✅ **SÍ** | Videojuegos |
| **Deezer** | ❌ Automática | Música (se configura sola) |
| **AniList** | ❌ Automática | Anime (se configura sola) |

**Resumen**: Solo necesitas configurar **TMDB** e **IGDB**. Las otras 2 son automáticas.

---

## Antes de Empezar

1. Extrae/instala MediaVerse
2. Abre la carpeta raíz del proyecto
3. Busca el archivo **`.env.example`**
4. **Cópialo y renómbralo a `.env`** (sin la palabra "example")
5. Sigue los pasos abajo para rellenar cada clave

---

## 🎬 TMDB — Series y Películas (OBLIGATORIO)

**The Movie Database** es la fuente de **series, películas, géneros y plataformas de streaming**. Sin TMDB, la búsqueda de series/películas no funciona.

### Paso a Paso: Obtener API Key

1. **Abre** [https://www.themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)
   - Si no tienes cuenta, **"Create an Account"** (gratis)
   - Completa email y contraseña
   - Verifica tu email

2. **Login** en TMDB

3. **Acepta los términos** de API (importante)

4. **Busca "API Key"** en la página (sección arriba)
   - Verás un botón **"Create"** o **"Generate API Key"**

5. **Selecciona "Developer"** como tipo de uso
   - (si pide más detalles, di que es personal/educativo)

6. **Acepta términos nuevamente**

7. **Copia tu API Key** — aparecerá como una larga cadena de caracteres
   - Ejemplo: `abc123def456ghi789jkl012mno345pqr...`

### Agregar a `.env`

Abre el archivo `.env` en la carpeta raíz y busca:
```ini
TMDB_API_KEY=
```

Cambia a:
```ini
TMDB_API_KEY=abc123def456ghi789jkl012mno345
```

**Importante**: Sin comillas alrededor de la clave.

### ✅ Verificar que Funciona

Inicia MediaVerse:
1. Si ves en la consola: **"✅ Variables de entorno cargadas correctamente"** → está bien
2. Intenta buscar una película (ej: "The Matrix")
3. Si aparecen resultados → **TMDB está configurado correctamente**

Si ves **"TMDB_API_KEY no configurada"**:
- Verifica que la línea en `.env` no está vacía
- Comprueba que no hay espacios extras: `TMDB_API_KEY=tu_clave` (sin espacios)
- Guarda el archivo y reinicia la app

---

## 🎮 IGDB — Videojuegos (OBLIGATORIO)

**Internet Game Database** proporciona **información de videojuegos, plataformas y géneros**. Sin IGDB, la búsqueda de juegos no funciona.

### Paso a Paso: Obtener Credenciales

1. **Abre** [https://api-docs.igdb.com/](https://api-docs.igdb.com/)

2. **Busca el botón "Register"** (arriba a la derecha)
   - Crea una cuenta con email y contraseña
   - Verifica tu email

3. **Login** en IGDB

4. **Accede a tu Dashboard** (desde el perfil)

5. **Busca "API Keys" o "Credentials"**
   - Verás dos campos:
     - `Client ID` — Cópialo
     - `Client Secret` — Pulsa el ojo para revelar y cópialo

6. **Nota**: IGDB puede tardar **5-10 minutos** en activar nuevas claves
   - Paciencia, es normal

### Agregar a `.env`

Abre el archivo `.env` y busca:
```ini
IGDB_CLIENT_ID=
IGDB_CLIENT_SECRET=
```

Cambia a:
```ini
IGDB_CLIENT_ID=abcd1234efgh5678
IGDB_CLIENT_SECRET=ijkl9012mnop3456
```

**Importante**: Sin comillas, sin espacios extras.

### ✅ Verificar que Funciona

1. Inicia MediaVerse
2. Busca un videojuego (ej: "Elden Ring", "The Legend of Zelda")
3. Si aparecen resultados → **IGDB está configurado correctamente**

Si ves error de conexión en búsquedas de juegos:
- Espera 10 minutos (IGDB tarda en activar claves nuevas)
- Verifica que ambos campos (`CLIENT_ID` y `CLIENT_SECRET`) están rellenos
- Copia exactamente desde IGDB sin espacios extras
- Reinicia la app

**Si el problema persiste**: Regresa a tu dashboard de IGDB y regenera las claves (botón "Reset").

---

## Deezer — Música (GRATUITO, SIN REGISTRO)

**Deezer** para música es libre y no requiere registration. **No necesitas hacer nada** — la app accede automáticamente.

Pero si quieres detalles técnicos: [Deezer API Docs](https://developers.deezer.com/guidelines)

---

## AniList — Anime (AUTOMÁTICO)

**AniList** (anime via GraphQL) **no requiere API key** — es público. No hay que hacer nada.

---

## Archivo `.env` Completo Ejemplo

```ini
# TMDB — Series y Películas (OBLIGATORIO)
TMDB_API_KEY=eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI4YTYwMDk2ZDY1Nzc3ZmY0NWI2NTY1YTk1MGI1ZGMzNiIsInN1YiI6IjY1YTMxOWE5ZjM5YTQyMDEyMzQ1Njc4OSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXIiOjF9.abc123

# IGDB — Videojuegos (OBLIGATORIO)
IGDB_CLIENT_ID=abcd1234efgh5678ijkl
IGDB_CLIENT_SECRET=ijkl9012mnop3456qrst

# Deezer — Música (automático, sin config)
# DEEZER_API_KEY= (no configurar, usa públicamente)
```

---

## Pasos Finales

### Checklist Antes de Usar MediaVerse

1. ✅ **Tienes el archivo `.env`** en la carpeta raíz (no `.env.example`)
2. ✅ **TMDB_API_KEY** está relleno (copiado desde TMDB sin comillas)
3. ✅ **IGDB_CLIENT_ID** y **IGDB_CLIENT_SECRET** están rellenos
4. ✅ **No hay espacios** al principio o final de cada valor
5. ✅ **Guardaste el archivo** (Ctrl+S)

### Iniciando MediaVerse

1. **Abre la app**
2. Mira la **consola** (debería decir):
   ```
   ✅ Variables de entorno cargadas correctamente
   ```
3. Si ves error tipo **"no configurada"**, vuelve al paso anterior

### Test de Búsqueda

Prueba cada tipo para verificar:

| Tipo | Test | Debe funcionar |
|------|------|---|
| **Series** | Busca "Game of Thrones" | Aparecen resultados de TMDB |
| **Películas** | Busca "The Matrix" | Aparecen resultados de TMDB |
| **Anime** | Busca "Naruto" | Aparecen resultados de AniList |
| **Juegos** | Busca "Minecraft" | Aparecen resultados de IGDB |
| **Música** | Busca "Bohemian Rhapsody" | Aparecen canciones de Deezer |

Si los 5 funcionan → **¡Configuración correcta!**

---

---

## Solución de Problemas

### ❌ "TMDB_API_KEY no configurada"

**Causa**: La clave TMDB está vacía, comentada o mal formateada.

**Solución**:
1. Abre el archivo `.env` con un editor de texto (Bloc de Notas, VS Code, etc.)
2. Busca la línea: `TMDB_API_KEY=`
3. Verifica:
   - ✅ Que NO comience con `#` (si comienza, quita el `#`)
   - ✅ Que tenga una clave después del `=` (no está vacío)
   - ✅ Que NO tenga comillas: `TMDB_API_KEY=abc123` (no `"abc123"`)
   - ✅ Que NO tenga espacios: `TMDB_API_KEY=abc123` (no `TMDB_API_KEY = abc123`)
4. Guarda (Ctrl+S)
5. Cierra MediaVerse completamente y reabre

### ❌ "IGDB_CLIENT_ID no configurada" o "IGDB_CLIENT_SECRET no configurada"

**Causa**: Falta rellenar las credenciales de IGDB.

**Solución**:
1. Abre `.env`
2. Busca: `IGDB_CLIENT_ID=` y `IGDB_CLIENT_SECRET=`
3. Verifica que AMBAS tienen valores (ninguna está vacía)
4. Copia desde IGDB exactamente, sin espacios
5. Guarda y reinicia la app
6. **Si aún no funciona**: IGDB tarda 5-10 minutos en activar claves nuevas. Espera y reinicia.

### ❌ Las búsquedas de Series/Películas no funcionan

**Causa**: TMDB_API_KEY inválida o expirada.

**Solución**:
1. Verifica en [https://www.themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) que tu clave sigue siendo válida
2. Si no aparece, **regenera una nueva clave** en TMDB
3. Actualiza `.env` con la nueva clave
4. Reinicia MediaVerse

### ❌ Las búsquedas de Juegos no funcionan

**Causa**: Credenciales de IGDB inválidas o aún no activadas.

**Solución**:
1. Espera **10 minutos** (las claves nuevas tarden en activarse)
2. Si sigue sin funcionar, en tu dashboard de IGDB:
   - Busca el botón **"Reset"** o **"Regenerate"**
   - Genera nuevas credenciales
   - Copia nuevamente a `.env`
   - Reinicia la app

### ❌ "Connection timeout" en búsquedas

**Causa**: Problemas de conexión a Internet o APIs caídas.

**Solución**:
1. Verifica que tienes **conexión a Internet** (abre un navegador)
2. Los servicios (TMDB, IGDB, Deezer) pueden estar temporalmente caídos
3. Espera 5-10 minutos e intenta de nuevo
4. Si el problema persiste, comprueba tu firewall/proxy

### ✅ Todo está mal pero necesito verificar que está correcto

**Checklist definitivo**:
1. Abre `.env` en el editor
2. Copia `TMDB_API_KEY=` **completo** (incluyendo la clave)
3. Ve a [https://www.themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) y verifica que coincide
4. Haz lo mismo con `IGDB_CLIENT_ID` y `IGDB_CLIENT_SECRET`
5. Si coinciden exactamente → las claves son válidas
6. Si no coinciden → copia de nuevo desde las webs oficiales
7. Reinicia la app

---

## Renovar Claves

Si una clave expira o quieres cambiarla:

1. Accede al servicio (TMDB, IGDB, etc.)
2. **Regenera** o **crea una nueva clave**
3. Actualiza el valor en `.env`
4. **Reinicia la app**

---

## Privacidad

⚠️ **Nunca compartas tu `.env`** — contiene credenciales privadas.

Si vas a subir el proyecto a GitHub:
1. Crea un archivo `.gitignore` con:
   ```
   .env
   ```
2. Así `.env` no se sube a GitHub, solo `.env.example`

---

**¿Necesitas ayuda obtener claves?** Consulta la [Guía de Uso](./GUIA_DE_USO.md) o abre un issue.

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Trabajo Final de Grado (TFG) en el ciclo DAM — 2025
