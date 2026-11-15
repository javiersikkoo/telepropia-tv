package es.telepropia.tv

import android.app.Application
import android.content.Context
import es.telepropia.tv.data.SourceRepository
import es.telepropia.tv.data.local.AppDatabase
import es.telepropia.tv.data.local.SettingsStore
import es.telepropia.tv.data.remote.ResolverClient
import es.telepropia.tv.domain.ListLiveMatches
import es.telepropia.tv.domain.ProbeSource
import es.telepropia.tv.domain.ResolveMatch

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val repository = SourceRepository(
        db = AppDatabase.build(context),
        settings = SettingsStore(context),
        resolver = ResolverClient()
    )
    val listLiveMatches = ListLiveMatches(repository)
    val resolveMatch = ResolveMatch(repository)
    val probeSource = ProbeSource(repository)
}

fun Context.appContainer(): AppContainer = (applicationContext as App).container
