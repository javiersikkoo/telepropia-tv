package es.telepropia.tv.ui.sources

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.telepropia.tv.R
import es.telepropia.tv.data.model.LiveEvent
import es.telepropia.tv.data.model.Sports
import es.telepropia.tv.ui.player.PlayerActivity

/**
 * Fuentes vivas de un evento. Las de hosts caídos no se listan: §9 los excluye y nunca se resuelve contra ellos.
 */
class SourcesActivity : AppCompatActivity() {

    private data class SourceItem(val hostId: Long, val matchId: String, val sourceName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val sportId = intent.getIntExtra(EXTRA_SPORT_ID, Sports.DEFAULT_ID)
        val hostIds = intent.getLongArrayExtra(EXTRA_HOST_IDS) ?: LongArray(0)
        val matchIds = intent.getStringArrayExtra(EXTRA_MATCH_IDS) ?: emptyArray()
        val sourceNames = intent.getStringArrayExtra(EXTRA_SOURCE_NAMES) ?: emptyArray()
        val items = hostIds.indices.map { SourceItem(hostIds[it], matchIds[it], sourceNames[it]) }

        findViewById<TextView>(R.id.sources_title).text = name
        val list: RecyclerView = findViewById(R.id.source_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = SourceAdapter(items) { item ->
            startActivity(PlayerActivity.intent(this, item.hostId, item.matchId, sportId, name))
        }
        list.post { list.getChildAt(0)?.requestFocus() }
    }

    private class SourceAdapter(
        private val items: List<SourceItem>,
        private val onPick: (SourceItem) -> Unit
    ) : RecyclerView.Adapter<SourceAdapter.Holder>() {

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            holder.name.text = context.getString(R.string.source_label, position + 1, item.sourceName)
            holder.itemView.contentDescription =
                context.getString(R.string.source_content_description, position + 1, item.sourceName)
            holder.itemView.setOnClickListener { onPick(item) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.source_name)
        }
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_SPORT_ID = "sport_id"
        private const val EXTRA_HOST_IDS = "host_ids"
        private const val EXTRA_MATCH_IDS = "match_ids"
        private const val EXTRA_SOURCE_NAMES = "source_names"

        fun intent(context: Context, event: LiveEvent): Intent {
            val live = event.liveSources
            return Intent(context, SourcesActivity::class.java)
                .putExtra(EXTRA_NAME, event.name)
                .putExtra(EXTRA_SPORT_ID, event.sportId)
                .putExtra(EXTRA_HOST_IDS, live.map { it.sourceHostId }.toLongArray())
                .putExtra(EXTRA_MATCH_IDS, live.map { it.matchId }.toTypedArray())
                .putExtra(EXTRA_SOURCE_NAMES, live.map { it.sourceName }.toTypedArray())
        }
    }
}
