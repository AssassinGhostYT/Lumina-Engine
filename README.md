<div align="center">

<img src="./assets/icon.png" width="96" height="96" alt="Icono de Lumina Engine" />

# ⭐ Lumina Engine

**Un estudio completo de juegos 2D que vive en tu teléfono Android.**
Bloques visuales, editor de código real, asistente de IA y sincronización con GitHub, impulsado por LibGDX + Box2D.

<br>

<a href="https://github.com/AssassinGhostYT/Lumina-Engine/releases/latest">
  <img src="https://img.shields.io/badge/⬇_Descargar_el_APK-Releases-2ea44f?style=for-the-badge" alt="Descargar desde Releases" />
</a>

<br><br>

[![Construir APK](https://github.com/AssassinGhostYT/Lumina-Engine/actions/workflows/android-build.yml/badge.svg)](https://github.com/AssassinGhostYT/Lumina-Engine/actions/workflows/android-build.yml)
![Licencia](https://img.shields.io/badge/licencia-Apache--2.0-blue)
![Plataforma](https://img.shields.io/badge/plataforma-Android-3DDC84?logo=android&logoColor=white)
![Motor](https://img.shields.io/badge/LibGDX-Box2D-red)
![Versión](https://img.shields.io/github/v/release/AssassinGhostYT/Lumina-Engine?include_prereleases)
![Estrellas](https://img.shields.io/github/stars/AssassinGhostYT/Lumina-Engine?style=social)

</div>

---

## 📥 ¿Dónde descargo la aplicación?

> **Opción 1 — descarga directa:** **[LuminaEngine-1.0.1.apk](https://github.com/AssassinGhostYT/Lumina-Engine/releases/download/v1.0.1/LuminaEngine-1.0.1.apk)** (97 MB, firmado para pruebas e instalable en cualquier teléfono).
>
> **Opción 2 — página de versiones:** abre la [última versión](https://github.com/AssassinGhostYT/Lumina-Engine/releases/latest) y descarga el `.apk` desde **Assets**.
>
> **Opción 3 — GitHub lo construye:** cada push a `main` ejecuta **Actions → Build APK**. Abre la ejecución más reciente en verde, baja hasta **Artifacts** y descarga **LuminaEngine-apk**.
>
> En Android tendrás que permitir la instalación desde el navegador o administrador de archivos la primera vez.
>
> **No necesitas una computadora** para probar el motor. El código de esta página es para desarrolladores que quieran leerlo, construirlo o contribuir.

**Checksum SHA-256:** `1f2aa901fdcc6b0b786360e845bfc157483226fef7411a3a4d72178e771bf977`

| Quiero... | Ir a |
|---|---|
| Instalar y usar el motor | **[Último APK de CI](https://github.com/AssassinGhostYT/Lumina-Engine/actions/workflows/android-build.yml)** o [Versiones](https://github.com/AssassinGhostYT/Lumina-Engine/releases/latest) |
| Construirlo yo mismo | [Construir desde el código](#-construir-desde-el-código) |
| Usar mi propia IA / OpenCode | [Guía de IA](docs/AI.md) |
| Corregir un error o añadir una función | [Contribuir](#-contribuir) |

---

## ✨ ¿Qué es Lumina Engine?

Lumina Engine es un **motor y editor de juegos 2D de código abierto que funciona completamente en un teléfono**.
Diseñas escenas, conectas la jugabilidad con bloques, Java o C++, ejecutas el juego y exportas un APK, todo sin computadora.

Internamente usa **LibGDX** para los gráficos y **Box2D** para la física, con un editor Android nativo.

## 🚀 Funciones principales

- **Editor:** escenas 2D/3D, elementos, lista de cuerpos, administrador de archivos, deshacer/rehacer y diseños verticales y horizontales.
- **Programación visual:** bloques basados en eventos, bloques anidados, búsqueda y bloques personalizados.
- **Editor de código:** Java y C++ con resaltado de sintaxis, autocompletado, panel de errores y archivos JSON.
- **Física:** cuerpos Box2D, uniones, sensores, colisiones personalizadas y luces.
- **Comportamientos:** 56 integrados y configurables mediante variables de script.
- **Herramientas de interfaz:** creador de interfaces, creador de botones, temas y contenedores de interfaz.
- **Recursos:** navegador de 1.831 iconos, paquetes gratuitos, editor de animaciones y fuentes.
- **Estructura:** grupos, prefabs, varias escenas y ordenamiento en eje Y.
- **IA:** asistente integrado y conector remoto para Claude u OpenCode. Consulta [docs/AI.md](docs/AI.md).
- **Git:** clonar, hacer commits, subir cambios y añadir workflows de GitHub Actions a tu juego.
- **Exportación:** crear APK, AAB y ZIP desde el teléfono; exportación experimental para escritorio.
- **Idiomas:** español, inglés, francés, portugués y ruso.

## 🧩 Estructura del proyecto

| Ruta | Qué contiene |
|---|---|
| `app/` | Editor Android, es el APK que instalas |
| `template/` | Motor usado por los juegos exportados ([docs/TEMPLATE.md](docs/TEMPLATE.md)) |
| `desktop/` | Motor experimental para escritorio |
| `formatter/` | Formateador del editor de código |
| `server/` | Servidor del conector de IA/remoto |
| `tools/` | Scripts auxiliares de construcción, plantillas e idiomas |
| `docs/` | Documentación del proyecto |

## 🛠 Construir desde el código

Requisitos: **JDK 21** y el **SDK de Android**.

```bash
git clone https://github.com/AssassinGhostYT/Lumina-Engine.git
cd Lumina-Engine
chmod +x gradlew
./gradlew :app:assembleDebug
```

Esta compilación de prueba se instala en cualquier teléfono. Para una compilación release necesitas tu propio keystore y ejecutar `./gradlew :app:assembleRelease`.

## 🤖 Compilaciones automáticas

El workflow **Build APK** se ejecuta en cada push a `main`, en pull requests y manualmente:

1. Abre **Actions → Build APK**.
2. Pulsa la ejecución más reciente.
3. Descarga **LuminaEngine-apk** desde **Artifacts**.

El workflow **Aab & Apk Build** genera el APK y el AAB release y puede publicar el APK en GitHub Releases.

## 🤝 Contribuir

Todas las contribuciones son bienvenidas: corregir errores, mejorar el rendimiento, añadir comportamientos, estilos, ejemplos, traducciones, documentación y tutoriales.

## 💖 Apoyar el proyecto

Lumina Engine es gratuito y de código abierto. Marca el repositorio con una estrella ⭐, compártelo o abre un issue para reportar errores y sugerir funciones.

## 🌐 Comunidad

[Repositorio de GitHub](https://github.com/AssassinGhostYT/Lumina-Engine) · [Issues](https://github.com/AssassinGhostYT/Lumina-Engine/issues) · [Versiones](https://github.com/AssassinGhostYT/Lumina-Engine/releases/latest) · [Compilaciones](https://github.com/AssassinGhostYT/Lumina-Engine/actions/workflows/android-build.yml)

> Puedes usar y modificar este proyecto bajo la licencia Apache-2.0. Consulta [LICENSE](LICENSE) y [assets/note.txt](assets/note.txt).

> Todavía pueden existir errores. Agradecemos mucho los reportes y las correcciones. 🚒
