package es.telepropia.tv.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import es.telepropia.tv.R
import es.telepropia.tv.appContainer
import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.data.model.Sports
import es.telepropia.tv.ui.messageRes
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.factory(
            appContainer(),
            intent.getLongExtra(EXTRA_HOST_ID, NO_HOST),
            requireNotNull(intent.getStringExtra(EXTRA_MATCH_ID)),
            intent.getIntExtra(EXTRA_SPORT_ID, Sports.DEFAULT_ID)
        )
    }

    private lateinit var playerView: PlayerView
    private lateinit var buffering: View
    private lateinit var status: TextView
    private lateinit var errorPanel: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private var player: ExoPlayer? = null
    private var loadedToken = NO_TOKEN

    // Sin cabeceras propias: el proxy del resolver ya inyecta el referer.
    private val mediaSourceFactory by lazy {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(TIMEOUT_MS)
            .setReadTimeoutMs(TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        HlsMediaSource.Factory(http)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering.isVisible = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) status.isVisible = false
            if (playbackState == Player.STATE_ENDED) viewModel.onStreamEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            // Quedarse detrás de la ventana en directo no es un fallo: se vuelve al borde.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player?.run {
                    seekToDefaultPosition()
                    prepare()
                }
                return
            }
            viewModel.onPlaybackFailed(PlaybackErrorMapper.map(error))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        playerView = findViewById(R.id.player_view)
        buffering = findViewById(R.id.buffering)
        status = findViewById(R.id.status_text)
        errorPanel = findViewById(R.id.error_panel)
        errorText = findViewById(R.id.error_text)
        retryButton = findViewById(R.id.retry_button)

        retryButton.setOnClickListener { viewModel.retry() }
        onBackPressedDispatcher.addCallback(this) { finish() }

        player = ExoPlayer.Builder(this).build().also {
            it.addListener(listener)
            it.playWhenReady = true
            playerView.player = it
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    // Al volver la URL podría haber caducado; se cierra y el siguiente play re-resuelve.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun render(state: PlayerState) {
        when (state) {
            is PlayerState.Resolving -> {
                errorPanel.isVisible = false
                buffering.isVisible = true
                status.setText(if (state.retrying) R.string.player_retrying else R.string.player_resolving)
                status.isVisible = true
            }
            is PlayerState.Ready -> {
                errorPanel.isVisible = false
                if (state.token != loadedToken) {
                    loadedToken = state.token
                    play(state.spec)
                }
            }
            is PlayerState.Error -> showError(state.failure)
        }
    }

    private fun play(spec: PlaybackSpec) {
        val current = player ?: return
        current.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(spec.url)))
        current.prepare()
    }

    private fun showError(failure: Failure) {
        player?.stop()
        buffering.isVisible = false
        status.isVisible = false
        errorText.setText(failure.messageRes())
        errorPanel.isVisible = true
        retryButton.requestFocus()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).run {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val EXTRA_HOST_ID = "host_id"
        private const val EXTRA_MATCH_ID = "match_id"
        private const val EXTRA_SPORT_ID = "sport_id"
        private const val NO_TOKEN = -1
        private const val NO_HOST = -1L
        private const val TIMEOUT_MS = 8_000
        private const val LOAD_RETRIES = 2

        fun intent(context: Context, hostId: Long, matchId: String, sportId: Int): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_HOST_ID, hostId)
                .putExtra(EXTRA_MATCH_ID, matchId)
                .putExtra(EXTRA_SPORT_ID, sportId)
    }
}
