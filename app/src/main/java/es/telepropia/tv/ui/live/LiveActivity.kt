package es.telepropia.tv.ui.live

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.telepropia.tv.R
import es.telepropia.tv.appContainer
import es.telepropia.tv.data.model.LiveEvent
import es.telepropia.tv.ui.messageRes
import es.telepropia.tv.ui.settings.SettingsActivity
import es.telepropia.tv.ui.sources.SourcesActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LiveActivity : AppCompatActivity() {

    private val viewModel: LiveViewModel by viewModels { LiveViewModel.factory(appContainer()) }

    private lateinit var sportRow: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var refreshing: ProgressBar
    private lateinit var message: TextView
    private lateinit var banner: TextView
    private val adapter = EventAdapter { event -> openSources(event) }
    private val sportViews = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
        sportRow = findViewById(R.id.sport_row)
        list = findViewById(R.id.event_list)
        loading = findViewById(R.id.loading)
        refreshing = findViewById(R.id.refreshing)
        message = findViewById(R.id.message)
        banner = findViewById(R.id.degraded_banner)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        // Las animaciones de cambio hacen saltar el foco del mando en cada refresco.
        list.itemAnimator = null

        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        buildSportFilter()
        observe()
        if (savedInstanceState == null) openSettingsIfUnconfigured()
    }

    override fun onStop() {
        super.onStop()
        viewModel.forgetDetections()
    }

    private fun buildSportFilter() {
        val inflater = LayoutInflater.from(this)
        viewModel.sports.forEach { sport ->
            val chip = inflater.inflate(R.layout.item_sport, sportRow, false) as TextView
            chip.id = View.generateViewId()
            chip.text = sport.label
            chip.nextFocusDownId = R.id.event_list
            chip.setOnClickListener { viewModel.selectSport(sport.id) }
            sportRow.addView(chip)
            sportViews[sport.id] = chip
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeSportId.collect { active ->
                        sportViews.forEach { (id, view) -> view.isSelected = id == active }
                    }
                }
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.refreshing.collect { busy ->
                        refreshing.visibility = if (busy && !loading.isVisible) View.VISIBLE else View.INVISIBLE
                    }
                }
                launch {
                    while (isActive) {
                        viewModel.refresh()
                        delay(REFRESH_INTERVAL_MS)
                    }
                }
            }
        }
    }

    private fun render(state: LiveState) {
        loading.isVisible = state is LiveState.Loading
        list.isVisible = state is LiveState.Content
        message.isVisible = state is LiveState.Empty || state is LiveState.Error
        when (state) {
            is LiveState.Content -> {
                adapter.submitList(state.rows)
                showDegraded(state.degraded)
            }
            is LiveState.Empty -> {
                message.setText(R.string.live_empty)
                showDegraded(state.degraded)
            }
            is LiveState.Error -> {
                message.setText(state.failure.messageRes(R.string.error_list_other))
                showDegraded(null)
            }
            LiveState.Loading -> showDegraded(null)
        }
    }

    private fun showDegraded(degraded: Degraded?) {
        banner.isVisible = degraded != null
        if (degraded != null) {
            banner.text = resources.getQuantityString(R.plurals.live_degraded, degraded.active, degraded.failing, degraded.active)
        }
    }

    private fun openSettingsIfUnconfigured() {
        lifecycleScope.launch {
            if (viewModel.needsSetup()) {
                startActivity(SettingsActivity.intent(this@LiveActivity, firstRun = true))
            }
        }
    }

    private fun openSources(event: LiveEvent) {
        startActivity(SourcesActivity.intent(this, event))
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
    }
}
