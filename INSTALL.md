# 💾 Instalación — MediaVerse

---

## Opción 1: Portable (Recomendado para Windows)

Esta es la forma más sencilla. No hay que instalar nada.

### 1. Descarga el zip

Descarga **`MediaVerse-portable.zip`** desde [Releases](https://github.com/rujiman/MediaVerse/releases).

### 2. Descomprime

Descomprime el zip donde quieras: Escritorio, Documentos, un USB...

La estructura que verás dentro:

```
MediaVerse/
├── MediaVerse.exe       ← el ejecutable
├── app/
│   ├── MediaVerse.jar
│   └── .env             ← aquí va tu fichero .env
└── runtime/             ← Java incluido, no tocar
```

### 3. Configura tus claves de API

1. Dentro de la carpeta descomprimida, entra en `app\`
2. Crea un fichero llamado `.env` (copia el `.env.example` que hay en la raíz del zip)
3. Ábrelo con el Bloc de Notas y rellena tus claves (ver [SETUP.md](./SETUP.md))
4. Guarda el fichero

### 4. Ejecuta

Doble clic en `MediaVerse.exe`. Listo.

> **No necesitas Java instalado.** El runtime de Java 25 va incluido dentro del zip en la carpeta `runtime\`.

---

## Opción 2: Desde código fuente (para desarrolladores)

### Requisitos

- Java 25
- Maven 3.8+
- Git

### Pasos

```bash
git clone https://github.com/rujiman/MediaVerse.git
cd MediaVerse
cp .env.example .env
```

Rellena `.env` con tus claves (ver [SETUP.md](./SETUP.md)) y ejecuta:

```bash
mvn javafx:run
```

---

## Dónde se guardan tus datos

Independientemente de cómo ejecutes la app, los datos siempre se guardan en:

```
C:\Users\<tu_usuario>\Documents\MediaVerse\userdata\
```

Esto significa que:
- Puedes mover o reinstalar el zip sin perder tus datos
- Puedes hacer backup copiando esa carpeta
- Puedes sincronizarla con Google Drive o OneDrive

---

## Problemas comunes

| Problema | Solución |
|----------|----------|
| La app no abre | Comprueba que el `.env` está en `app\` con las claves rellenas |
| "TMDB_API_KEY no configurada" | Falta crear o rellenar el `.env` (ver [SETUP.md](./SETUP.md)) |
| Las búsquedas no funcionan | Verifica que tienes conexión a Internet y que las claves son válidas |
| La app es lenta al arrancar | Normal la primera vez; las siguientes arranca más rápido |
