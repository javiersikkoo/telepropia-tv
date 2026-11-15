package es.telepropia.tv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.SourceHost

@Entity(tableName = "source_host", indices = [Index(value = ["baseUrl"], unique = true)])
data class SourceHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baseUrl: String,
    val isPrimary: Boolean,
    @ColumnInfo(defaultValue = STATE_ACTIVE) val state: String = STATE_ACTIVE
)

internal const val STATE_ACTIVE = "ACTIVE"

internal fun SourceHostEntity.toModel(): SourceHost = SourceHost(
    id = id,
    baseUrl = baseUrl,
    isPrimary = isPrimary,
    state = HostState.entries.firstOrNull { it.name == state } ?: HostState.ACTIVE
)
