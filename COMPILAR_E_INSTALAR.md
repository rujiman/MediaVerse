# 🔨 Compilar e instalar — Guía para desarrolladores

Esta guía explica cómo compilar MediaVerse desde el código fuente y generar el portable `.zip` para distribución.

---

## Requisitos

- **Java 25** (JDK completo) — [Descarga OpenJDK 25](https://jdk.java.net/25/)
- **Maven 3.8+** — [Descarga Maven](https://maven.apache.org/download.cgi)
- **Git** — [Descarga Git](https://git-scm.com/)
- **JavaFX jmods 25** — ver paso 3

---

## Paso 1: Clonar el repositorio

```bash
git clone https://github.com/rujiman/MediaVerse.git
cd MediaVerse
```

---

## Paso 2: Configurar claves de API

```bash
cp .env.example .env
```

Abre `.env` y rellena las claves de TMDB e IGDB. Ver [SETUP.md](./SETUP.md) para obtenerlas.

---

## Paso 3: Ejecutar en modo desarrollo

```bash
mvn javafx:run
```

Maven descarga todas las dependencias automáticamente la primera vez (puede tardar unos minutos). Cuando termine, se abre la app directamente.

---

## Paso 4: Generar el portable para distribución

Para generar el zip que los usuarios pueden descargar y ejecutar sin instalar nada, sigue los pasos de [BUILD_EXE.md](./BUILD_EXE.md).

En resumen:
1. Descargar JavaFX jmods 25
2. `mvn clean package` para compilar el fat jar
3. `jpackage` para generar el portable con Java embebido
4. Comprimir en zip y distribuir

---

## Estructura del proyecto

```
MediaVerse/
├── src/main/java/com/rujiman/mediatracker/
│   ├── controllers/     Controladores JavaFX
│   ├── models/          Modelos de datos
│   ├── services/        Lógica de negocio y acceso a datos
│   └── util/            Utilidades (ConfigLoader, etc.)
├── src/main/resources/  FXML, CSS, imágenes
├── .env.example         Plantilla de configuración
├── pom.xml              Dependencias Maven
└── mediaverse_logo.ico  Icono de la app
```

---

## Versiones futuras

Para sacar una nueva versión:

1. Actualiza `<version>` en `pom.xml`
2. Añade las novedades en `CHANGELOG.md`
3. Ejecuta `mvn clean package` y genera el nuevo portable con `jpackage`
4. Crea un nuevo Release en GitHub y sube el zip

---

## Contribuciones

Este es un Trabajo Final de Grado (TFG). No se aceptan contribuciones externas de momento, pero las sugerencias son bienvenidas vía issues.

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)
TFG DAM — 2025
