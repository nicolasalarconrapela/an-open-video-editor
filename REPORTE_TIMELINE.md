# Reporte de posibles fallos del timeline

## Observaciones principales

1. **Zoom del timeline no se aplica al estado del editor (corregido)**
   - Corregido: se unificó el `VideoEditorViewModel` para evitar instancias duplicadas en los controles, el diálogo de exportación y los paneles del editor.
   - Archivos implicados:
     - `VideoEditorScreen.kt` (ahora recibe el ViewModel compartido y lo propaga a subcomponentes).

2. **Tracks del timeline son datos estáticos (placeholders)**
   - Los `TimelineUiTrack` se inicializan con clips fijos (Intro/Entrevista/B-roll/Outro) y no reflejan el proyecto actual ni sus ediciones. Esto genera una desconexión entre el editor y el timeline real.
   - Archivos implicados:
     - `VideoEditorScreen.kt` (creación de `timelineTracks` con datos hardcodeados).

3. **Recorte (trim) sin implementación funcional**
   - `TimelinePrecisionView` recibe un callback `onTrim`, pero en `BottomControls` se pasa un lambda vacío y en la UI no se exponen controles que disparen el recorte. El flujo de recorte del timeline no está conectado.
   - Archivos implicados:
     - `VideoEditorScreen.kt` (callback vacío para `onTrim`).
     - `TimelinePrecisionView.kt` (no hay uso de `TrimHandles` ni activación real de `onTrim`).

4. **Controles de audio y loops ocultos sin acceso**
   - En `TimelinePrecisionView` hay estados `showControls` y `showAudio` inicializados a `false` y nunca se actualizan. El usuario no puede ver ni activar esos controles.
   - Archivos implicados:
     - `TimelinePrecisionView.kt`.

5. **Arrastre de clips en `TimelineView` sin implementación**
   - Existen estados para `draggingClipId`/`dragOffsetPx` y un callback `onClipMoved`, pero no hay lógica de drag-and-drop visible ni consumo del callback. La funcionalidad de mover clips no está conectada.
   - Archivos implicados:
     - `TimelineView.kt`.
