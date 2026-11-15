package es.telepropia.tv.data

import android.os.SystemClock
import androidx.room.withTransaction
import es.telepropia.tv.data.local.AppDatabase
import es.telepropia.tv.data.local.SettingsStore
import es.telepropia.tv.data.local.SourceHostEntity
import es.telepropia.tv.data.local.toModel
import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.data.model.SourceHealth
import es.telepropia.tv.data.model.SourceHost
import es.telepropia.tv.data.model.Sports
import es.telepropia.tv.data.remote.HostUrls
import es.telepropia.tv.data.remote.ResolverClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

sealed interface AddHostResult {
    data class Added(val host: SourceHost) : AddHostResult
    data object Duplicate : AddHostResult
    data object Invalid : AddHostResult
}

class SourceRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val resolver: ResolverClient
) {
    private val dao = db.hostDao()

    val hosts: Flow<List<SourceHost>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    val activeSportId: Flow<Int> = settings.activeSportId

    suspend fun setActiveSport(sportId: Int) = settings.setActiveSport(sportId)

    suspend fun hasHosts(): Boolean = dao.count() > 0

    suspend fun addHost(raw: String): AddHostResult {
        val baseUrl = HostUrls.normalize(raw) ?: return AddHostResult.Invalid
        val id = dao.insert(SourceHostEntity(baseUrl = baseUrl, isPrimary = false))
        if (id == -1L) return AddHostResult.Duplicate
        if (dao.primary() == null) setPrimary(id)
        val host = dao.byId(id)?.toModel() ?: return AddHostResult.Invalid
        return AddHostResult.Added(host)
    }

    suspend fun setPrimary(id: Long) {
        dao.markPrimary(id)
        settings.setActiveHost(id)
    }

    suspend fun deleteHost(id: Long) {
        db.withTransaction {
            val wasPrimary = dao.byId(id)?.isPrimary == true
            dao.delete(id)
            if (wasPrimary) dao.all().firstOrNull()?.let { dao.markPrimary(it.id) }
        }
        settings.setActiveHost(dao.primary()?.id)
    }

    suspend fun listLive(sportId: Int): Outcome<List<Match>> {
        val host = activeHost() ?: return Outcome.Err(Failure.NoHost)
        return resolver.live(host.baseUrl, sportId)
    }

    // PlaybackSpec no se guarda en ningún sitio: cada play pasa por aquí.
    suspend fun resolve(matchId: String, sportId: Int): Outcome<PlaybackSpec> {
        val host = activeHost() ?: return Outcome.Err(Failure.NoHost)
        return resolver.resolve(host.baseUrl, matchId, sportId)
    }

    suspend fun probe(host: SourceHost): SourceHealth {
        val started = SystemClock.elapsedRealtime()
        val outcome = resolver.live(host.baseUrl, Sports.PROBE_ID)
        val latency = SystemClock.elapsedRealtime() - started
        return when (outcome) {
            is Outcome.Ok -> SourceHealth(ok = true, latencyMs = latency, failure = null)
            is Outcome.Err -> SourceHealth(ok = false, latencyMs = latency, failure = outcome.failure)
        }
    }

    private suspend fun activeHost(): SourceHost? {
        val all = dao.all().map { it.toModel() }
        val activeId = settings.activeHostId.first()
        return all.firstOrNull { it.id == activeId }
            ?: all.firstOrNull { it.isPrimary }
            ?: all.firstOrNull()
    }
}
