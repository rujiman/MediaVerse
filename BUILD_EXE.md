# 🚀 Generar el portable — MediaVerse

Esta guía es para **desarrolladores** que quieran generar el zip portable de MediaVerse desde el código fuente.

---

## Requisitos

- Java 25 (JDK completo, no solo JRE)
- Maven 3.8+
- JavaFX jmods 25 descargados en local

---

## Paso 1: Descargar los JavaFX jmods

Los jmods son necesarios para que `jpackage` empaquete correctamente las librerías nativas de JavaFX. **Son distintos al SDK de JavaFX** — asegúrate de descargar el paquete de tipo "jmods".

1. Ve a [https://gluonhq.com/products/javafx/](https://gluonhq.com/products/javafx/)
2. Selecciona: versión **25**, sistema **Windows**, arquitectura **x64**, tipo **jmods**
3. Descarga y descomprime en una carpeta conocida, por ejemplo `C:\javafx\javafx-jmods-25\`

Comprueba que dentro de esa carpeta hay ficheros `.jmod` (`javafx.base.jmod`, `javafx.graphics.jmod`, etc.).

---

## Paso 2: Compilar el fat jar

```powershell
mvn clean package
```

Genera `target\MediaVerse.jar` (~44 MB), que incluye todas las dependencias dentro.

---

## Paso 3: Preparar la carpeta de input limpia

`jpackage` toma todos los jars que encuentre en la carpeta de input. Para evitar que incluya los jars intermedios que Maven Shade genera como subproducto, copia solo el jar final a una carpeta limpia:

```powershell
mkdir input-clean
Copy-Item target\MediaVerse.jar input-clean\
```

---

## Paso 4: Generar el portable con jpackage

```powershell
& "C:\ruta\a\tu\jdk-25\bin\jpackage.exe" `
  --input input-clean `
  --main-jar MediaVerse.jar `
  --main-class com.rujiman.mediatracker.Main `
  --name MediaVerse `
  --app-version 1.2.0 `
  --type app-image `
  --icon mediaverse_logo.ico `
  --module-path "C:\javafx\javafx-jmods-25" `
  --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.media,java.base,java.desktop,java.sql,java.naming,java.management,java.security.jgss `
  --dest dist
```

Ajusta las rutas a tu JDK y a tu carpeta de jmods.

El resultado es una carpeta `dist\MediaVerse\` con esta estructura:

```
dist\MediaVerse\
├── MediaVerse.exe
├── app\
│   └── MediaVerse.jar
└── runtime\
    └── (Java 25 + JavaFX embebidos)
```

---

## Paso 5: Añadir el .env.example

Copia el `.env.example` a la carpeta `app\` para que el usuario sepa qué fichero tiene que crear:

```powershell
Copy-Item .env.example dist\MediaVerse\app\.env.example
```

---

## Paso 6: Comprimir y distribuir

```powershell
Compress-Archive -Path dist\MediaVerse -DestinationPath MediaVerse-portable.zip
```

Sube `MediaVerse-portable.zip` a GitHub Releases. El usuario lo descarga, descomprime, pone su `.env` en `app\` y ejecuta `MediaVerse.exe`. No necesita instalar nada.

---

## Paso 7 (recomendado): Publicar con GitHub Actions

Desde la 1.2.1 no hace falta compilar nada a mano. El workflow `.github/workflows/release.yml` lo hace todo en los servidores de GitHub:

1. Sube la versión en `util/AppInfo.java` (`VERSION`) y en `pom.xml`
2. Añade la sección `## [X.Y.Z]` en `CHANGELOG.md` (será el texto de la Release)
3. Haz commit y push
4. Crea y sube la etiqueta:
   ```bash
   git tag v1.2.2
   git push origin v1.2.2
   ```
5. En la pestaña **Actions** de GitHub verás "Release" en marcha (~5 min). Al terminar, la Release tiene los 4 archivos:
   - `MediaVerse.jar` y `MediaVerse-portable.zip` (Windows)
   - `MediaVerse-linux.jar` y `MediaVerse-linux.tar.gz` (Linux)

Si algo falla, corrige, haz push y relánzalo desde **Actions → Release → Run workflow** indicando la etiqueta.

Los pasos 1-6 de esta guía quedan para compilar a mano en tu PC si alguna vez lo necesitas.

---

## Paso 7b: Publicar una versión a mano (con actualización automática)

Desde la 1.2, las apps instaladas se actualizan solas leyendo la **última Release** de GitHub. Para que funcione:

1. **Sube la versión** en `src/main/java/com/rujiman/mediatracker/util/AppInfo.java` (`VERSION = "1.3.0"`) y en `pom.xml`. **Antes** de compilar.
2. Compila y genera el portable (pasos 2 a 6).
3. En GitHub → **Releases → Draft a new release**:
   - **Tag**: `v1.3.0` (la `v` + la misma versión que `AppInfo.VERSION`)
   - **Notas**: las novedades (las verá el usuario en el aviso de actualización)
   - **Archivos**, con estos nombres EXACTOS:
     - `MediaVerse.jar` → el de `target\` (lo descarga el actualizador, ~15 MB)
     - `MediaVerse-portable.zip` → para instalaciones nuevas
4. Publica la Release. Las apps la detectarán al abrirse y mostrarán **"Actualizar a 1.3.0"**.

**¿Cuándo hace falta reinstalar en vez de actualizar?** Si cambias la versión de Java o JavaFX (el `runtime\` cambia y no basta con el jar). En ese caso escribe `[instalación completa]` en las notas de la Release: la app abrirá la página de descarga en vez de actualizarse sola.

**Enlaces fijos para la web de descarga** (siempre apuntan a la última versión):
- Windows: `https://github.com/rujiman/MediaVerse/releases/latest/download/MediaVerse-portable.zip`
- Linux: `https://github.com/rujiman/MediaVerse/releases/latest/download/MediaVerse-linux.tar.gz`

---

## Notas importantes

- El `runtime\` pesa varios cientos de MB (Java 25 + JavaFX embebidos). Es normal.
- Desde la 1.3.3 el exe se genera **sin** `--win-console`: no aparece la ventana negra al abrir la app. Los mensajes de la app se guardan en `Documentos\MediaVerse\userdata\mediaverse.log`. Si quieres depurar con consola, añade `--win-console` al comando.
- Si cambias código, repite los pasos 2, 3 y 4. El paso 1 solo hay que hacerlo una vez.
- El `.env` del usuario nunca va dentro del zip que distribuyes — cada uno pone el suyo propio con sus claves.
