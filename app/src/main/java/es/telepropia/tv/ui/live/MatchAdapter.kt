package es.telepropia.tv.ui.live

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import es.telepropia.tv.R
import es.telepropia.tv.data.model.Match
import java.util.Date

class MatchAdapter(private val onPlay: (Match) -> Unit) : ListAdapter<LiveRow, MatchAdapter.Holder>(MatchDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)
        return Holder(view, onPlay)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(view: View, private val onPlay: (Match) -> Unit) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.match_name)
        private val detected: TextView = view.findViewById(R.id.match_detected)

        fun bind(row: LiveRow) {
            val context = itemView.context
            val time = DateFormat.getTimeFormat(context).format(Date(row.detectedAt))
            name.text = row.match.name
            detected.text = context.getString(R.string.live_detected, time)
            itemView.contentDescription = context.getString(R.string.match_content_description, row.match.name, time)
            itemView.setOnClickListener { onPlay(row.match) }
        }
    }
}

private object MatchDiff : DiffUtil.ItemCallback<LiveRow>() {
    override fun areItemsTheSame(oldItem: LiveRow, newItem: LiveRow): Boolean =
        oldItem.match.matchId == newItem.match.matchId

    override fun areContentsTheSame(oldItem: LiveRow, newItem: LiveRow): Boolean = oldItem == newItem
}
