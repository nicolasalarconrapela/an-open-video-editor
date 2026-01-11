package io.github.devhyper.openvideoeditor.videoeditor

import androidx.lifecycle.ViewModel
import io.github.devhyper.openvideoeditor.videoeditor.state.EditorMode
import io.github.devhyper.openvideoeditor.videoeditor.state.EditorState
import io.github.devhyper.openvideoeditor.videoeditor.state.TimelineBlock
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VideoEditorViewModel : ViewModel() {
    val transformManager = TransformManager()

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    sealed class EditorEvent {
        data class SelectBlock(val id: String) : EditorEvent()
        data class ToggleMode(val mode: EditorMode) : EditorEvent()
        data class Seek(val ms: Long) : EditorEvent()
        data class Trim(val inMs: Long, val outMs: Long) : EditorEvent()
        data class Split(val atMs: Long) : EditorEvent()
        data class MoveBlock(val from: Int, val to: Int) : EditorEvent()
        data class ZoomChanged(val zoomLevel: Float) : EditorEvent()
        data class ZoomByDelta(val delta: Float) : EditorEvent()
    }

    private val _outputPath = MutableStateFlow("")
    val outputPath: StateFlow<String> = _outputPath.asStateFlow()

    private val _projectOutputPath = MutableStateFlow("")
    val projectOutputPath: StateFlow<String> = _projectOutputPath.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _projectSavingSupported = MutableStateFlow(true)
    val projectSavingSupported: StateFlow<Boolean> = _projectSavingSupported.asStateFlow()

    private val _filterDurationEditorEnabled = MutableStateFlow(false)
    val filterDurationEditorEnabled = _filterDurationEditorEnabled.asStateFlow()

    private val _filterDurationCallback = MutableStateFlow<(LongRange) -> Unit> {}
    val filterDurationCallback: StateFlow<(LongRange) -> Unit> =
        _filterDurationCallback.asStateFlow()

    private val _filterDurationEditorSliderPosition = MutableStateFlow(0f..0f)
    val filterDurationEditorSliderPosition: StateFlow<ClosedFloatingPointRange<Float>> =
        _filterDurationEditorSliderPosition.asStateFlow()

    private val _startFilterSelected = MutableStateFlow(true)
    val startFilterSelected: StateFlow<Boolean> = _startFilterSelected.asStateFlow()

    private val _filterDialogArgs = MutableStateFlow<PersistentList<EffectDialogSetting>>(
        persistentListOf()
    )
    val filterDialogArgs: StateFlow<PersistentList<EffectDialogSetting>> =
        _filterDialogArgs.asStateFlow()

    private val _currentEditingEffect = MutableStateFlow<OnVideoUserEffect?>(null)
    val currentEditingEffect: StateFlow<OnVideoUserEffect?> =
        _currentEditingEffect.asStateFlow()

    private val _currentExportWorkId = MutableStateFlow<String?>(null)
    val currentExportWorkId: StateFlow<String?> = _currentExportWorkId.asStateFlow()

    fun onEvent(event: EditorEvent) {
        _state.update { current ->
            when (event) {
                is EditorEvent.SelectBlock -> current.copy(selectedBlockId = event.id)
                is EditorEvent.ToggleMode -> current.copy(mode = event.mode)
                is EditorEvent.Seek -> current.copy(currentTimeMs = event.ms)
                is EditorEvent.ZoomChanged -> current.copy(zoomLevel = event.zoomLevel)
                is EditorEvent.ZoomByDelta -> {
                    val next = (current.zoomLevel * event.delta).coerceIn(0.5f, 4f)
                    current.copy(zoomLevel = next)
                }
                is EditorEvent.Trim -> trimSelectedBlock(current, event.inMs, event.outMs)
                is EditorEvent.Split -> splitSelectedBlock(current, event.atMs)
                is EditorEvent.MoveBlock -> moveBlock(current, event.from, event.to)
            }
        }
    }

    fun setOutputPath(path: String) {
        _outputPath.update { path }
    }

    fun setProjectOutputPath(path: String) {
        _projectOutputPath.update { path }
    }

    fun setControlsVisible(value: Boolean) {
        _controlsVisible.update { value }
    }

    fun setProjectSavingSupported(value: Boolean) {
        _projectSavingSupported.update { value }
    }

    fun setFilterDurationEditorEnabled(value: Boolean) {
        _filterDurationEditorEnabled.update { value }
    }

    fun setFilterDurationCallback(value: (LongRange) -> Unit) {
        _filterDurationCallback.update { value }
    }

    fun setFilterDurationEditorSliderPosition(value: ClosedFloatingPointRange<Float>) {
        _filterDurationEditorSliderPosition.update { value }
    }

    fun setStartFilterSelected(value: Boolean) {
        _startFilterSelected.update { value }
    }

    fun setFilterDialogArgs(value: PersistentList<EffectDialogSetting>) {
        _filterDialogArgs.update { value }
    }

    fun setCurrentEditingEffect(value: OnVideoUserEffect?) {
        _currentEditingEffect.update { value }
    }

    fun setCurrentExportWorkId(value: String?) {
        _currentExportWorkId.update { value }
    }

    private fun trimSelectedBlock(
        current: EditorState,
        inMs: Long,
        outMs: Long
    ): EditorState {
        val selectedId = current.selectedBlockId ?: return current
        val durationMs = (outMs - inMs).coerceAtLeast(0L)
        val updatedBlocks = current.blocks.map { block ->
            if (block.id != selectedId) {
                block
            } else {
                block.copyWithDuration(durationMs)
            }
        }
        return current.copy(blocks = updatedBlocks)
    }

    private fun splitSelectedBlock(current: EditorState, atMs: Long): EditorState {
        val selectedId = current.selectedBlockId ?: return current
        val blocks = current.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == selectedId }
        if (index == -1) return current
        val block = blocks[index]
        if (atMs <= 0 || atMs >= block.durationMs) return current
        val first = block.copyWithDuration(atMs).withId("${block.id}-a")
        val second = block.copyWithDuration(block.durationMs - atMs).withId("${block.id}-b")
        blocks[index] = first
        blocks.add(index + 1, second)
        return current.copy(blocks = blocks)
    }

    private fun moveBlock(current: EditorState, from: Int, to: Int): EditorState {
        val blocks = current.blocks.toMutableList()
        if (blocks.isEmpty()) return current
        val boundedFrom = from.coerceIn(0, blocks.lastIndex)
        val boundedTo = to.coerceIn(0, blocks.lastIndex)
        if (boundedFrom == boundedTo) return current
        val block = blocks.removeAt(boundedFrom)
        val insertIndex = if (boundedFrom < boundedTo) boundedTo - 1 else boundedTo
        blocks.add(insertIndex, block)
        return current.copy(blocks = blocks)
    }

    private fun TimelineBlock.copyWithDuration(durationMs: Long): TimelineBlock =
        when (this) {
            is TimelineBlock.Intro -> copy(durationMs = durationMs)
            is TimelineBlock.Roll -> copy(durationMs = durationMs)
            is TimelineBlock.Outro -> copy(durationMs = durationMs)
            is TimelineBlock.LowerThird -> copy(durationMs = durationMs)
            is TimelineBlock.Logo -> copy(durationMs = durationMs)
            is TimelineBlock.AudioBed -> copy(durationMs = durationMs)
        }

    private fun TimelineBlock.withId(id: String): TimelineBlock =
        when (this) {
            is TimelineBlock.Intro -> copy(id = id)
            is TimelineBlock.Roll -> copy(id = id)
            is TimelineBlock.Outro -> copy(id = id)
            is TimelineBlock.LowerThird -> copy(id = id)
            is TimelineBlock.Logo -> copy(id = id)
            is TimelineBlock.AudioBed -> copy(id = id)
        }
}
