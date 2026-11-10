package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookRating
import io.legado.app.data.entities.readRecord.ReadRecordIdentity
import io.legado.app.domain.gateway.BookRatingGateway
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.ResolvedBookRating
import io.legado.app.help.config.AppConfigStore
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class BookRatingRepository(
    private val appDatabase: AppDatabase,
) : BookRatingGateway {

    private val dao get() = appDatabase.bookRatingDao

    override val configFlow: Flow<BookRatingConfig> =
        AppConfigStore.preferencesFlow
            .map { parseConfig(AppConfigStore.getString(PreferKey.bookRatingConfig)) }
            .distinctUntilChanged()

    override val currentConfig: BookRatingConfig
        get() = parseConfig(AppConfigStore.getString(PreferKey.bookRatingConfig))

    override suspend fun saveConfig(config: BookRatingConfig) {
        AppConfigStore.putString(PreferKey.bookRatingConfig, GSON.toJson(config))
    }

    override fun observeAll(): Flow<List<ResolvedBookRating>> =
        dao.observeAll().map { list -> list.map { resolve(it) } }

    override fun observeByBook(bookName: String, bookAuthor: String): Flow<ResolvedBookRating?> =
        dao.observe(BookRatingConfig.ratingKeyOf(bookName, bookAuthor))
            .map { it?.let(::resolve) }

    override suspend fun getByBook(bookName: String, bookAuthor: String): ResolvedBookRating? =
        withContext(Dispatchers.IO) {
            dao.get(BookRatingConfig.ratingKeyOf(bookName, bookAuthor))?.let(::resolve)
        }

    override suspend fun save(
        rating: BookRating,
        scores: Map<String, Float>,
        notes: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            rating.copy(
                scoresJson = GSON.toJson(scores),
                notesJson = GSON.toJson(notes),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun delete(ratingKey: String) = withContext(Dispatchers.IO) {
        dao.delete(ratingKey)
    }

    override suspend fun importFromReadRecords(): Int = withContext(Dispatchers.IO) {
        // 阅读记录只有本地分区；规范化身份键与评分表主键同源
        val records = appDatabase.readRecordDao.all.filter { it.deviceId.isEmpty() }
        var imported = 0
        records.forEach { record ->
            val name = ReadRecordIdentity.bookName(record.bookName)
            val author = ReadRecordIdentity.author(record.bookAuthor)
            if (name.isBlank()) return@forEach
            val key = BookRatingConfig.ratingKeyOf(name, author)
            if (dao.get(key) == null) {
                dao.insert(
                    BookRating(
                        ratingKey = key,
                        bookName = name,
                        bookAuthor = author,
                        updatedAt = 0L,
                    )
                )
                imported++
            }
        }
        imported
    }

    /**
     * 把当前全部评分导出为 UTF-8 BOM CSV（Excel/WPS 直接双击可读）。
     * 表头根据 config.dimensions 动态生成——用户删改维度后导出仍对齐。
     * 列：书名, 作者, 内容限制级, 各维度分数, 总分。
     */
    override suspend fun exportCsv(output: OutputStream) = withContext(Dispatchers.IO) {
        val config = currentConfig
        val ratings = dao.all.map { resolve(it) }
        // Excel/WPS 识别 BOM：直接写字节，避免 Char 构造歧义
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8).buffered()
        val header = buildString {
            append("书名,作者,内容限制级")
            config.dimensions.forEach { dim -> append(',').append(dim.name) }
            append(",总分")
        }
        writer.appendLine(header)
        ratings.forEach { item ->
            val levelName = config.levels.firstOrNull { it.id == item.record.levelId }?.name.orEmpty()
            val total = BookRatingConfig.totalScore(config, item.record.levelId, item.scores)
            val row = buildString {
                append(item.record.bookName.csvEscape())
                append(',').append(item.record.bookAuthor.csvEscape())
                append(',').append(levelName.csvEscape())
                config.dimensions.forEach { dim ->
                    val score = item.scores[dim.id]
                    val intScore = score?.let { (it + 0.5f).toInt() }
                    append(',').append(intScore?.toString().orEmpty())
                }
                append(',').append(total?.let { String.format("%.1f", it) }.orEmpty())
            }
            writer.appendLine(row)
        }
        writer.flush()
    }

    private fun String.csvEscape(): String {
        val needsQuote = any { it in "R\"\n," } || isEmpty() || startsWith(' ')
        if (!needsQuote) return this
        return "\"${replace("\"", "\"\"")}\""
    }

    private fun parseConfig(json: String?): BookRatingConfig {
        if (json.isNullOrBlank()) return BookRatingConfig.default()
        return GSON.fromJsonObject<BookRatingConfig>(json).getOrNull()
            ?.takeIf { it.dimensions.isNotEmpty() }
            ?: BookRatingConfig.default()
    }

    private fun resolve(record: BookRating): ResolvedBookRating {
        val scores = GSON.fromJsonObject<Map<String, Float>>(record.scoresJson)
            .getOrNull().orEmpty()
        val notes = GSON.fromJsonObject<Map<String, String>>(record.notesJson)
            .getOrNull().orEmpty()
        return ResolvedBookRating(record, scores, notes)
    }
}
