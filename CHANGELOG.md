# Changelog

## [1.3.53] - 2026-01-09
### Ajustado
- Workflow con caching ampliado y opciones de Gradle para optimizar recursos y tiempos.

## [1.3.52] - 2026-01-09
### Ajustado
- Pantalla inicial con estado vacío cuando no hay proyectos.

## [1.3.51] - 2026-01-09
### Ajustado
- Nombre del artefacto APK en el workflow con nombre y versión.

## [1.3.50] - 2026-01-09
### Corregido
- Nombre de archivo en proyectos con URI de tipo archivo para evitar mostrar "null".

## [1.3.49] - 2026-01-09
### Añadido
- Opciones para renombrar y eliminar proyectos desde la pantalla principal.

## [1.3.48] - 2026-01-09
### Añadido
- Pantalla inicial en formato de cuadrícula con tarjetas de proyectos.
### Ajustado
- Guardado de proyectos gestionado internamente por la app en almacenamiento privado.

## [1.3.47] - 2026-01-09
### Corregido
- Import necesario para usar `collectAsState` con delegación en la pantalla principal.

## [1.3.46] - 2026-01-09
### Añadido
- Lista de proyectos recientes en la pantalla principal con acceso rápido.
### Ajustado
- Guardado automático de proyectos recientes al abrir o guardar proyectos.

## [1.3.45] - 2026-01-09
### Ajustado
- Dependencia Material Components para resolver el tema Material3 en recursos.

## [1.3.44] - 2026-01-09
### Añadido
- Pantalla de carga con SplashScreen y tema dedicado.
### Ajustado
- Actualización de versionCode y versionName para el APK.

## [1.3.43] - 2026-01-09
### Añadido
- Lectura de versión desde PackageManager para evitar referencias directas a BuildConfig en la pantalla principal.
### Ajustado
- Actualización de versionCode y versionName para el APK.

## [1.3.42] - 2026-01-09
### Añadido
- Workflow de APK debug para la rama codex/2026-01-12/22-00-29/explore-ui-2027-highlights.
### Ajustado
- Actualización de versionCode y versionName para el APK.

## [1.3.41] - 2026-01-09
### Añadido
- Subtítulo de bienvenida y botones con iconos en la pantalla principal.
### Ajustado
- Actualización de versionCode y versionName para el APK.

## [1.3.40] - 2026-01-09
### Añadido
- Versión visible en la pantalla principal al abrir la app.
### Ajustado
- Actualización de versionCode y versionName para el APK.

## [1.3.39] - 2026-01-09
### Ajustado
- Ajustes visuales de playhead, toolbar y botón añadir en la UI mock del editor.

## [1.3.38] - 2026-01-09
### Ajustado
- Sincronización del desplazamiento visual de clips con el estado del timeline mock.

## [1.3.37] - 2026-01-09
### Añadido
- UI mock de editor con timeline superior, playhead centrado y barra inferior.

## [1.3.36] - 2026-01-09
### Corregido
- Variables de miniaturas movidas al scope correcto del TimelineView.

## [1.3.35] - 2026-01-09
### Corregido
- Conexión de TimelineView al repositorio de miniaturas para cargar thumbnails.

## [1.3.34] - 2026-01-09
### Corregido
- Import de LaunchedEffect para la integración de miniaturas en TimelineView.

## [1.3.33] - 2026-01-09
### Añadido
- Workflow de APK debug para la rama de sistema de miniaturas de línea de tiempo.

## [1.3.32] - 2026-01-09
### Añadido
- Option B / Pro timeline thumbnails con cache, scheduler y decode MediaCodec.

## [1.3.31] - 2026-01-09
### Añadido
- Integración de miniaturas en TimelineView con solicitudes por viewport.

## [1.3.30] - 2026-01-09
### Añadido
- Repositorio de miniaturas con pipeline memoria/disco/decode y dedupe en vuelo.

## [1.3.29] - 2026-01-09
### Añadido
- Scheduler de miniaturas con deduplicación, cancelación y prioridad por playhead.

## [1.3.28] - 2026-01-09
### Añadido
- Extractor de frames con MediaCodec y conversión YUV->ARGB para miniaturas.

## [1.3.27] - 2026-01-09
### Añadido
- Cache en disco para miniaturas con hash SHA-256 y eviction por antigüedad.

## [1.3.26] - 2026-01-09
### Añadido
- Cache LRU en memoria para miniaturas con límite configurable por bytes.

## [1.3.25] - 2026-01-09
### Añadido
- Modelo ThumbnailKey para claves estables de miniaturas en caches y deduplicación.

## [1.3.24] - 2026-01-09
### Ajustado
- Workflow de APK debug para ejecutar también en la rama de fix de referencias del editor.

## [1.3.23] - 2026-01-09
### Ajustado
- Workflow de APK debug para ejecutar también en la rama work.

## [1.3.22] - 2026-01-09
### Corregido
- Imports faltantes de EditorState y LazyListState para la pantalla del editor.

## [1.3.21] - 2026-01-09
### Corregido
- Estado y datos de timeline disponibles en el scope correcto del editor.

## [1.3.20] - 2026-01-09
### Corregido
- Import de clipToBounds para el PreviewArea.

## [1.3.19] - 2026-01-09
### Ajustado
- Sincronización de reproducción en ViewModel y corrección de currentTime.

## [1.3.18] - 2026-01-09
### Ajustado
- TimelinePrecisionView con scroll animado y playhead centrado.

## [1.3.17] - 2026-01-09
### Añadido
- TimelineBlocksView con bloques semánticos seleccionables.

## [1.3.16] - 2026-01-09
### Añadido
- Selector de modo Bloques/Precisión y uso de TimelinePrecisionView.

## [1.3.15] - 2026-01-09
### Añadido
- TimelinePrecisionView con regla temporal, placeholders y callbacks de interacción.

## [1.3.14] - 2026-01-09
### Ajustado
- Separación de PreviewArea y TimelineArea con insets y clipToBounds.

## [1.3.13] - 2026-01-09
### Ajustado
- Limpieza de estado duplicado en VideoEditorScreen.

## [1.3.12] - 2026-01-09
### Ajustado
- Integración de EditorState en VideoEditorScreen.

## [1.3.11] - 2026-01-09
### Añadido
- Estado del editor con eventos, zoom derivado y sincronización de tiempo.

## [1.3.10] - 2026-01-09
### Ajustado
- Modelos del editor con modo BLOCKS/PRECISION y clips de precisión con placeholders.

## [1.3.9] - 2026-01-09
### Añadido
- Modelos de estado del editor y bloques semánticos para timeline híbrida.

## [1.3.8] - 2026-01-09
### Corregido
- Correcciones de sincronización y regla temporal del timeline para compilar en CI.

## [1.3.7] - 2026-01-09
### Añadido
- Workflow de APK debug para la rama codex/2026-01-11/17-39-50/create-timelineview-composable.

## [1.3.6] - 2026-01-09
### Añadido
- Sincronización del scroll del timeline con la reproducción y zoom por gesto.

## [1.3.5] - 2026-01-09
### Añadido
- Tracks múltiples con regla temporal y colores por tipo de clip en el timeline.

## [1.3.4] - 2026-01-09
### Añadido
- Reordenamiento de clips por arrastre y selección persistente en el timeline.

## [1.3.3] - 2026-01-09
### Añadido
- Selección de clips y etiqueta de tiempo actual en el timeline.

## [1.3.2] - 2026-01-09
### Añadido
- Texto localizado para el control de zoom temporal del timeline.

## [1.3.1] - 2026-01-09
### Añadido
- Vista de timeline con clips dummy, playhead centrado y control de zoom temporal.

## [1.3.0] - 2026-01-09
### Añadido
- Mini previsualización del vídeo en la barra de controles con miniaturas y marcador de progreso.

## [1.2.9] - 2026-01-09
### Añadido
- Generación de proxies de previsualización con FFmpegKit, guardados en los proyectos para edición.
- Ajustes para habilitar proxies y elegir su calidad.

## [1.2.8] - 2026-01-09
### Añadido
- Workflow de APK debug limitado a la rama develop.

## [1.2.7] - 2026-01-09
### Añadido
- Cambio de dependencia FFmpegKit a io.github.maitrungduc1410:ffmpeg-kit-min:6.0.1.

## [1.2.6] - 2026-01-09
### Añadido
- Preparación del Android SDK y aceptación de licencias en el workflow.

## [1.2.5] - 2026-01-09
### Añadido
- Ajuste de DNS en workflow para resolver Maven de FFmpegKit.

## [1.2.4] - 2026-01-09
### Añadido
- Opción manual para reintentar el workflow de APK debug.

## [1.2.3] - 2026-01-09
### Añadido
- Workflow de APK debug en ubuntu-latest.

## [1.2.2] - 2026-01-09
### Añadido
- Workflow de APK debug para todas las ramas usando runner codex.

## [1.2.1] - 2026-01-09
### Añadido
- Repositorio Maven de FFmpegKit para resolver dependencias en CI.

## [1.2.0] - 2026-01-09
### Añadido
- Workflow de GitHub Actions para generar APK debug en la rama develop.

## [1.1.9] - 2026-01-09
### Añadido
- Tests unitarios para exportación y planificación de segmentos.

## [1.1.8] - 2026-01-09
### Añadido
- Configuración de Codespaces con devcontainer.

## [1.1.7] - 2026-01-09
### Añadido
- README ampliado con instalación para desarrolladores.

## [1.1.6] - 2026-01-09
### Añadido
- README ampliado con detalles de instalación.

## [1.1.5] - 2026-01-09
### Añadido
- README ampliado con descripción, construcción y notas de exportación.

## [1.1.4] - 2026-01-09
### Añadido
- Exportación segmentada automática con umbrales configurables para duración/tamaño, reanudación por estado y concatenación final.
