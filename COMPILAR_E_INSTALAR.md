# 🔨 Compilar e Instalar MediaVerse — Guía para Desarrolladores

Esta guía explica cómo **compilar el proyecto desde cero** y **crear un instalador .exe profesional** para distribuir.

---

## 📋 Requisitos Previos

Antes de empezar, necesitas:

- **Java 21+** → [Descarga OpenJDK 21](https://jdk.java.net/21/)
- **Maven 3.8+** → [Descarga Maven](https://maven.apache.org/download.cgi)
- **Git** → [Descarga Git](https://git-scm.com/)
- **InnoSetup 6+** → [Descarga InnoSetup](https://jrsoftware.org/isdl.php)

---

## Paso 1: Instalar Maven

### 1.1 Descargar Maven

1. Ve a [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)
2. Descarga **`apache-maven-3.9.x-bin.zip`** (versión .zip)
3. Descomprime en una carpeta (ej: `C:\maven\apache-maven-3.9.x\`)

### 1.2 Agregar Maven al PATH

Opción A (Recomendada - Línea de comandos):

```powershell
[Environment]::SetEnvironmentVariable("Path", "$env:Path;C:\ruta\a\tu\maven\bin", "Machine")
```

Reemplaza `C:\ruta\a\tu\maven\bin` con tu ruta real.

Opción B (Manual - GUI):

1. **Panel de Control** → **Variables de entorno**
2. Edita la variable `Path`
3. Agrega: `C:\ruta\a\tu\maven\bin`
4. OK

### 1.3 Verificar Maven

```bash
mvn -version
```

Debe mostrar: `Apache Maven 3.9.x`

---

## Paso 2: Compilar el Proyecto

### 2.1 Abre Terminal en la Carpeta del Proyecto

```bash
cd C:\ruta\a\mediatracker
```

### 2.2 Compila Todo

```bash
mvn clean package
```

**¿Qué pasa?**
- Maven descarga todas las librerías
- Compila tu código Java
- Crea un **Fat JAR** con todo incluido
- Guarda en `target/MediaVerse.jar` (~44 MB)

⏱️ **Tarda 5-10 minutos la primera vez**

Cuando termine, verás: **BUILD SUCCESS**

---

## Paso 3: Crear el Script de Ejecución

### 3.1 Crea `run-mediaverse.bat`

En la **carpeta raíz de tu proyecto**, crea un archivo llamado `run-mediaverse.bat`:

```batch
@echo off
setlocal enabledelayedexpansion

REM Obtener ruta de instalación
set INSTALL_DIR=%~dp0

REM Configurar ruta de Java y Maven
set PATH=%INSTALL_DIR%jdk\bin;%INSTALL_DIR%maven\bin;%PATH%

REM Ir a carpeta de proyecto
cd /d "%INSTALL_DIR%"

REM Ejecutar
echo Iniciando MediaVerse...
echo.
call mvn javafx:run

pause
```

Guarda tal cual (incluye los comentarios).

---

## Paso 4: Instalar InnoSetup

### 4.1 Descargar e Instalar

1. Ve a [https://jrsoftware.org/isdl.php](https://jrsoftware.org/isdl.php)
2. Descarga **Inno Setup 6** (versión `innosetup-6-7-3.exe`)
3. Instala normalmente (Next > Next > Finish)

### 4.2 Verificar Instalación

Abre **InnoSetup** desde el menú inicio.

---

## Paso 5: Crear el Instalador

### 5.1 Crear el Script de InnoSetup

En la carpeta raíz de tu proyecto, crea `mediaverse-installer.iss`:

```ini
[Setup]
AppName=MediaVerse
AppVersion=1.0.0
AppPublisher=Rujiman
DefaultDirName={pf}\MediaVerse
DefaultGroupName=MediaVerse
OutputDir=output
OutputBaseFilename=MediaVerse-1.0.0-Setup
SetupIconFile=C:\ruta\a\tu\proyecto\mediaverse_logo.ico
WizardStyle=modern
PrivilegesRequired=lowest
Compression=lzma
SolidCompression=yes

[Files]
; Java 25
Source: "C:\ruta\a\java\jdk-25\*"; DestDir: "{app}\jdk"; Flags: ignoreversion recursesubdirs createallsubdirs

; Maven
Source: "C:\ruta\a\maven\apache-maven-3.9.x\*"; DestDir: "{app}\maven"; Flags: ignoreversion recursesubdirs createallsubdirs

; Proyecto
Source: "C:\ruta\a\tu\proyecto\src\*"; DestDir: "{app}\src"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "C:\ruta\a\tu\proyecto\pom.xml"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\ruta\a\tu\proyecto\.env.example"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\ruta\a\tu\proyecto\README.md"; DestDir: "{app}"; Flags: ignoreversion

; Script de inicio
Source: "C:\ruta\a\tu\proyecto\run-mediaverse.bat"; DestDir: "{app}"; Flags: ignoreversion

; Logo
Source: "C:\ruta\a\tu\proyecto\mediaverse_logo.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\MediaVerse"; Filename: "{app}\run-mediaverse.bat"; IconFilename: "{app}\mediaverse_logo.ico"
Name: "{commondesktop}\MediaVerse"; Filename: "{app}\run-mediaverse.bat"; IconFilename: "{app}\mediaverse_logo.ico"

[Run]
Filename: "{app}\run-mediaverse.bat"; Description: "Ejecutar MediaVerse"; Flags: nowait postinstall skipifsilent
```

**Reemplaza `C:\ruta\a\...` con las rutas reales de tu sistema.**

### 5.2 Compilar el Instalador

1. **Abre InnoSetup**
2. **File** → **Open** → `mediaverse-installer.iss`
3. **Build** → **Compile** (o F9)

Espera a que termine. Verás:

```
Successfully created C:\ruta\a\tu\proyecto\output\MediaVerse-1.0.0-Setup.exe
```

**El `.exe` está listo en la carpeta `output/`**

---

## Paso 6: Verificar el Instalador

### 6.1 Instalar Localmente

1. Ve a `output/MediaVerse-1.0.0-Setup.exe`
2. **Doble clic** para instalar
3. Sigue el wizard (Next, Install, Finish)
4. Se instala en `C:\Program Files (x86)\MediaVerse\`
5. **Abre desde el escritorio** para verificar que funciona

---

## Paso 7: Subir a GitHub

### 7.1 Crear Repositorio

En [GitHub](https://github.com), crea un repo nuevo:
- Nombre: `mediaverse`
- Descripción: "Tu universo de entretenimiento en una sola app"
- Público

### 7.2 Subir Código

En tu terminal:

```bash
cd C:\ruta\a\tu\proyecto
git init
git add .
git commit -m "MediaVerse v1.0.0 - Initial release"
git branch -M main
git remote add origin https://github.com/tu-usuario/mediaverse.git
git push -u origin main
```

Reemplaza `tu-usuario` con tu usuario de GitHub.

### 7.3 Crear Release en GitHub

1. Ve a tu repo en GitHub
2. **Releases** → **Create a new release**
3. **Tag version**: `v1.0.0`
4. **Release title**: `MediaVerse 1.0.0`
5. **Description**:

```markdown
# MediaVerse 1.0.0

Tu universo de entretenimiento en una sola app.

## 📥 Descarga

- **MediaVerse-1.0.0-Setup.exe** - Instalador para Windows

## 🚀 Instalación

1. Descarga el `.exe`
2. Doble clic para instalar
3. Abre desde el escritorio

**No necesitas instalar nada más. Java 25 y Maven están incluidos.**

## 📖 Documentación

- [Guía de Uso](./GUIA_DE_USO.md)
- [Configuración de APIs](./SETUP.md)
- [Instalación](./INSTALL.md)
- [Dependencias](./DEPENDENCIES.md)

## ✨ Features

- Búsqueda de Series, Películas, Anime, Juegos, Música
- Sistema de favoritos con carpetas personalizadas
- Listas de seguimiento ("Pienso Ver", "Pienso Jugar", etc.)
- Dashboard personalizado
- Plataformas de streaming integradas
- Interfaz oscura moderna

## 🛠️ Stack Técnico

- **Java 21+**
- **JavaFX 25** (UI)
- **TMDB + AniList + IGDB + Deezer** (APIs)
- **Maven** (Build)

## 👨‍💻 Autor

**Ruben Jimenez Manzano (Rujiman)**

TFG DAM — 2025
```

6. **Attach files** → Sube `MediaVerse-1.0.0-Setup.exe` desde `output/`
7. **Publish release**

---

## Troubleshooting

### Maven no se reconoce
```bash
mvn -version
```
Si no funciona, reinicia PowerShell como Administrador y asegúrate de que Maven está en PATH.

### El compilador falla
```bash
mvn clean package -DskipTests
```
Compila sin ejecutar tests (más rápido si hay problemas).

### InnoSetup no encuentra archivos
- Verifica que las rutas en `mediaverse-installer.iss` existen
- Usa rutas completas, no relativas

### El .exe no se abre
- Asegúrate de que Java y Maven están en las carpetas correctas dentro del instalador
- El usuario debe tener permisos de escritura en la carpeta de instalación

---

## Versiones Futuras

Cuando saques v1.1, v1.2, etc.:

1. Cambia versión en `pom.xml`: `<version>1.1.0</version>`
2. Actualiza `mediaverse-installer.iss`: 
   - `AppVersion=1.1.0`
   - `OutputBaseFilename=MediaVerse-1.1.0-Setup`
3. Compila: `mvn clean package`
4. Crea nuevo `.iss` en InnoSetup con la versión nueva
5. Nuevo `.exe` se genera en `output/`
6. Crea nuevo Release en GitHub con la versión

---

## 🚀 Extender MediaVerse

MediaVerse es **open source** y puedes modificarlo, mejorarlo o crear versiones derivadas. Solo pide que:

### ✅ Si Modificas o Extiendes la App

1. **Cita el trabajo original**:
   - En tu README: *"Basado en MediaVerse de Ruben Jimenez Manzano (Rujiman)"*
   - En comentarios del código: *"Original: Rujiman - TFG DAM 2025"*

2. **Contribuye mejoras** (opcional pero bienvenidas):
   - Fork el repo
   - Crea una rama: `git checkout -b feature/tu-mejora`
   - Haz cambios y commit: `git commit -m "Agrega: tu mejora"`
   - Push: `git push origin feature/tu-mejora`
   - Abre un Pull Request

3. **Ejemplos de extensiones posibles**:
   - Integración con más APIs (Spotify nativo, Letterboxd, etc.)
   - Modo claro + más temas
   - Exportación de datos (CSV, PDF)
   - Sincronización en la nube
   - Versión web (React/Vue)
   - Aplicación móvil

---

## 📋 Licencia

MediaVerse es un **proyecto educativo (TFG)** distribuido bajo **licencia MIT**.

Eres libre de:
- ✅ Usar, modificar y distribuir
- ✅ Fines personales y comerciales
- ❌ **Pero debes citar el original**

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Trabajo Final de Grado (TFG) en el ciclo DAM — 2025

**GitHub**: [https://github.com/rujiman/mediaverse](https://github.com/rujiman/mediaverse)
