package es.telepropia.tv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceHostDao {

    @Query("SELECT * FROM source_host ORDER BY id")
    fun observeAll(): Flow<List<SourceHostEntity>>

    @Query("SELECT * FROM source_host ORDER BY id")
    suspend fun all(): List<SourceHostEntity>

    @Query("SELECT * FROM source_host WHERE id = :id")
    suspend fun byId(id: Long): SourceHostEntity?

    @Query("SELECT * FROM source_host WHERE isPrimary = 1 LIMIT 1")
    suspend fun primary(): SourceHostEntity?

    @Query("SELECT COUNT(*) FROM source_host")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(host: SourceHostEntity): Long

    @Query("DELETE FROM source_host WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE source_host SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun markPrimary(id: Long)

    @Query("UPDATE source_host SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)
}
