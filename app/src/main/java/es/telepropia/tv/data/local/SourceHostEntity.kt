package es.telepropia.tv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import es.telepropia.tv.data.model.SourceHost

@Entity(tableName = "source_host", indices = [Index(value = ["baseUrl"], unique = true)])
data class SourceHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baseUrl: String,
    val isPrimary: Boolean
)

internal fun SourceHostEntity.toModel(): SourceHost = SourceHost(id = id, baseUrl = baseUrl, isPrimary = isPrimary)
