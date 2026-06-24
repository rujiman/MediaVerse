# 💾 Instalación — MediaVerse

---

## 🪟 Windows (Recomendado)

### ⚠️ IMPORTANTE: Ruta de Instalación

**Instala siempre en `C:\MediaVerse`** (cambia la ruta durante la instalación)

Esto evita problemas de permisos de Windows.

---

### 1. Descargar Instalador

Descarga **`MediaVerse-1.0.0-Setup.exe`** desde [Releases](https://github.com/rujiman/mediaverse/releases)

### 2. Ejecutar Instalador

1. **Doble clic** en el `.exe`
2. En **"Select Destination Location"**, cambia a `C:\MediaVerse`
3. **Siguiente → Siguiente → Instalar**
4. ¡Listo! Se crea acceso directo en el escritorio

### 3. Configurar Claves

1. Abre `C:\MediaVerse\`
2. Copia `.env.example` → renómbralo a `.env`
3. Abre `.env` con **Bloc de Notas**
4. Rellena tus claves (ver [SETUP.md](./SETUP.md))
5. **Guarda** (Ctrl+S) y **reinicia la app**

### 4. Usar

- **Doble clic** en el icono del escritorio
- O busca "MediaVerse" en el menú inicio

---

## 🐧 Linux / macOS

### 1. Clona el Repositorio

```bash
git clone https://github.com/rujiman/mediaverse.git
cd mediaverse
```

### 2. Configura Claves

```bash
cp .env.example .env
nano .env
```

Rellena tus claves (ver [SETUP.md](./SETUP.md))

Guarda: **Ctrl+X → Y → Enter**

### 3. Ejecuta

```bash
mvn javafx:run
```

**Requisitos previos:**
- Java 21+ → [Descarga aquí](https://jdk.java.net/21/)
- Maven 3.8+ → [Descarga aquí](https://maven.apache.org/download.cgi)

---

## ✅ Verificar que Funciona

1. **Abre MediaVerse**
2. **Busca una serie** (ej: "Breaking Bad")
3. Si ves resultados → ¡**Listo!**

---

## ❌ Problemas Comunes

| Problema | Solución |
|----------|----------|
| "TMDB_API_KEY no configurada" | Falta crear/rellenar `.env` (ver [SETUP.md](./SETUP.md)) |
| "Java no encontrado" | Instala [Java 21+](https://jdk.java.net/21/) |
| "Maven no encontrado" | Instala [Maven](https://maven.apache.org/download.cgi) |
| La app es lenta | Normal en primeras búsquedas (5-10 seg) |

---

**¿Necesitas ayuda?** Consulta [SETUP.md](./SETUP.md) o abre un [issue en GitHub](https://github.com/rujiman/mediaverse/issues).
