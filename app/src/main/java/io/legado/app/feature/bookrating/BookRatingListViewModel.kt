package io.legado.app.feature.bookrating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookRating
import io.legado.app.data.entities.readRecord.ReadRecordIdentity
import io.legado.app.domain.gateway.BookRatingGateway
import io.legado.app.domain.model.BookRatingConfig
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BookRatingListViewModel(
    private val bookRatingGateway: BookRatingGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookRatingListUiState())
    val uiState: StateFlow<BookRatingListUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookRatingListEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<BookRatingListEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            bookRatingGateway.observeAll().collectLatest { list ->
                _uiState.update { it.copy(ratings = list.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            bookRatingGateway.configFlow.collectLatest { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
    }

    fun onIntent(intent: BookRatingListIntent) {
        when (intent) {
            is BookRatingListIntent.QueryChanged ->
                _uiState.update { it.copy(query = intent.query) }

            is BookRatingListIntent.EditRating -> _uiState.update {
                it.copy(
                    editing = RatingEditTarget(
                        ratingKey = intent.rating.ratingKey,
                        bookName = intent.rating.record.bookName,
                        bookAuthor = intent.rating.record.bookAuthor,
                    )
                )
            }

            BookRatingListIntent.AddManuallyClick ->
                _uiState.update { it.copy(showAddDialog = true) }

            BookRatingListIntent.DismissAddDialog ->
                _uiState.update { it.copy(showAddDialog = false) }

            is BookRatingListIntent.ConfirmAdd -> confirmAdd(intent.name, intent.author)

            is BookRatingListIntent.SaveRating ->
                save(intent.levelId, intent.scores, intent.notes)

            BookRatingListIntent.ClearEditingRating -> clearEditing()

            BookRatingListIntent.DismissEditor -> _uiState.update { it.copy(editing = null) }

            is BookRatingListIntent.DeleteClick ->
                _uiState.update { it.copy(deleting = intent.rating) }

            BookRatingListIntent.ConfirmDelete -> confirmDelete()

            BookRatingListIntent.DismissDelete -> _uiState.update { it.copy(deleting = null) }

            BookRatingListIntent.ImportFromReadRecords -> importFromReadRecords()

            BookRatingListIntent.ExportCsv -> exportCsv()
        }
    }

    /**
     * CSV 内容由 Repository 生成（IO 线程 + 动态表头），实际落盘 + 分享
     * 交给 RouteScreen（有 Context）。
     */
    private fun exportCsv() {
        viewModelScope.launch {
            val fileName = "Legado_ratings_${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.csv"
            _effects.emit(BookRatingListEffect.ExportCsvReady(fileName))
        }
    }

    /** RouteScreen 收到 effect 后拿 OutputStream 回填——在 IO 线程上由 Repository 写出 CSV。 */
    suspend fun exportToCsv(output: java.io.OutputStream) =
        bookRatingGateway.exportCsv(output)

    private fun confirmAdd(name: String, author: String) {
        val bookName = ReadRecordIdentity.bookName(name)
        val bookAuthor = ReadRecordIdentity.author(author)
        if (bookName.isBlank()) return
        _uiState.update {
            it.copy(
                showAddDialog = false,
                editing = RatingEditTarget(
                    ratingKey = BookRatingConfig.ratingKeyOf(bookName, bookAuthor),
                    bookName = bookName,
                    bookAuthor = bookAuthor,
                ),
            )
        }
    }

    private fun save(levelId: String, scores: Map<String, Float>, notes: Map<String, String>) {
        val target = _uiState.value.editing ?: return
        _uiState.update { it.copy(editing = null) }
        viewModelScope.launch {
            val existing = _uiState.value.ratings.firstOrNull { it.ratingKey == target.ratingKey }
            bookRatingGateway.save(
                BookRating(
                    ratingKey = target.ratingKey,
                    bookName = target.bookName,
                    bookAuthor = target.bookAuthor,
                    bookUrl = existing?.record?.bookUrl.orEmpty(),
                    levelId = levelId,
                ),
                scores,
                notes,
            )
        }
    }

    private fun clearEditing() {
        val target = _uiState.value.editing ?: return
        _uiState.update { it.copy(editing = null) }
        viewModelScope.launch { bookRatingGateway.delete(target.ratingKey) }
    }

    private fun confirmDelete() {
        val deleting = _uiState.value.deleting ?: return
        _uiState.update { it.copy(deleting = null) }
        viewModelScope.launch { bookRatingGateway.delete(deleting.ratingKey) }
    }

    private fun importFromReadRecords() {
        viewModelScope.launch {
            val count = bookRatingGateway.importFromReadRecords()
            _effects.emit(BookRatingListEffect.ShowImportResult(count))
        }
    }
}
