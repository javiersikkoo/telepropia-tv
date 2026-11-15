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
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.ui.messageRes
import es.telepropia.tv.ui.player.PlayerActivity
import es.telepropia.tv.ui.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LiveActivity : AppCompatActivity() {

    private val viewModel: LiveViewModel by viewModels { LiveViewModel.factory(appContainer()) }

    private lateinit var sportRow: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var message: TextView
    private val adapter = MatchAdapter { match -> openPlayer(match) }
    private val sportViews = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
        sportRow = findViewById(R.id.sport_row)
        list = findViewById(R.id.match_list)
        loading = findViewById(R.id.loading)
        message = findViewById(R.id.message)

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
            chip.nextFocusDownId = R.id.match_list
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
            is LiveState.Content -> adapter.submitList(state.rows)
            LiveState.Empty -> message.setText(R.string.live_empty)
            is LiveState.Error -> message.setText(state.failure.messageRes(R.string.error_list_other))
            LiveState.Loading -> Unit
        }
    }

    private fun openSettingsIfUnconfigured() {
        lifecycleScope.launch {
            if (viewModel.needsSetup()) {
                startActivity(SettingsActivity.intent(this@LiveActivity, firstRun = true))
            }
        }
    }

    private fun openPlayer(match: Match) {
        startActivity(PlayerActivity.intent(this, match))
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
    }
}
