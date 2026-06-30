# 🔧 Configuración de APIs — MediaVerse

MediaVerse necesita claves de dos servicios externos para funcionar. Ambos son **gratuitos** y no requieren tarjeta de crédito.

| API | Obligatoria | Uso |
|-----|-------------|-----|
| TMDB | ✅ Sí | Series, películas, plataformas de streaming |
| IGDB | ✅ Sí | Videojuegos |
| AniList | ❌ No | Anime (sin configuración, funciona solo) |
| Deezer | ❌ No | Música (sin configuración, funciona solo) |

---

## Paso previo: crear el fichero `.env`

Dentro de la carpeta `app\` de tu MediaVerse descomprimido (o en la raíz del proyecto si compilas desde código), crea un fichero llamado `.env` copiando el `.env.example` que viene incluido. Ábrelo con el Bloc de Notas o cualquier editor de texto.

---

## 🎬 TMDB — Series y Películas

1. Crea una cuenta en [https://www.themoviedb.org](https://www.themoviedb.org)
2. Inicia sesión → haz clic en tu perfil → **Settings**
3. En el menú izquierdo, selecciona **API**
4. Haz clic en **Create** → selecciona **Developer**
5. Rellena el formulario con cualquier descripción (es para uso personal/educativo)
6. Copia la **API Key (v3 auth)**

Pégala en tu `.env`:
```
TMDB_API_KEY=tu_clave_aqui
```

---

## 🎮 IGDB — Videojuegos

IGDB usa la infraestructura de autenticación de Twitch. Necesitas una cuenta de Twitch (gratuita).

1. Ve a [https://dev.twitch.tv/console](https://dev.twitch.tv/console) e inicia sesión con tu cuenta de Twitch
2. Haz clic en **Register Your Application**
3. Rellena:
   - **Name**: MediaVerse (o cualquier nombre)
   - **OAuth Redirect URLs**: `http://localhost`
   - **Category**: Application Integration
4. Haz clic en **Create**
5. En tu aplicación creada, haz clic en **New Secret**
6. Copia el **Client ID** y el **Client Secret**

Pégalos en tu `.env`:
```
IGDB_CLIENT_ID=tu_client_id
IGDB_CLIENT_SECRET=tu_client_secret
```

> Si la búsqueda de juegos da error 403 "invalid client secret", ve a tu consola de Twitch, genera un nuevo secret con **New Secret** y actualiza el `.env`.

---

## Fichero `.env` completo

```ini
# TMDB — Series y Películas (obligatorio)
TMDB_API_KEY=

# IGDB — Videojuegos (obligatorio)
IGDB_CLIENT_ID=
IGDB_CLIENT_SECRET=
```

Reglas importantes al editar el fichero:
- Sin comillas alrededor de los valores: `TMDB_API_KEY=abc123` ✅
- Sin espacios antes o después del `=`: `TMDB_API_KEY = abc123` ❌
- Las líneas que empiezan por `#` son comentarios y se ignoran

---

## Verificar que todo funciona

Al arrancar la app, la consola debe mostrar:
```
✅ Variables de entorno cargadas correctamente desde: ...\app\.env
```

Para comprobar cada API:
- **Series/Películas**: busca "Breaking Bad" → deben aparecer resultados
- **Juegos**: busca "Elden Ring" → deben aparecer resultados
- **Anime**: busca "Naruto" → deben aparecer resultados (sin configuración)
- **Música**: busca "Bohemian Rhapsody" → deben aparecer resultados (sin configuración)

---

## Renovar claves

Si una clave caduca o deja de funcionar:
1. Accede al servicio correspondiente (TMDB o consola de Twitch)
2. Genera una nueva clave
3. Actualiza el valor en tu `.env`
4. Reinicia la app

---

## Privacidad

Tu `.env` contiene credenciales privadas. Si usas el proyecto desde código fuente y lo subes a GitHub, comprueba que `.env` está en tu `.gitignore` (ya viene configurado así en el proyecto). Solo se sube `.env.example`, que tiene los campos vacíos.
