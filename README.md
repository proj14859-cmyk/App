# GD Recorder — prototipo para Android 12

Grabador nativo Java para probar en POCO X3 NFC. Sin anuncios, Internet ni marcas de agua.

## Descargar e instalar desde el teléfono

1. En este repositorio abre **Actions → Generar APK**.
2. Abre la ejecución más reciente. Espera a que termine en verde.
3. Abajo, en **Artifacts**, descarga **GDRecorder-APK** (requiere iniciar sesión en GitHub).
4. Descomprime el ZIP y abre `app-debug.apk`. Permite instalar desde el navegador o gestor de archivos si Android lo solicita.
5. Abre la app. Concede el permiso de audio y confirma la captura de pantalla de Android.

Para volver a compilar: **Actions → Generar APK → Run workflow → Run workflow**. En móvil puedes necesitar la vista de escritorio del navegador.

## Primera prueba recomendada

Selecciona 1080p, 60 fps, 24 Mbps y audio interno. Abre Geometry Dash en horizontal, vuelve a la grabadora, inicia y regresa al juego. Graba 20–30 segundos y detén desde la notificación. Busca el MP4 en **Movies/GDRecorder**.

La orientación se toma al iniciar; no gires durante la grabación. Si tu móvil vuelve a vertical al entrar en la app, activa el giro automático y mantén el teléfono horizontal. La grabación incluye cualquier pantalla o notificación que aparezca.

## Funciones y límites

- AVC/H.264 por hardware y MP4. Salida horizontal de 1920 × 1080 o 1280 × 720. Puede haber franjas para conservar la proporción de la pantalla. La grabadora se abre en horizontal.
- Selección de 30, 60 o 120 fps. Se comprueba si el codificador declara soporte para el tamaño y velocidad. En modo normal se rechazan modos no declarados. El interruptor experimental permite intentar velocidades no declaradas, manteniendo la comprobación del tamaño: configurar 120 no implica obtenerlos.
- La comprobación **no garantiza 120 fotogramas distintos por segundo**. Android puede entregar menos y la carga o temperatura pueden reducir el rendimiento. No se ha medido todavía en un POCO X3 NFC.
- Audio interno estéreo AAC a 48 kHz y 192 kbps. Se requiere permiso RECORD_AUDIO, aunque no se usa el micrófono. Si el juego impide la captura, puede quedar silencio.
- Notificación permanente con botón Detener y archivos visibles en la galería.
- Sin micrófono, controles flotantes ni pausa. Al terminar, la app muestra el promedio de fotogramas codificados calculado con las marcas de tiempo de video. No mide los FPS del juego ni detecta fotogramas duplicados.
- Bitrates de 8 a 64 Mbps. Se limitan al rango declarado del codificador; el resumen muestra el bitrate configurado, no el bitrate medio real del archivo.
- No hay servicio de subida de videos ni permiso de Internet.
- El APK de Actions es una compilación de prueba firmada con una clave debug efímera. Para actualizar entre compilaciones puede hacer falta desinstalar antes. Se debe configurar una clave persistente antes de distribuir versiones estables.

## Desarrollo

JDK 17, Gradle 8.9 y Android SDK 35. Ejecuta `gradle assembleDebug lintDebug`. El workflow instala las herramientas. No se incluye Gradle Wrapper; usa la versión de Gradle indicada.

La app apunta a Android 12L (API 32) y requiere Android 10+. Está preparada para pruebas personales en Android 12; no para publicación en Google Play. El APK debe probarse en el dispositivo para verificar sincronización, estabilidad y rendimiento del juego.
