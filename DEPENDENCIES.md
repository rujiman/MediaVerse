# 📦 Dependencias — MediaVerse

Esta es la lista completa de librerías que usa MediaVerse y cómo se incluyen automáticamente.

## ¿Cómo Se Incluyen las Librerías?

Cuando descargas el **.exe instalador**, **todas las librerías vienen incluidas**. No necesitas descargar nada extra.

El instalador incluye:
- ✅ Java 21 (integrado)
- ✅ JavaFX 25 (UI framework)
- ✅ Todas las librerías de Maven (Gson, OkHttp, SQLite, etc.)

**Total tamaño del instalador**: ~300-400 MB (incluye todo)

---

## Librerías del Proyecto (pom.xml)

| Librería | Versión | Uso | Incluida en .exe |
|----------|---------|-----|-----------------|
| **JavaFX Controls** | 25 | Componentes UI (botones, paneles, etc.) | ✅ Sí |
| **JavaFX FXML** | 25 | Cargar archivos .fxml (layouts) | ✅ Sí |
| **JavaFX Media** | 25 | Reproducción de video/audio | ✅ Sí |
| **Gson** | 2.10.1 | Serializar/deserializar JSON | ✅ Sí |
| **OkHttp** | 4.11.0 | Realizar peticiones HTTP a APIs | ✅ Sí |
| **SQLite JDBC** | 3.44.0 | Base de datos (preparado, no activo) | ✅ Sí |
| **Hibernate** | 6.3.1 | ORM para bases de datos (preparado, no activo) | ✅ Sí |

---

## Desglose por Categoría

### 🎨 Interfaz (JavaFX)
- **JavaFX 25** — Framework moderno para UI en Java
  - Controls: Botones, campos de texto, combobox, etc.
  - FXML: Formato XML para definir layouts (como HTML)
  - Media: Reproducción de video

**Por qué**: MediaVerse necesita una interfaz desktop moderna y responsive.

### 📡 Conexión a APIs
- **OkHttp 4.11.0** — Cliente HTTP robusto
  - Envía peticiones GET/POST a TMDB, IGDB, AniList, Deezer
  - Maneja reintentos automáticos
  - Gestiona headers, timeouts, pooling

**Por qué**: Es más eficiente y confiable que la librería HTTP nativa de Java.

### 📄 Formato JSON
- **Gson 2.10.1** — Serialización JSON ↔ objetos Java
  - Convierte respuestas HTTP (JSON) en objetos Java (`MediaItem`, `User`, etc.)
  - Guarda/carga datos locales desde archivos .json
  - Maneja tipos complejos y null values

**Por qué**: Toda comunicación con APIs y persistencia local usa JSON.

### 💾 Base de Datos (Preparado para Futuro)
- **SQLite JDBC 3.44.0** — Driver para base de datos SQLite
- **Hibernate 6.3.1** — ORM (Object-Relational Mapping)

**Estado**: No activos en v1.0 (usamos JSON en lugar de BD)
**Para qué**: Versiones futuras pueden migrar a SQLite para mejor rendimiento

---

## Tamaños Estimados

| Componente | Tamaño |
|-----------|--------|
| Java 21 Runtime | ~180 MB |
| JavaFX 25 | ~60 MB |
| Gson + OkHttp + librerías | ~15 MB |
| Código compilado (JAR) | ~5 MB |
| **Total instalador .exe** | ~260-300 MB |

---

## ¿Qué Necesita el Usuario?

### ✅ CON el .exe instalador
- Nada. Solo descargar e instalar.
- Java está integrado.
- Librerías están integradas.
- **Tamaño en disco tras instalar**: ~600 MB

### ❌ SIN el .exe (compilar desde código)
- Java 21 instalado en su ordenador
- Maven instalado
- Las librerías se descargan automáticamente de Maven Central

---

## Maven Central Repository

Si alguien quiere compilar el proyecto desde cero:

```bash
mvn clean package
```

Maven descarga automáticamente todas las librerías desde [Maven Central](https://central.sonatype.com/) usando las coordenadas del `pom.xml`.

Ejemplo de una dependencia:
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

Maven busca esa librería, la descarga, y la añade al classpath.

---

## Licencias

Todas las librerías tienen licencias permisivas:

| Librería | Licencia |
|----------|----------|
| JavaFX | GPL v2 + Classpath Exception |
| Gson | Apache 2.0 |
| OkHttp | Apache 2.0 |
| SQLite JDBC | Apache 2.0 |
| Hibernate | LGPL 2.1 |

✅ Todas permiten uso comercial y personal.

---

## ¿Cómo Se Empaqueta Todo en el .exe?

El .exe se genera con **jpackage** (herramienta de Java 21):

```bash
mvn clean package jpackage:jpackage
```

El proceso:
1. Maven compila el código Java
2. Descarga todas las dependencias
3. Crea un JAR executável
4. jpackage integra Java 21 + JAR + librerías
5. Genera el instalador .exe (Windows) o .dmg (macOS) o .deb (Linux)

Resultado: Un instalador que no requiere Java pre-instalado en el usuario. Todo viene dentro.

---

## Actualizar Librerías (Para Desarrolladores)

Si quieres actualizar una librería:

1. **pom.xml**: Cambia el número de versión
   ```xml
   <version>2.11.0</version>
   ```

2. **Terminal**:
   ```bash
   mvn clean package
   ```

3. Maven descarga la nueva versión automáticamente

4. **Commit y push** a GitHub

---

## Troubleshooting Dependencias

### "Cannot find symbol" al compilar
→ Maven no descargó las dependencias. Intenta:
```bash
mvn clean install -U
```

### Conflicto de versiones
→ Dos librerías requieren versiones diferentes de otra
→ Solución: Ver `dependency tree` con `mvn dependency:tree` y ajustar

### Librería muy grande
→ Si alguna librería aumenta mucho el tamaño del .exe, considera usar shade plugin para optimizar

---

## 👨‍💻 Autor

**Ruben Jimenez Manzano** (Rujiman)

Trabajo Final de Grado (TFG) en el ciclo DAM — 2025
