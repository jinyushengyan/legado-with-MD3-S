package io.legado.app.domain.model

import androidx.annotation.Keep
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.BookRating
import io.legado.app.data.entities.readRecord.ReadRecordIdentity

/** 评分维度：所有书共用一套，可增删改。 */
@Keep
@Stable
data class RatingDimension(
    val id: String = "",
    val name: String = "",
    /** 细则参考，评分时展开查看。 */
    val detail: String = "",
)

/** 内容限制级：每级一套维度权重，缺省维度权重视为 1（等权）。 */
@Keep
@Stable
data class RatingLevel(
    val id: String = "",
    val name: String = "",
    /** dimensionId -> 权重（0-10，0.1 步进；0 = 该维度不参与总分） */
    val weights: Map<String, Double> = emptyMap(),
)

@Keep
data class BookRatingConfig(
    val dimensions: List<RatingDimension> = emptyList(),
    val levels: List<RatingLevel> = emptyList(),
) {

    fun levelOrDefault(levelId: String): RatingLevel? =
        levels.firstOrNull { it.id == levelId } ?: levels.firstOrNull()

    companion object {

        fun ratingKeyOf(bookName: String, bookAuthor: String): String =
            ReadRecordIdentity.key(bookName, bookAuthor)

        fun default(): BookRatingConfig {
            val dims = listOf(
                RatingDimension("plot", "剧情", "故事结构、伏笔回收、逻辑自洽、高潮设计"),
                RatingDimension("writing", "文笔", "文字功底、描写细腻度、对话自然度"),
                RatingDimension("character", "人设", "角色塑造、成长弧线、动机合理性"),
                RatingDimension("pacing", "节奏", "推进速度、详略分配、是否注水或拖沓"),
                RatingDimension("emotion", "情感", "情绪感染力、代入感、共鸣与后劲"),
            )
            val levels = listOf(
                RatingLevel("all_ages", "全年龄"),
                RatingLevel("mild", "轻度限制"),
                RatingLevel("adult", "限制级"),
            )
            return BookRatingConfig(dims, levels)
        }

        /**
         * 综合总分（100 分制）：Σ(维度分 / 100 × 权重) / Σ权重 × 100。
         * 只统计已打分且权重 > 0 的维度；一个都没打返回 null（未评分）。
         */
        fun totalScore(
            config: BookRatingConfig,
            levelId: String,
            scores: Map<String, Float>,
        ): Double? {
            if (config.dimensions.isEmpty() || scores.isEmpty()) return null
            val level = config.levelOrDefault(levelId)
            var weighted = 0.0
            var weightSum = 0.0
            config.dimensions.forEach { dim ->
                val raw = scores[dim.id] ?: return@forEach
                val weight = level?.weights?.get(dim.id) ?: 1.0
                if (weight <= 0.0) return@forEach
                weighted += (raw / 100.0) * weight
                weightSum += weight
            }
            if (weightSum <= 0.0) return null
            return (weighted / weightSum * 100.0).coerceIn(0.0, 100.0)
        }
    }
}

/** BookRating 实体的解析视图：JSON 列已解码为 Map。 */
@Stable
data class ResolvedBookRating(
    val record: BookRating,
    val scores: Map<String, Float>,
    val notes: Map<String, String>,
) {
    val ratingKey: String get() = record.ratingKey

    fun totalScore(config: BookRatingConfig): Double? =
        BookRatingConfig.totalScore(config, record.levelId, scores)
}
