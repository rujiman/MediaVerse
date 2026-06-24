# 🚀 Crear .exe — MediaVerse con jpackage

**Los usuarios NO necesitarán tener Java instalado.** El .exe incluye Java 21 integrado.

## Requisitos Previos (Para Ti, Desarrollador)

- ✅ Java 21 instalado (para compilar)
- ✅ Maven instalado
- ✅ WiX Toolset 3.14+ (solo para Windows, gratis)
- ✅ Git
- ✅ 5-10 GB libres en disco (para el proceso de build)

---

## Paso 1: Instalar WiX Toolset (Solo Windows)

**WiX** es necesario para generar el instalador .exe en Windows.

1. **Descarga** desde [https://wixtoolset.org/releases/](https://wixtoolset.org/releases/)
   - Versión: 3.14+ (recomendado 3.14.4)
   - Sistema: Windows

2. **Instala** (Next > Next > Finish)

3. **Verifica** que se instaló:
   ```bash
   heat.exe --version
   # Deberías ver: WiX Toolset Version 3.14...
   ```

Si ves el error "no se reconoce el comando", reinicia PowerShell/CMD.

---

## Paso 2: Configurar pom.xml para jpackage

Abre `pom.xml` y añade el plugin de jpackage en la sección `<build>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jpackage-plugin</artifactId>
    <version>0.1.4</version>
    <configuration>
        <mainClass>com.rujiman.mediatracker.Main</mainClass>
        <name>MediaVerse</name>
        <version>1.0.0</version>
        <icon>src/main/resources/com/rujiman/mediatracker/views/icons/mediaverse_logo.ico</icon>
        <vendor>Rujiman</vendor>
        <description>Tu universo de entretenimiento en una sola app</description>
        
        <!-- Windows específico -->
        <winInstallDir>MediaVerse</winInstallDir>
        <winConsole>false</winConsole>
        <winMenu>true</winMenu>
        <winShortcut>true</winShortcut>
        
        <!-- Java integrado (no requiere Java en el usuario) -->
        <javaOptions>
            <option>-Xmx1024m</option>
            <option>--add-opens java.base/java.lang=ALL-UNNAMED</option>
        </javaOptions>
    </configuration>
</plugin>
```

**Importante**: 
- `mainClass` debe ser `com.rujiman.mediatracker.Main`
- `icon` debe ser ruta a tu icono en formato `.ico`

---

## Paso 3: Convertir Logo PNG a ICO (Opcional)

Si tienes `mediaverse_logo.png` pero necesitas `.ico`:

### Online (Fácil)
1. Abre [https://convertio.co/png-ico/](https://convertio.co/png-ico/)
2. Sube `mediaverse_logo.png`
3. Descarga el `.ico`
4. Copia a `src/main/resources/com/rujiman/mediatracker/views/icons/mediaverse_logo.ico`

### Con ImageMagick (Línea de Comandos)
```bash
convert mediaverse_logo.png -define icon:auto-resize=256,128,96,64,48,32,16 mediaverse_logo.ico
```

---

## Paso 4: Compilar el Proyecto

```bash
cd tu-carpeta-mediaverse
mvn clean package
```

Esto genera un **JAR ejecutable** con todas las dependencias empaquetadas.

Espera 5-10 minutos (Maven descarga dependencias).

**Resultado**: `target/mediaverse-1.0-SNAPSHOT.jar`

---

## Paso 5: Generar el .exe

### Opción A: Con jpackage Maven Plugin (Recomendado)

```bash
mvn jpackage:jpackage
```

**Salida**: `target/dist/MediaVerse-1.0.0.exe`

El instalador estará listo en `target/dist/`.

### Opción B: Con jpackage Directo (Más Control)

```bash
# Busca tu JAVA_HOME
java -XshowSettings:properties -version 2>&1 | grep "java.home"

# Genera el .exe
jpackage --input target \
    --name MediaVerse \
    --main-jar mediaverse-1.0-SNAPSHOT.jar \
    --main-class com.rujiman.mediatracker.Main \
    --version 1.0.0 \
    --vendor Rujiman \
    --description "Tu universo de entretenimiento" \
    --icon src/main/resources/com/rujiman/mediatracker/views/icons/mediaverse_logo.ico \
    --app-version 1.0.0 \
    --type exe
```

---

## Paso 6: Prueba el .exe

1. **Busca** el instalador en `target/dist/MediaVerse-1.0.0.exe` (o similar)

2. **Doble clic** para instalar

3. **Sigue el wizard** (Siguiente > Siguiente > Instalar)

4. **Abre la app** desde el escritorio o menú inicio

5. **Verifica**:
   - La app inicia sin errores
   - No pide Java instalado
   - Las búsquedas funcionan (si tienes `.env` con API keys)

---

## Paso 7: Crear Release en GitHub

1. **Sube el .exe a GitHub**:
   - Ve a tu repo
   - **Releases** → **Create a new release**
   - Tag: `v1.0.0`
   - Title: `MediaVerse 1.0.0`
   - Descripción:
     ```markdown
     # MediaVerse 1.0.0

     ## 📦 Descargar

     - **MediaVerse-1.0.0.exe** — Instalador para Windows (recomendado)
     - **mediaverse-1.0-SNAPSHOT.jar** — JAR ejecutable (requiere Java 21)

     ## 🚀 Instalación

     1. Descarga el `.exe`
     2. Doble clic para instalar
     3. **No necesitas Java** — viene incluido
     4. Configura `.env` con tus API keys (ver SETUP.md)

     ## 📝 Cambios

     - Serie v1.0 de MediaVerse
     - Soporte para Series, Películas, Anime, Juegos, Música
     - Sistema de favoritos y carpetas
     - Dashboard personalizado
     ```

   - **Attach files**: Sube el `.exe`
   - **Publish release**

---

## Tamaño Final del .exe

- **Sin optimización**: 300-400 MB
- **Con optimizaciones**: 250-300 MB

El tamaño incluye:
- Java 21 Runtime: ~180 MB
- JavaFX: ~60 MB
- Dependencias Maven: ~15 MB
- Tu código: ~5 MB

---

## Optimizar el Tamaño (Opcional)

Si el .exe es demasiado grande:

### 1. Usar jlink para crear JRE custom

```bash
jlink --module-path $JAVA_HOME/jmods \
    --add-modules java.base,java.logging,java.desktop,java.net.http \
    --output custom-jre \
    --compress 2
```

### 2. Usar ese JRE en jpackage

```bash
jpackage --runtime-image custom-jre \
    --name MediaVerse \
    ...
```

**Reducción**: ~50-100 MB menos de tamaño.

---

## Troubleshooting .exe

### ❌ "jpackage not found"
→ Java 21 no está en PATH
```bash
java -version  # Debe mostrar 21.x
```

### ❌ "WiX Toolset not found"
→ Reinicia PowerShell/CMD tras instalar WiX

### ❌ El .exe se abre pero falla
→ Comprueba que tienes `.env` con API keys válidas

### ❌ "Icon not found"
→ La ruta en `pom.xml` no existe
→ Verifica ruta: `src/main/resources/com/rujiman/mediatracker/views/icons/mediaverse_logo.ico`

### ❌ El .exe no inicia (consola abre y cierra)
→ Probablemente falta una librería en el empaquetado
→ Abre consola en la carpeta de instalación y ejecuta:
```bash
MediaVerse.exe --version
```
Para ver el error exacto.

---

## Redistribuir el .exe

Una vez tengas el `.exe`:

1. **Sube a GitHub Releases** (recomendado)
2. **Crea un ZIP** con el `.exe` y un `README.txt`:
   ```
   MediaVerse 1.0.0
   
   1. Doble clic en MediaVerse-1.0.0.exe
   2. Sigue el instalador
   3. Abre MediaVerse desde el escritorio
   4. Configura API keys (mira SETUP.md)
   ```
3. **Distribúyelo** donde quieras

Los usuarios NO necesitarán Java — está incluido en el `.exe`.

---

## Versiones Futuras

Cuando saques v1.1, v1.2, etc.:

1. Cambia versión en `pom.xml`: `<version>1.1.0</version>`
2. Corre: `mvn clean package jpackage:jpackage`
3. Nuevo `.exe` se genera en `target/dist/`
4. Sube a GitHub Releases como v1.1.0

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Trabajo Final de Grado (TFG) en el ciclo DAM — 2025
