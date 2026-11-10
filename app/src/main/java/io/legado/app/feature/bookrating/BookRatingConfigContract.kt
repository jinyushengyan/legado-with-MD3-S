package io.legado.app.feature.bookrating

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.RatingDimension
import io.legado.app.domain.model.RatingLevel

/** 维度编辑草稿；id 为空表示新建。 */
@Stable
data class RatingDimensionEditorState(
    val id: String = "",
    val name: String = "",
    val detail: String = "",
)

/** 限制级编辑草稿；id 为空表示新建。 */
@Stable
data class RatingLevelEditorState(
    val id: String = "",
    val name: String = "",
    val weights: Map<String, Double> = emptyMap(),
)

@Stable
data class BookRatingConfigUiState(
    val config: BookRatingConfig = BookRatingConfig(),
    val dimensionEditor: RatingDimensionEditorState? = null,
    val levelEditor: RatingLevelEditorState? = null,
    val deletingDimension: RatingDimension? = null,
    val deletingLevel: RatingLevel? = null,
)

sealed interface BookRatingConfigIntent {

    data object AddDimension : BookRatingConfigIntent

    data class EditDimension(val dimension: RatingDimension) : BookRatingConfigIntent

    data object DismissDimensionEditor : BookRatingConfigIntent

    data class SaveDimension(val name: String, val detail: String) : BookRatingConfigIntent

    data class DeleteDimensionClick(val dimension: RatingDimension) : BookRatingConfigIntent

    data object ConfirmDeleteDimension : BookRatingConfigIntent

    data object DismissDeleteDimension : BookRatingConfigIntent

    data object AddLevel : BookRatingConfigIntent

    data class EditLevel(val level: RatingLevel) : BookRatingConfigIntent

    data object DismissLevelEditor : BookRatingConfigIntent

    data class SaveLevel(val name: String, val weights: Map<String, Double>) : BookRatingConfigIntent

    data class DeleteLevelClick(val level: RatingLevel) : BookRatingConfigIntent

    data object ConfirmDeleteLevel : BookRatingConfigIntent

    data object DismissDeleteLevel : BookRatingConfigIntent
}
