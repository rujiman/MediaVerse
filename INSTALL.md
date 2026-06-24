# 💾 Instalación — MediaVerse

Elige tu sistema operativo:

- [Windows](#windows)
- [macOS](#macos)
- [Linux](#linux)
- [Desde Código Fuente](#desde-código-fuente)

---

## Windows

### Instalador Recomendado (Más Fácil)

1. Descarga **`MediaVerse-1.0-Installer.exe`** desde [Releases](https://github.com/rujiman/mediaverse/releases)
2. **Doble clic** para ejecutar
3. Elige carpeta de instalación (por defecto: `C:\Program Files\MediaVerse`)
4. El instalador incluye **Java 21 integrado** — sin necesidad de instalación previa
5. **Sigue el wizard** (Siguiente, Siguiente, Instalar)
6. ¡Listo! Se creará un acceso directo en el escritorio

### Ejecutar la App
- **Desde el escritorio**: Doble clic en el icono "MediaVerse"
- **Desde el menú inicio**: Busca "MediaVerse"

### Configurar API Keys
1. Abre la carpeta de instalación: `C:\Program Files\MediaVerse\`
2. Busca el archivo **`.env.example`**
3. Cópialo y renómbralo a **`.env`**
4. **Abre `.env` con Bloc de Notas**
5. Rellena tus claves (ver [SETUP.md](./SETUP.md))
6. **Guarda** (Ctrl+S)
7. **Reinicia MediaVerse**

### Desinstalar
- **Configuración** → Aplicaciones → Busca "MediaVerse" → Desinstalar
- O ejecuta `uninstall.exe` en la carpeta de instalación

---

## macOS

### Instalador DMG (Recomendado)

1. Descarga **`MediaVerse-1.0.dmg`** desde [Releases](https://github.com/rujiman/mediaverse/releases)
2. **Doble clic** para abrir el DMG
3. **Arrastra el icono de MediaVerse** a la carpeta "Applications"
4. Espera a que termine la copia
5. **Abre Aplicaciones** (Cmd+Space, escribe "Aplicaciones")
6. Busca **MediaVerse** y doble clic

### Permitir Aplicación de Terceros (Si Aparece Pop-up)
Si macOS dice "No se puede abrir porque es de desarrollador no identificado":
1. **System Preferences** → **Security & Privacy**
2. Pestaña **General**
3. Pulsa "Abrir de todas formas" junto a MediaVerse
4. **Confirma con tu contraseña**

### Configurar API Keys
1. **Finder** → **Aplicaciones** → **MediaVerse** (clic derecho)
2. **"Show Package Contents"**
3. Navega a **Contents** → **Resources**
4. Busca **`.env.example`** → cópialo y renómbralo a **`.env`**
5. Edita con **TextEdit** (abre con → TextEdit → Formato → Texto Plano)
6. Rellena claves (ver [SETUP.md](./SETUP.md))
7. **Guarda**
8. **Reinicia MediaVerse**

### Desinstalar
- **Finder** → **Aplicaciones**
- Arrastra **MediaVerse** a la **Papelera**
- Vacía la papelera

---

## Linux

### Instalador .deb (Debian/Ubuntu)

1. Descarga **`mediaverse_1.0_amd64.deb`** desde [Releases](https://github.com/rujiman/mediaverse/releases)
2. Abre terminal y navega a la carpeta de descarga
3. Ejecuta:
   ```bash
   sudo dpkg -i mediaverse_1.0_amd64.deb
   ```
4. Instala dependencias si es necesario:
   ```bash
   sudo apt-get install -f
   ```
5. **Listo** — MediaVerse está instalado

### Ejecutar
```bash
mediaverse
```

O busca en tu menú de aplicaciones.

### Instalador .rpm (Red Hat/Fedora)

1. Descarga **`mediaverse-1.0-1.x86_64.rpm`** desde [Releases](https://github.com/rujiman/mediaverse/releases)
2. Terminal:
   ```bash
   sudo rpm -i mediaverse-1.0-1.x86_64.rpm
   ```

### Configurar API Keys
1. **Terminal**:
   ```bash
   nano /usr/share/mediaverse/.env.example
   ```
2. Copia el contenido
3. Crea `.env` en la misma carpeta:
   ```bash
   nano /usr/share/mediaverse/.env
   ```
4. Pega y rellena claves (ver [SETUP.md](./SETUP.md))
5. **Ctrl+O** → Enter → **Ctrl+X** para guardar

### Desinstalar
```bash
# Debian/Ubuntu
sudo apt-get remove mediaverse

# Red Hat/Fedora
sudo rpm -e mediaverse
```

---

## Desde Código Fuente

Para desarrolladores o si quieres compilar tú mismo.

### Requisitos Previos
- **Java 21** (o superior) — [Descarga OpenJDK](https://jdk.java.net/21/)
- **Maven 3.8+** — [Descarga Maven](https://maven.apache.org/download.cgi)
- **Git** — [Descarga Git](https://git-scm.com/)

### Pasos

1. **Clona el repositorio**:
   ```bash
   git clone https://github.com/rujiman/mediaverse.git
   cd mediaverse
   ```

2. **Crea el archivo `.env`** (copia de `.env.example`):
   ```bash
   cp .env.example .env
   ```

3. **Rellena tus API keys** en `.env` (ver [SETUP.md](./SETUP.md))

4. **Compila con Maven**:
   ```bash
   mvn clean package
   ```

   Esto genera un JAR executable en `target/mediaverse-1.0.jar`

5. **Ejecuta la app**:
   ```bash
   mvn javafx:run
   ```

   O directamente:
   ```bash
   java -jar target/mediaverse-1.0.jar
   ```

### Crear Ejecutable (.exe) Personalizado
Si quieres generar un .exe desde el código:

```bash
mvn clean package jpackage:jpackage
```

Genera un instalador en `target/dist/`

---

## Verificar Instalación

Después de instalar, abre la app y verifica:

### ✅ Señales de Éxito
- App se abre sin errores
- Pantalla de **Login** aparece
- En consola ves: **"✅ Variables de entorno cargadas correctamente"**
- Puedes buscar series/películas/anime

### ❌ Señales de Error
- "TMDB_API_KEY no configurada" → Falta configurar `.env` (ver [SETUP.md](./SETUP.md))
- Pantalla blanca/crash → Java no está instalado correctamente
- Conexión error en búsquedas → API keys inválidas o sin conexión

---

## Requisitos del Sistema

| Componente | Mínimo | Recomendado |
|-----------|--------|------------|
| **Java** | 21 | 21+ |
| **RAM** | 1 GB | 2+ GB |
| **Almacenamiento** | 100 MB | 500 MB |
| **Internet** | Requerido | Para búsquedas |

---

## Actualizar a Nueva Versión

### Si Usas Instalador
1. **Desinstala** la versión anterior
2. **Instala** la nueva versión
3. Tus datos en `userdata/` se preservan automáticamente

### Si Compilas Desde Código
```bash
git pull origin main
mvn clean package
```

---

## Troubleshooting

### La app no inicia
**Solución**: Verifica que Java 21+ está instalado:
```bash
java -version
```

Deberías ver algo como: `openjdk 21.0.2`

Si no aparece, [descarga Java 21](https://jdk.java.net/21/)

### "Connection refused" en búsquedas
**Soluciones**:
- Verifica conexión a Internet
- Comprueba que las API keys son válidas (ver [SETUP.md](./SETUP.md))
- Los servicios (TMDB, IGDB) pueden estar temporalmente caídos

### La app es lenta / se congela
- Es normal en búsquedas iniciales (espera 5-10 seg)
- Si se cuelga completamente, cierra y reabre
- Revisa que tu RAM disponible > 1 GB

### Problemas de Permissions (Linux)
Si ves errores de permisos al crear carpetas:
```bash
sudo chown -R $USER /usr/share/mediaverse
```

---

**¿Problemas con instalación?** Abre un [issue en GitHub](https://github.com/rujiman/mediaverse/issues).

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Trabajo Final de Grado (TFG) en el ciclo DAM — 2025
