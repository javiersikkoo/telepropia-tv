package es.telepropia.tv.data

import android.os.SystemClock
import androidx.room.withTransaction
import es.telepropia.tv.data.local.AppDatabase
import es.telepropia.tv.data.local.SettingsStore
import es.telepropia.tv.data.local.SourceHostEntity
import es.telepropia.tv.data.local.toModel
import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.HostFetch
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.HostStatus
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.data.model.SourceHealth
import es.telepropia.tv.data.model.SourceHost
import es.telepropia.tv.data.model.Sports
import es.telepropia.tv.data.remote.HostUrls
import es.telepropia.tv.data.remote.ResolverClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
    private val clock: () -> Long = SystemClock::elapsedRealtime
    private val pauses = HostPauses()
    private val aggregator = LiveAggregator(resolver::live, pauses, clock)

    val hostStatuses: Flow<List<HostStatus>> = combine(dao.observeAll(), pauses.current) { rows, paused ->
        rows.map { it.toModel() }.primaryFirst().map { HostStatus(it, paused[it.id]?.failure) }
    }

    val activeSportId: Flow<Int> = settings.activeSportId

    suspend fun setActiveSport(sportId: Int) = settings.setActiveSport(sportId)

    suspend fun hasHosts(): Boolean = dao.count() > 0

    suspend fun addHost(raw: String): AddHostResult {
        val baseUrl = HostUrls.normalize(raw) ?: return AddHostResult.Invalid
        val id = dao.insert(SourceHostEntity(baseUrl = baseUrl, isPrimary = false))
        if (id == -1L) return AddHostResult.Duplicate
        if (dao.primary() == null) dao.markPrimary(id)
        val host = dao.byId(id)?.toModel() ?: return AddHostResult.Invalid
        return AddHostResult.Added(host)
    }

    suspend fun setPrimary(id: Long) = dao.markPrimary(id)

    suspend fun setState(id: Long, state: HostState) {
        dao.setState(id, state.name)
        if (state == HostState.ACTIVE) pauses.resume(id)
    }

    suspend fun deleteHost(id: Long) {
        db.withTransaction {
            val wasPrimary = dao.byId(id)?.isPrimary == true
            dao.delete(id)
            if (wasPrimary) dao.all().firstOrNull()?.let { dao.markPrimary(it.id) }
        }
        pauses.resume(id)
        aggregator.forget(id)
    }

    suspend fun fetchLive(sportId: Int): Outcome<HostFetch> =
        aggregator.fetch(dao.all().map { it.toModel() }, sportId)

    // PlaybackSpec no se guarda en ningún sitio: cada play pasa por aquí, y siempre contra el host de la fuente.
    suspend fun resolve(hostId: Long, matchId: String, sportId: Int): Outcome<PlaybackSpec> {
        val host = dao.byId(hostId)?.toModel() ?: return Outcome.Err(Failure.NoHost)
        return resolver.resolve(host.baseUrl, matchId, sportId)
    }

    suspend fun probe(host: SourceHost): SourceHealth {
        val started = clock()
        val outcome = resolver.live(host, Sports.PROBE_ID)
        val latency = clock() - started
        return when (outcome) {
            is Outcome.Ok -> {
                pauses.resume(host.id)
                SourceHealth(ok = true, latencyMs = latency, failure = null)
            }
            is Outcome.Err -> {
                if (host.state == HostState.ACTIVE) pauses.pause(host.id, outcome.failure, clock())
                SourceHealth(ok = false, latencyMs = latency, failure = outcome.failure)
            }
        }
    }
}
