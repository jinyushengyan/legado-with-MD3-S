package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookRating
import io.legado.app.domain.model.BookRatingConfig
import io.legado.app.domain.model.ResolvedBookRating
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

interface BookRatingGateway {

    val configFlow: Flow<BookRatingConfig>

    val currentConfig: BookRatingConfig

    suspend fun saveConfig(config: BookRatingConfig)

    fun observeAll(): Flow<List<ResolvedBookRating>>

    fun observeByBook(bookName: String, bookAuthor: String): Flow<ResolvedBookRating?>

    suspend fun getByBook(bookName: String, bookAuthor: String): ResolvedBookRating?

    suspend fun save(rating: BookRating, scores: Map<String, Float>, notes: Map<String, String>)

    suspend fun delete(ratingKey: String)

    /** 从阅读记录导入尚未评分的书，返回新建条目数。 */
    suspend fun importFromReadRecords(): Int

    /** 导出全部评分到 UTF-8 BOM CSV（Excel/WPS 可读）。 */
    suspend fun exportCsv(output: OutputStream)
}
