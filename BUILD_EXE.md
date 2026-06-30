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
  --type app-image `
  --icon mediaverse_logo.ico `
  --module-path "C:\javafx\javafx-jmods-25" `
  --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.media,java.base,java.desktop,java.sql,java.naming,java.management,java.security.jgss `
  --win-console `
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

## Notas importantes

- El `runtime\` pesa varios cientos de MB (Java 25 + JavaFX embebidos). Es normal.
- El parámetro `--win-console` hace que el exe vuelque errores en la terminal, útil para depurar. Para distribución final puedes quitarlo si no quieres que aparezca una ventana de consola.
- Si cambias código, repite los pasos 2, 3 y 4. El paso 1 solo hay que hacerlo una vez.
- El `.env` del usuario nunca va dentro del zip que distribuyes — cada uno pone el suyo propio con sus claves.
