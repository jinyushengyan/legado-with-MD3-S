package io.legado.app.feature.bookrating

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.ResolvedBookRating
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** 评分编辑目标：只依赖书名+作者，书可以不在书架。 */
@Stable
data class RatingEditTarget(
    val ratingKey: String,
    val bookName: String,
    val bookAuthor: String,
)

@Stable
data class BookRatingListUiState(
    val ratings: ImmutableList<ResolvedBookRating> = persistentListOf(),
    val config: BookRatingConfig = BookRatingConfig(),
    val query: String = "",
    val editing: RatingEditTarget? = null,
    val showAddDialog: Boolean = false,
    val deleting: ResolvedBookRating? = null,
)

sealed interface BookRatingListIntent {

    data class QueryChanged(val query: String) : BookRatingListIntent

    data class EditRating(val rating: ResolvedBookRating) : BookRatingListIntent

    data object AddManuallyClick : BookRatingListIntent

    data object DismissAddDialog : BookRatingListIntent

    data class ConfirmAdd(val name: String, val author: String) : BookRatingListIntent

    data class SaveRating(
        val levelId: String,
        val scores: Map<String, Float>,
        val notes: Map<String, String>,
    ) : BookRatingListIntent

    data object ClearEditingRating : BookRatingListIntent

    data object DismissEditor : BookRatingListIntent

    data class DeleteClick(val rating: ResolvedBookRating) : BookRatingListIntent

    data object ConfirmDelete : BookRatingListIntent

    data object DismissDelete : BookRatingListIntent

    data object ImportFromReadRecords : BookRatingListIntent

    data object ExportCsv : BookRatingListIntent
}

sealed interface BookRatingListEffect {

    data class ShowImportResult(val count: Int) : BookRatingListEffect

    data class ExportCsvReady(val fileName: String) : BookRatingListEffect
}
