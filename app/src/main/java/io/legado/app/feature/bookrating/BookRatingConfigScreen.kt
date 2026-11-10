package io.legado.app.feature.bookrating

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.RatingDimension
import io.legado.app.domain.model.RatingLevel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun BookRatingConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BookRatingConfigViewModel = koinViewModel(),
) {
    BookRatingConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookRatingConfigScreen(
    state: BookRatingConfigUiState,
    onIntent: (BookRatingConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.book_rating_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                RatingSectionHeader(stringResource(R.string.book_rating_config))
                SplicedColumnGroup {
                    state.config.dimensions.forEach { dimension ->
                        ClickableSettingItem(
                            title = dimension.name,
                            description = dimension.detail.ifBlank { null },
                            onClick = { onIntent(BookRatingConfigIntent.EditDimension(dimension)) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        onIntent(BookRatingConfigIntent.DeleteDimensionClick(dimension))
                                    }
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.rating_dimension_add),
                        onClick = { onIntent(BookRatingConfigIntent.AddDimension) }
                    )
                }
            }
            item {
                RatingSectionHeader(stringResource(R.string.rating_content_level))
                SplicedColumnGroup {
                    state.config.levels.forEach { level ->
                        ClickableSettingItem(
                            title = level.name,
                            description = levelWeightsSummary(level, state.config),
                            onClick = { onIntent(BookRatingConfigIntent.EditLevel(level)) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        onIntent(BookRatingConfigIntent.DeleteLevelClick(level))
                                    }
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.rating_level_add),
                        onClick = { onIntent(BookRatingConfigIntent.AddLevel) }
                    )
                }
            }
        }
    }

    state.dimensionEditor?.let { editor ->
        DimensionEditorDialog(
            editor = editor,
            onSave = { name, detail ->
                onIntent(BookRatingConfigIntent.SaveDimension(name, detail))
            },
            onDismiss = { onIntent(BookRatingConfigIntent.DismissDimensionEditor) },
        )
    }

    state.levelEditor?.let { editor ->
        LevelEditorDialog(
            editor = editor,
            dimensions = state.config.dimensions,
            onSave = { name, weights ->
                onIntent(BookRatingConfigIntent.SaveLevel(name, weights))
            },
            onDismiss = { onIntent(BookRatingConfigIntent.DismissLevelEditor) },
        )
    }

    AppAlertDialog(
        show = state.deletingDimension != null,
        onDismissRequest = { onIntent(BookRatingConfigIntent.DismissDeleteDimension) },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.rating_delete_dimension_confirm),
        onConfirm = { onIntent(BookRatingConfigIntent.ConfirmDeleteDimension) },
        onDismiss = { onIntent(BookRatingConfigIntent.DismissDeleteDimension) },
    )

    AppAlertDialog(
        show = state.deletingLevel != null,
        onDismissRequest = { onIntent(BookRatingConfigIntent.DismissDeleteLevel) },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.rating_delete_confirm),
        onConfirm = { onIntent(BookRatingConfigIntent.ConfirmDeleteLevel) },
        onDismiss = { onIntent(BookRatingConfigIntent.DismissDeleteLevel) },
    )
}

@Composable
private fun RatingSectionHeader(title: String) {
    AppText(
        text = title,
        style = LegadoTheme.typography.labelMedium,
        color = LegadoTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun levelWeightsSummary(level: RatingLevel, config: BookRatingConfig): String? {
    if (level.weights.isEmpty()) return stringResource(R.string.rating_weights_equal)
    val parts = config.dimensions.mapNotNull { dimension ->
        level.weights[dimension.id]?.let { "${dimension.name}×${formatWeight(it)}" }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("、")
        ?: stringResource(R.string.rating_weights_equal)
}

private fun formatWeight(weight: Double): String {
    val rounded = (weight * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

@Composable
private fun DimensionEditorDialog(
    editor: RatingDimensionEditorState,
    onSave: (name: String, detail: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editor.name) }
    var detail by remember { mutableStateOf(editor.detail) }
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.rating_dimension_add),
        content = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.rating_dimension_name_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = stringResource(R.string.rating_dimension_detail_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, detail) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LevelEditorDialog(
    editor: RatingLevelEditorState,
    dimensions: List<RatingDimension>,
    onSave: (name: String, weights: Map<String, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editor.name) }
    var weights by remember { mutableStateOf(editor.weights) }
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.rating_level_add),
        content = {
            Column(
                modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
            ) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.rating_level_name_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = stringResource(R.string.rating_level_weights),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { weights = emptyMap() }) {
                        Text(stringResource(R.string.rating_weights_equal))
                    }
                }
                dimensions.forEach { dimension ->
                    val weight = weights[dimension.id] ?: 1.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = dimension.name,
                            style = LegadoTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        AppText(
                            text = formatWeight(weight),
                            style = LegadoTheme.typography.bodyMedium,
                            color = LegadoTheme.colorScheme.primary,
                        )
                    }
                    AppSlider(
                        value = weight.toFloat(),
                        onValueChange = { raw ->
                            val stepped = (raw * 10).roundToInt() / 10.0
                            weights = weights.toMutableMap().apply {
                                if (stepped == 1.0) remove(dimension.id)
                                else put(dimension.id, stepped)
                            }
                        },
                        valueRange = 0f..10f,
                        steps = 99,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, weights) },
        onDismiss = onDismiss,
    )
}
