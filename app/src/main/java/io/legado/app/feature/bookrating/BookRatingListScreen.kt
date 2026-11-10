package io.legado.app.feature.bookrating

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.ResolvedBookRating
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookRatingListRouteScreen(
    onBackClick: () -> Unit,
    editName: String? = null,
    editAuthor: String? = null,
    viewModel: BookRatingListViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()

    // SAF 另存为：系统文件选择器返回目标 Uri 后，把 CSV 写进去
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException(
                            context.getString(R.string.rating_storage_unavailable)
                        )
                    out.use { viewModel.exportToCsv(it) }
                }.onSuccess {
                    context.toastOnUi(context.getString(R.string.rating_export_success))
                }.onFailure {
                    context.toastOnUi(
                        context.getString(R.string.rating_export_failed, it.message ?: "unknown")
                    )
                }
            }
        }
    }

    // 阅读记录等入口带书名直达：进页即打开该书的评分编辑弹层
    LaunchedEffect(editName, editAuthor) {
        if (!editName.isNullOrBlank()) {
            viewModel.onIntent(BookRatingListIntent.ConfirmAdd(editName, editAuthor.orEmpty()))
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookRatingListEffect.ShowImportResult -> context.toastOnUi(
                    context.getString(R.string.rating_import_result, effect.count)
                )
                is BookRatingListEffect.ExportCsvReady -> exportLauncher.launch(effect.fileName)
            }
        }
    }

    BookRatingListScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookRatingListScreen(
    state: BookRatingListUiState,
    onIntent: (BookRatingListIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.book_rating),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
                    IconButton(onClick = { onIntent(BookRatingListIntent.ExportCsv) }) {
                        AppIcon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.rating_export),
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val filtered = remember(state.ratings, state.query) {
            val query = state.query.trim()
            if (query.isEmpty()) {
                state.ratings
            } else {
                state.ratings.filter {
                    it.record.bookName.contains(query, ignoreCase = true) ||
                            it.record.bookAuthor.contains(query, ignoreCase = true)
                }.toImmutableList()
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                AppTextField(
                    value = state.query,
                    onValueChange = { onIntent(BookRatingListIntent.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = stringResource(R.string.search),
                )
            }
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.rating_import_from_read_records),
                        onClick = { onIntent(BookRatingListIntent.ImportFromReadRecords) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.rating_add_manually),
                        onClick = { onIntent(BookRatingListIntent.AddManuallyClick) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyMessage(message = stringResource(R.string.rating_empty))
                    }
                }
            } else {
                items(filtered, key = { it.ratingKey }) { rating ->
                    BookRatingRow(
                        rating = rating,
                        config = state.config,
                        onClick = { onIntent(BookRatingListIntent.EditRating(rating)) },
                        onDelete = { onIntent(BookRatingListIntent.DeleteClick(rating)) },
                    )
                }
            }
        }
    }

    // 评分编辑弹层：手动新建时列表里还没有条目，existing 允许为 null
    val editing = state.editing
    BookRatingSheet(
        show = editing != null,
        bookName = editing?.bookName.orEmpty(),
        config = state.config,
        existing = state.ratings.firstOrNull { it.ratingKey == editing?.ratingKey },
        onSave = { levelId, scores, notes ->
            onIntent(BookRatingListIntent.SaveRating(levelId, scores, notes))
        },
        onClear = { onIntent(BookRatingListIntent.ClearEditingRating) },
        onDismissRequest = { onIntent(BookRatingListIntent.DismissEditor) },
    )

    if (state.showAddDialog) {
        AddRatingDialog(
            onConfirm = { name, author ->
                onIntent(BookRatingListIntent.ConfirmAdd(name, author))
            },
            onDismiss = { onIntent(BookRatingListIntent.DismissAddDialog) },
        )
    }

    AppAlertDialog(
        show = state.deleting != null,
        onDismissRequest = { onIntent(BookRatingListIntent.DismissDelete) },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.rating_delete_confirm),
        onConfirm = { onIntent(BookRatingListIntent.ConfirmDelete) },
        onDismiss = { onIntent(BookRatingListIntent.DismissDelete) },
    )
}

@Composable
private fun BookRatingRow(
    rating: ResolvedBookRating,
    config: BookRatingConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val total = rating.totalScore(config)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = rating.record.bookName,
                    style = LegadoTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rating.record.bookAuthor.isNotBlank()) {
                    AppText(
                        text = rating.record.bookAuthor,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AppText(
                text = total?.let { stringResource(R.string.rating_total_score, it) }
                    ?: stringResource(R.string.rating_not_rated),
                style = LegadoTheme.typography.titleSmall,
                color = if (total != null) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (total != null) FontWeight.Bold else null,
            )
            IconButton(onClick = onDelete) {
                AppIcon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddRatingDialog(
    onConfirm: (name: String, author: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.rating_add_manually),
        content = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.rating_book_name_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = stringResource(R.string.rating_book_author_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name, author) },
        onDismiss = onDismiss,
    )
}
