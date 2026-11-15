package es.telepropia.tv.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.telepropia.tv.R
import es.telepropia.tv.appContainer
import es.telepropia.tv.data.model.SourceHost
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(appContainer()) }

    private lateinit var input: EditText
    private lateinit var labelInput: EditText
    private lateinit var status: TextView
    private lateinit var emptyHosts: TextView

    private val adapter = HostAdapter(
        object : HostAdapter.Actions {
            override fun onProbe(host: SourceHost) = viewModel.probe(host)
            override fun onToggleState(host: SourceHost) = viewModel.toggleState(host)
            override fun onEditLabel(host: SourceHost) = editLabel(host)
            override fun onMove(host: SourceHost, delta: Int) = viewModel.move(host, delta)
            override fun onDelete(host: SourceHost) = viewModel.delete(host)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        input = findViewById(R.id.host_input)
        labelInput = findViewById(R.id.label_input)
        status = findViewById(R.id.status_text)
        emptyHosts = findViewById(R.id.empty_hosts)
        findViewById<TextView>(R.id.first_run_hint).isVisible = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

        val list: RecyclerView = findViewById(R.id.host_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null

        findViewById<TextView>(R.id.add_button).setOnClickListener { submit() }
        labelInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rows.collect { rows ->
                        adapter.submitList(rows)
                        emptyHosts.isVisible = rows.isEmpty()
                    }
                }
                launch {
                    viewModel.feedback.collect { feedback ->
                        if (feedback == null) {
                            status.text = ""
                        } else {
                            status.setText(feedback.textRes)
                            if (feedback.clearInput) {
                                input.text.clear()
                                labelInput.text.clear()
                                viewModel.onFeedbackShown()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun submit() {
        viewModel.add(input.text.toString(), labelInput.text.toString())
    }

    private fun editLabel(host: SourceHost) {
        val field = EditText(this).apply {
            setText(host.label.orEmpty())
            setSelection(text.length)
            setSingleLine()
            hint = host.baseUrl.substringAfter("://")
        }
        val margin = resources.getDimensionPixelSize(R.dimen.overscan_v)
        val container = FrameLayout(this).apply {
            setPadding(margin, margin / 2, margin, 0)
            addView(field)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.label_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.label_dialog_save) { _, _ -> viewModel.setLabel(host, field.text.toString()) }
            .setNegativeButton(R.string.label_dialog_cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_FIRST_RUN = "first_run"

        fun intent(context: Context, firstRun: Boolean): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_FIRST_RUN, firstRun)
    }
}
