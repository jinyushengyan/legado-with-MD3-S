package io.legado.app.feature.bookrating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BookRatingGateway
import io.legado.app.domain.model.RatingDimension
import io.legado.app.domain.model.RatingLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookRatingConfigViewModel(
    private val bookRatingGateway: BookRatingGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookRatingConfigUiState())
    val uiState: StateFlow<BookRatingConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookRatingGateway.configFlow.collectLatest { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
    }

    fun onIntent(intent: BookRatingConfigIntent) {
        when (intent) {
            BookRatingConfigIntent.AddDimension -> _uiState.update {
                it.copy(dimensionEditor = RatingDimensionEditorState())
            }

            is BookRatingConfigIntent.EditDimension -> _uiState.update {
                it.copy(
                    dimensionEditor = RatingDimensionEditorState(
                        id = intent.dimension.id,
                        name = intent.dimension.name,
                        detail = intent.dimension.detail,
                    )
                )
            }

            BookRatingConfigIntent.DismissDimensionEditor ->
                _uiState.update { it.copy(dimensionEditor = null) }

            is BookRatingConfigIntent.SaveDimension ->
                saveDimension(intent.name.trim(), intent.detail.trim())

            is BookRatingConfigIntent.DeleteDimensionClick ->
                _uiState.update { it.copy(deletingDimension = intent.dimension) }

            BookRatingConfigIntent.ConfirmDeleteDimension -> deleteDimension()

            BookRatingConfigIntent.DismissDeleteDimension ->
                _uiState.update { it.copy(deletingDimension = null) }

            BookRatingConfigIntent.AddLevel -> _uiState.update {
                it.copy(levelEditor = RatingLevelEditorState())
            }

            is BookRatingConfigIntent.EditLevel -> _uiState.update {
                it.copy(
                    levelEditor = RatingLevelEditorState(
                        id = intent.level.id,
                        name = intent.level.name,
                        weights = intent.level.weights,
                    )
                )
            }

            BookRatingConfigIntent.DismissLevelEditor ->
                _uiState.update { it.copy(levelEditor = null) }

            is BookRatingConfigIntent.SaveLevel ->
                saveLevel(intent.name.trim(), intent.weights)

            is BookRatingConfigIntent.DeleteLevelClick ->
                _uiState.update { it.copy(deletingLevel = intent.level) }

            BookRatingConfigIntent.ConfirmDeleteLevel -> deleteLevel()

            BookRatingConfigIntent.DismissDeleteLevel ->
                _uiState.update { it.copy(deletingLevel = null) }
        }
    }

    private fun saveDimension(name: String, detail: String) {
        val editor = _uiState.value.dimensionEditor ?: return
        if (name.isEmpty()) return
        val config = _uiState.value.config
        val dimensions = config.dimensions.toMutableList()
        if (editor.id.isEmpty()) {
            // 新维度不进任何限制级的 weights：计分时缺省按权重 1（等权）处理
            dimensions.add(
                RatingDimension(
                    id = "dim_${System.currentTimeMillis()}",
                    name = name,
                    detail = detail,
                )
            )
        } else {
            val index = dimensions.indexOfFirst { it.id == editor.id }
            if (index < 0) return
            dimensions[index] = dimensions[index].copy(name = name, detail = detail)
        }
        persist(config.copy(dimensions = dimensions))
        _uiState.update { it.copy(dimensionEditor = null) }
    }

    private fun deleteDimension() {
        val deleting = _uiState.value.deletingDimension ?: return
        val config = _uiState.value.config
        persist(
            config.copy(dimensions = config.dimensions.filterNot { it.id == deleting.id })
        )
        _uiState.update { it.copy(deletingDimension = null) }
    }

    private fun saveLevel(name: String, weights: Map<String, Double>) {
        val editor = _uiState.value.levelEditor ?: return
        if (name.isEmpty()) return
        val config = _uiState.value.config
        val validDimensionIds = config.dimensions.mapTo(HashSet()) { it.id }
        val cleaned = weights.filterKeys { it in validDimensionIds }
        val levels = config.levels.toMutableList()
        if (editor.id.isEmpty()) {
            levels.add(
                RatingLevel(
                    id = "level_${System.currentTimeMillis()}",
                    name = name,
                    weights = cleaned,
                )
            )
        } else {
            val index = levels.indexOfFirst { it.id == editor.id }
            if (index < 0) return
            levels[index] = levels[index].copy(name = name, weights = cleaned)
        }
        persist(config.copy(levels = levels))
        _uiState.update { it.copy(levelEditor = null) }
    }

    private fun deleteLevel() {
        val deleting = _uiState.value.deletingLevel ?: return
        val config = _uiState.value.config
        persist(config.copy(levels = config.levels.filterNot { it.id == deleting.id }))
        _uiState.update { it.copy(deletingLevel = null) }
    }

    private fun persist(config: io.legado.app.domain.model.BookRatingConfig) {
        viewModelScope.launch { bookRatingGateway.saveConfig(config) }
    }
}
