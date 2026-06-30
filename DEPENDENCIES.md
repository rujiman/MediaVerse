# 📦 Dependencias — MediaVerse

---

## Stack técnico

| Componente | Versión | Uso |
|------------|---------|-----|
| Java | 25 | Lenguaje y runtime |
| JavaFX | 25 | Interfaz gráfica (controles, gráficos, media) |
| Gson | 2.10.1 | Serialización JSON para persistencia local |
| OkHttp | 4.11.0 | Cliente HTTP para llamadas a APIs externas |
| SQLite JDBC | 3.44.0 | Incluido como dependencia, no en uso activo en v1.0 |
| Hibernate | 6.3.1 | Incluido como dependencia, no en uso activo en v1.0 |

---

## Lo que incluye el portable

Cuando descargas el zip portable de MediaVerse, ya va todo incluido:

- Runtime de Java 25 (embebido en `runtime\`)
- JavaFX 25 con sus librerías nativas para Windows
- Todas las dependencias Maven (Gson, OkHttp, etc.) dentro del jar

**El usuario no necesita instalar nada.**

---

## Para desarrolladores: compilar desde código

Maven gestiona todas las dependencias automáticamente. Al ejecutar `mvn clean package`, las descarga de Maven Central y las empaqueta dentro del jar final.

```bash
mvn clean package
```

Las dependencias están declaradas en `pom.xml`. Para ver el árbol completo:

```bash
mvn dependency:tree
```

---

## Licencias

| Librería | Licencia |
|----------|----------|
| JavaFX | GPL v2 + Classpath Exception |
| Gson | Apache 2.0 |
| OkHttp | Apache 2.0 |
| SQLite JDBC | Apache 2.0 |
| Hibernate | LGPL 2.1 |

Todas permiten uso personal y educativo sin restricciones.
