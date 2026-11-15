package es.telepropia.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.telepropia.tv.data.model.Sports
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore
    private val activeHostKey = longPreferencesKey("active_host_id")
    private val activeSportKey = intPreferencesKey("active_sport_id")

    val activeHostId: Flow<Long?> = store.data.map { it[activeHostKey] }

    val activeSportId: Flow<Int> = store.data.map { it[activeSportKey] ?: Sports.DEFAULT_ID }

    suspend fun setActiveHost(id: Long?) {
        store.edit { prefs ->
            if (id == null) {
                prefs.remove(activeHostKey)
            } else {
                prefs[activeHostKey] = id
            }
        }
    }

    suspend fun setActiveSport(id: Int) {
        store.edit { it[activeSportKey] = id }
    }
}
