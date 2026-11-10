package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookRating
import kotlinx.coroutines.flow.Flow

@Dao
interface BookRatingDao {

    @get:Query("SELECT * FROM book_ratings ORDER BY updatedAt DESC")
    val all: List<BookRating>

    @Query("SELECT * FROM book_ratings ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BookRating>>

    @Query("SELECT * FROM book_ratings WHERE ratingKey = :ratingKey")
    fun get(ratingKey: String): BookRating?

    @Query("SELECT * FROM book_ratings WHERE ratingKey = :ratingKey")
    fun observe(ratingKey: String): Flow<BookRating?>

    /** 恢复备份时以备份侧为准覆盖本地评分。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg rating: BookRating)

    @Query("DELETE FROM book_ratings WHERE ratingKey = :ratingKey")
    fun delete(ratingKey: String)
}
