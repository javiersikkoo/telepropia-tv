package es.telepropia.tv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceHostDao {

    @Query("SELECT * FROM source_host ORDER BY position, id")
    fun observeAll(): Flow<List<SourceHostEntity>>

    @Query("SELECT * FROM source_host ORDER BY position, id")
    suspend fun all(): List<SourceHostEntity>

    @Query("SELECT * FROM source_host WHERE id = :id")
    suspend fun byId(id: Long): SourceHostEntity?

    @Query("SELECT COUNT(*) FROM source_host")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM source_host")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(host: SourceHostEntity): Long

    @Query("DELETE FROM source_host WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE source_host SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)

    @Query("UPDATE source_host SET label = :label WHERE id = :id")
    suspend fun setLabel(id: Long, label: String?)

    @Query("UPDATE source_host SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}
