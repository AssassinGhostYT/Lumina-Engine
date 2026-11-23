## Nebulon Engine 1.0.7

### Nueva marca

- Nuevo logo **N** en el icono de la app, la pantalla de carga y la cabecera del editor.
- La app se llama **Nebulon Engine** en todos los textos visibles.
- Eliminados los restos de la marca Lumina de la tienda y de los textos de publicación.
- El paquete sigue siendo `com.lumina.engine` para no perder la ficha de Play Store.
- Correcciones y traducciones.

### Versión

- Versión Android `1.0.7`, código `107`.

## Nebulon Engine 1.0.6

### Nombre

- La app pasa a llamarse **Nebulon Engine**. El paquete (`com.lumina.engine`) y los proyectos existentes no cambian.

### Modelos 3D

- Importación de **FBX / OBJ / BLEND** desde el panel 3D, el explorador de archivos y la nube.
- Conversión automática a **GLB** con Assimp, conservando huesos, skins y animaciones.
- Los juegos exportados incluyen los GLB convertidos y no necesitan la librería nativa.

### Sonidos de animación

- `Animation Sound` y `Animation Sounds` para asociar sonidos a clips.
- Volumen, distancia y reproducción en cada bucle.

### C# en el móvil

- Editor con resaltado, autocompletado y plantilla `Nebulon`.
- **Mono + Roslyn** incluidos: abre un archivo `.cs`, pulsa el botón verde y el proyecto se compila y ejecuta en el teléfono.
- Errores del compilador con archivo, línea y columna; `Engine.Log` escribe en la consola del editor.

### Otros

- Correcciones y traducciones.
- Versión Android `1.0.6`, código `106`.

## 🎉 Nebulon Engine 1.0.1 — 2D y 3D

### Motor 3D

- Escenas 3D junto a escenas 2D.
- Cápsula de personaje `Player` creada automáticamente en escenas 3D nuevas.
- Objetos, primitivas y modelos GLB/glTF.
- Luces puntuales, cónicas y direccionales, sombras, cielo, niebla, agua, terreno y efectos.
- Física Bullet con colisiones de caja, esfera, cápsula y malla, activadores, rayos y vehículos.
- Comportamientos y bloques para controles en primera y tercera persona.

### Editor

- Vista 3D con cámara orbital y herramientas de mover, rotar y escalar.
- Ejes rojo, verde y azul, cuadrícula y plataforma de referencia.
- Editor de colisiones personalizadas.
- Editor de código oscuro con Java, C++, JSON y autocompletado C++.
- Escenas 2D nuevas con un `Player` físico inicial.
- Diseño visual Lumina con fondo espacial y color lima.

### Exportación y GitHub

- Exportación de proyectos completos en ZIP.
- Archivos `.cpp`, `.h`, `.hpp` y configuración CMake incluidos en el ZIP.
- GitHub Actions genera APK y AAB release.
- El APK puede publicarse como asset de GitHub Releases.
- Versión Android actualizada a `1.0.1`, código `101`.

### Idioma

- README, notas de versión y textos visibles actualizados al español.
- Español como idioma predeterminado para la interfaz y las respuestas de IA.
