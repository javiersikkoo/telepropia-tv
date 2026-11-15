package es.telepropia.tv.ui.live

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import es.telepropia.tv.R
import es.telepropia.tv.data.model.LiveEvent
import java.util.Date

class EventAdapter(private val onOpen: (LiveEvent) -> Unit) : ListAdapter<EventRow, EventAdapter.Holder>(EventDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return Holder(view, onOpen)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(view: View, private val onOpen: (LiveEvent) -> Unit) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.event_name)
        private val sources: TextView = view.findViewById(R.id.event_sources)
        private val detected: TextView = view.findViewById(R.id.event_detected)

        fun bind(row: EventRow) {
            val context = itemView.context
            val event = row.event
            val time = DateFormat.getTimeFormat(context).format(Date(row.detectedAt))
            val liveCount = event.liveSources.size
            val sourcesText = if (event.hasLiveSource) {
                context.resources.getQuantityString(R.plurals.event_sources, liveCount, liveCount)
            } else {
                context.getString(R.string.event_no_live_sources)
            }

            name.text = event.name
            sources.text = sourcesText
            sources.setTextColor(
                ContextCompat.getColor(context, if (event.hasLiveSource) R.color.accent else R.color.warning)
            )
            detected.text = context.getString(R.string.live_detected, time)
            // Sigue siendo enfocable para que el mando no salte el hueco; solo deja de abrirse.
            itemView.alpha = if (event.hasLiveSource) 1f else DEAD_ALPHA
            itemView.contentDescription =
                context.getString(R.string.event_content_description, event.name, sourcesText, time)
            itemView.setOnClickListener { if (event.hasLiveSource) onOpen(event) }
        }
    }

    private companion object {
        const val DEAD_ALPHA = 0.5f
    }
}

private object EventDiff : DiffUtil.ItemCallback<EventRow>() {
    override fun areItemsTheSame(oldItem: EventRow, newItem: EventRow): Boolean = oldItem.event.key == newItem.event.key

    override fun areContentsTheSame(oldItem: EventRow, newItem: EventRow): Boolean = oldItem == newItem
}
