package es.telepropia.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.telepropia.tv.data.model.Sports
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore
    private val activeSportKey = intPreferencesKey("active_sport_id")

    val activeSportId: Flow<Int> = store.data.map { it[activeSportKey] ?: Sports.DEFAULT_ID }

    suspend fun setActiveSport(id: Int) {
        store.edit { it[activeSportKey] = id }
    }
}
