package io.legado.app.data.entities

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书籍维度评分。
 *
 * 独立于 books 表：以规范化「书名 + 作者」为主键（见 ReadRecordIdentity），
 * 移出书架、换源、重新加书架都不会丢分；不设外键，删书不级联。
 */
@Keep
@Entity(tableName = "book_ratings")
data class BookRating(
    @PrimaryKey
    var ratingKey: String = "",
    var bookName: String = "",
    @ColumnInfo(defaultValue = "")
    var bookAuthor: String = "",
    /** 书在书架时写入，用于回填与换源更新；不在书架时保持最后已知值。 */
    @ColumnInfo(defaultValue = "")
    var bookUrl: String = "",
    /** 内容限制级 id，对应 BookRatingConfig.levels。 */
    @ColumnInfo(defaultValue = "")
    var levelId: String = "",
    /** 维度 id -> 分数（0-100） */
    @ColumnInfo(defaultValue = "{}")
    var scoresJson: String = "{}",
    /** 维度 id -> 短评 */
    @ColumnInfo(defaultValue = "{}")
    var notesJson: String = "{}",
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = 0L,
)
