# Política de privacidad de Lumina Engine

**Última actualización: 11 de noviembre de 2026**

**Responsable:** AssassinGhostYT, desarrollador de Lumina Engine.

Lumina Engine («la app») es una herramienta para crear videojuegos 2D y 3D en el dispositivo. Esta política explica qué información maneja la app, para qué se usan los permisos de Android y cómo se relaciona con servicios opcionales de terceros.

## 1. Información guardada en tu dispositivo

Los proyectos, escenas, código, imágenes, sonidos, modelos, respaldos y preferencias se guardan localmente en el almacenamiento de la app o en la ubicación que elijas al exportar. Las claves de proveedores de IA y los tokens de GitHub también se guardan localmente.

Lumina Engine no mantiene una cuenta central ni un servidor propio donde se suban tus proyectos. No vendemos datos personales. Si exportas, respaldas o sincronizas un proyecto, tú eliges el destino y mantienes el control sobre esos archivos.

## 2. Permisos de Android y su finalidad

La app solicita únicamente permisos necesarios para sus funciones:

| Permiso | Para qué se usa |
|---|---|
| `INTERNET` y acceso a red | Sincronización opcional con GitHub, proveedores de IA, anuncios, comprobación de actualizaciones y conexión remota opcional. |
| `com.android.vending.BILLING` | Donaciones opcionales mediante Google Play Billing. |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `WRITE_MEDIA_STORAGE` y `ACCESS_MEDIA_LOCATION` | Importar y exportar imágenes, proyectos, respaldos y APK cuando tú lo solicitas. |
| `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_DATA_SYNC` | Mantener activas operaciones largas de compilación, exportación o sincronización. |
| `REQUEST_INSTALL_PACKAGES` | Instalar un APK que hayas exportado desde la app. |
| Identificador de publicidad (`AD_ID`) | Añadido por el SDK de anuncios de Google para mostrar y medir anuncios. |

La app **no** solicita acceso a ubicación precisa, contactos, cámara, micrófono, SMS ni registros de llamadas.

## 3. Publicidad (Google AdMob)

La pantalla de inicio puede mostrar un banner pequeño de Google AdMob. Puedes ocultarlo con el botón «Ocultar» o desactivarlo definitivamente desde **Ajustes → Mostrar anuncios**.

Google Mobile Ads puede recopilar y procesar datos como el identificador de publicidad, la dirección IP, la ubicación aproximada derivada de la IP, información del dispositivo y de la app, interacciones con los anuncios y datos de diagnóstico. Estos datos se usan para mostrar anuncios, limitar su frecuencia, medirlos y prevenir fraudes.

- Política de privacidad de Google: <https://policies.google.com/privacy>
- Cómo usa Google los datos en publicidad: <https://policies.google.com/technologies/ads>

Cuando desactivas los anuncios en Ajustes, la app deja de solicitar anuncios nuevos. Los datos que Google ya hubiera recopilado se rigen por su propia política. Puedes restablecer o eliminar tu identificador de publicidad desde los ajustes de Android.

## 4. Servicios opcionales de terceros

Estas funciones solo se activan si decides usarlas:

- **GitHub:** la app usa el flujo de autorización de GitHub para acceder únicamente a la cuenta y los repositorios que autorices. El token se guarda en el dispositivo. Sujeto a la política de GitHub: <https://docs.github.com/site-policy/privacy-policies/github-privacy-statement>.
- **Proveedores de IA:** si configuras Google Gemini, OpenRouter, OpenCode, Pollinations u otro endpoint compatible, el mensaje y el contexto que elijas enviar se transmiten al proveedor seleccionado para generar la respuesta. Las claves se guardan localmente. Cada proveedor aplica su propia política de privacidad.
- **Lumina Remote (opcional):** permite conectar una terminal u otro dispositivo para controlar la app. La sesión pasa por el relay `https://lumina.brayanhernandez385.workers.dev` y solo se usa si activas esta función.
- **Compras y donaciones:** las donaciones se procesan mediante Google Play Billing. No recibimos ni almacenamos datos de tarjetas ni de pago; Google gestiona la transacción.
- **Actualizaciones y descargas:** la app consulta un archivo de versión en GitHub para avisar de actualizaciones y usa GitHub para distribuir APK. No se envía información personal en esa consulta.

Estos proveedores pueden procesar datos en países distintos al tuyo, conforme a sus propias políticas y garantías legales.

## 5. Datos que no recopilamos

No creamos perfiles centrales de usuarios, no vendemos datos personales, no mostramos anuncios fuera de Google AdMob y no accedemos a ubicación precisa, contactos, cámara, micrófono, SMS ni llamadas.

## 6. Conservación y eliminación

La información local permanece en tu dispositivo hasta que borres los proyectos, borres los datos de la app o la desinstales. Los respaldos, ZIP y APK exportados quedan bajo tu control. Los datos enviados a GitHub, a tu proveedor de IA o a Google se conservan según las políticas de esos servicios.

## 7. Seguridad

Las claves y tokens se guardan en el almacenamiento privado de la app y las comunicaciones con servicios externos usan HTTPS. Ningún método de almacenamiento o transmisión es completamente seguro, por lo que debes proteger el acceso a tu dispositivo.

## 8. Menores

Lumina Engine no está dirigido a menores de 13 años ni se diseña para recopilar deliberadamente información personal de menores. Si un padre, madre o tutor cree que un menor ha proporcionado datos personales, puede contactarnos para solicitar su eliminación.

## 9. Tus derechos

Según tu país, puedes tener derecho a acceder, corregir, eliminar u oponerte al tratamiento de tus datos. La mayor parte de la información permanece en tu dispositivo y puedes eliminarla directamente. Para datos tratados por Google, GitHub o un proveedor de IA, ejerce tus derechos ante ese proveedor. Para consultas sobre la app, usa el contacto de la sección 11.

## 10. Cambios en esta política

Podemos actualizar esta política cuando cambien las funciones de la app o los servicios utilizados. Publicaremos la versión vigente en esta misma página con su fecha de actualización. Los cambios importantes se comunicarán en la app o en el repositorio oficial.

## 11. Contacto

Para preguntas o solicitudes relacionadas con privacidad:

- Repositorio oficial: <https://github.com/AssassinGhostYT/Lumina-Engine>
- Issues: <https://github.com/AssassinGhostYT/Lumina-Engine/issues>
- También puedes usar la dirección de contacto del desarrollador publicada en Google Play.
