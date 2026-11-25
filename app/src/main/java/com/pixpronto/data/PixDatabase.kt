package com.pixpronto.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pix_keys", indices = [Index(value = ["value"], unique = true)])
data class PixKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: String,
    val value: String,
    val beneficiary: String,
    val city: String,
    val isDefault: Boolean = false,
)

@Entity(tableName = "pix_history", indices = [Index(value = ["createdAt"])])
data class PixHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val beneficiary: String,
    val amount: String?,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
)

@Dao interface PixDao {
    @Query("SELECT * FROM pix_keys ORDER BY isDefault DESC, label COLLATE NOCASE") fun keys(): Flow<List<PixKeyEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertKey(key: PixKeyEntity): Long
    @Update suspend fun updateKey(key: PixKeyEntity)
    @Delete suspend fun deleteKey(key: PixKeyEntity)
    @Query("SELECT * FROM pix_history ORDER BY createdAt DESC") fun history(): Flow<List<PixHistoryEntity>>
    @Insert suspend fun insertHistory(item: PixHistoryEntity): Long
    @Query("UPDATE pix_history SET favorite = :favorite WHERE id = :id") suspend fun setFavorite(id: Long, favorite: Boolean)
    @Query("DELETE FROM pix_history WHERE id = :id") suspend fun deleteHistory(id: Long)
}

@Database(entities = [PixKeyEntity::class, PixHistoryEntity::class], version = 1, exportSchema = true)
abstract class PixDatabase : RoomDatabase() {
    abstract fun pixDao(): PixDao
    companion object {
        @Volatile private var INSTANCE: PixDatabase? = null
        fun get(context: Context): PixDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PixDatabase::class.java, "pixpronto.db").build().also { INSTANCE = it }
        }
    }
}
