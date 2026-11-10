package io.legado.app.feature.bookrating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.ResolvedBookRating
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.roundToInt

/**
 * 维度评分编辑弹层。书籍详情页、「我的评分」总览、阅读记录补录共用；
 * 只依赖书名+作者，不要求书在书架。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookRatingSheet(
    show: Boolean,
    bookName: String,
    config: BookRatingConfig,
    existing: ResolvedBookRating?,
    onSave: (levelId: String, scores: Map<String, Float>, notes: Map<String, String>) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.rating_edit_title),
    ) {
        if (!show) return@AppModalBottomSheet
        var levelId by remember { mutableStateOf("") }
        val scores = remember { mutableStateMapOf<String, Float>() }
        val notes = remember { mutableStateMapOf<String, String>() }
        // 每次打开时用已有评分初始化草稿
        LaunchedEffect(show, existing?.ratingKey, existing?.record?.updatedAt) {
            levelId = existing?.record?.levelId
                ?: config.levels.firstOrNull()?.id.orEmpty()
            scores.clear()
            existing?.scores?.let { scores.putAll(it) }
            notes.clear()
            existing?.notes?.let { notes.putAll(it) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AppText(
                text = bookName,
                style = LegadoTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (config.levels.isNotEmpty()) {
                AppText(
                    text = stringResource(R.string.rating_content_level),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    config.levels.forEach { level ->
                        FilterChip(
                            selected = levelId == level.id,
                            onClick = { levelId = level.id },
                            label = { Text(level.name) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val total = BookRatingConfig.totalScore(config, levelId, scores.toMap())
            AppText(
                text = total?.let { stringResource(R.string.rating_total_score, it) }
                    ?: stringResource(R.string.rating_not_rated),
                style = LegadoTheme.typography.titleLarge,
                color = LegadoTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            config.dimensions.forEach { dim ->
                RatingDimensionEditor(
                    dimensionId = dim.id,
                    name = dim.name,
                    detail = dim.detail,
                    score = scores[dim.id],
                    note = notes[dim.id].orEmpty(),
                    onScoreChange = { value ->
                        if (value == null) scores.remove(dim.id)
                        else scores[dim.id] = value
                    },
                    onNoteChange = { text ->
                        if (text.isBlank()) notes.remove(dim.id)
                        else notes[dim.id] = text
                    },
                )
                HorizontalDivider()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (existing != null) {
                    TextButton(onClick = onClear) {
                        Text(
                            text = stringResource(R.string.rating_clear),
                            color = LegadoTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = {
                        onSave(levelId, scores.toMap(), notes.toMap())
                    },
                    enabled = scores.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun RatingDimensionEditor(
    dimensionId: String,
    name: String,
    detail: String,
    score: Float?,
    note: String,
    onScoreChange: (Float?) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    // 短评默认折叠；已有内容时默认展开，避免用户以为内容丢失
    var noteExpanded by remember { mutableStateOf(note.isNotBlank()) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                TextButton(onClick = { noteExpanded = !noteExpanded }) {
                    Text(
                        text = stringResource(R.string.rating_note_hint),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppText(
                text = name,
                style = LegadoTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ScoreInput(
                    score = score,
                    onScoreChange = onScoreChange,
                )
            }
        }
        AppSlider(
            value = score ?: 0f,
            onValueChange = { onScoreChange(it.roundToInt().coerceIn(0, 100).toFloat()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth(),
        )
        if (noteExpanded) {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                placeholder = { Text(stringResource(R.string.rating_note_hint)) },
                singleLine = true,
            )
        }
        // 细则参考默认完整展示：长文本放浅色容器里，阅读不费力
        if (detail.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(
                        color = LegadoTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                AppText(
                    text = stringResource(R.string.rating_dimension_detail),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                )
                AppText(
                    text = detail,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreInput(
    score: Float?,
    onScoreChange: (Float?) -> Unit,
) {
    var text by remember { mutableStateOf(score?.toInt()?.toString() ?: "") }
    LaunchedEffect(score) {
        val expected = score?.toInt()?.toString() ?: ""
        if (text != expected) text = expected
    }
    val textStyle = LegadoTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        color = LegadoTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    BasicTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(3)
            text = filtered
            val value = filtered.toIntOrNull()
            when {
                value == null -> onScoreChange(null)
                value in 0..100 -> onScoreChange(value.toFloat())
                else -> {
                    text = "100"
                    onScoreChange(100f)
                }
            }
        },
        modifier = Modifier
            .widthIn(min = 48.dp, max = 56.dp)
            .height(40.dp),
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .border(
                        width = 1.dp,
                        color = LegadoTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "—",
                        style = textStyle,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}
