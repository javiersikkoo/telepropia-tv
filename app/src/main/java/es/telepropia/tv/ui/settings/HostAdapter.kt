package es.telepropia.tv.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import es.telepropia.tv.R
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.SourceHost
import es.telepropia.tv.ui.messageRes

class HostAdapter(private val actions: Actions) : ListAdapter<HostRow, HostAdapter.Holder>(HostDiff) {

    interface Actions {
        fun onProbe(host: SourceHost)
        fun onToggleState(host: SourceHost)
        fun onEditLabel(host: SourceHost)
        fun onMove(host: SourceHost, delta: Int)
        fun onDelete(host: SourceHost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_host, parent, false)
        return Holder(view, actions)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    class Holder(view: View, private val actions: Actions) : RecyclerView.ViewHolder(view) {
        private val position: TextView = view.findViewById(R.id.host_position)
        private val name: TextView = view.findViewById(R.id.host_name)
        private val url: TextView = view.findViewById(R.id.host_url)
        private val state: TextView = view.findViewById(R.id.host_state)
        private val probeResult: TextView = view.findViewById(R.id.probe_result)
        private val probeButton: Button = view.findViewById(R.id.probe_button)
        private val stateButton: Button = view.findViewById(R.id.state_button)
        private val labelButton: Button = view.findViewById(R.id.label_button)
        private val upButton: Button = view.findViewById(R.id.up_button)
        private val downButton: Button = view.findViewById(R.id.down_button)
        private val deleteButton: Button = view.findViewById(R.id.delete_button)

        fun bind(row: HostRow, index: Int, count: Int) {
            val host = row.host
            val context = itemView.context
            val active = host.state == HostState.ACTIVE
            val failing = active && row.status.failure != null

            position.text = context.getString(R.string.host_position, index + 1)
            name.text = row.displayName
            url.text = host.baseUrl
            url.isVisible = row.displayName != host.baseUrl.substringAfter("://")
            state.setText(
                when {
                    !active -> R.string.host_state_paused
                    failing -> R.string.host_state_failing
                    else -> R.string.host_state_active
                }
            )
            state.setTextColor(
                ContextCompat.getColor(context, if (active && !failing) R.color.accent else R.color.warning)
            )
            stateButton.setText(if (active) R.string.host_pause else R.string.host_resume)
            // Visibles siempre: ocultar el botón con foco haría saltar el mando a otra fila.
            upButton.alpha = if (index > 0) 1f else DISABLED_ALPHA
            downButton.alpha = if (index < count - 1) 1f else DISABLED_ALPHA

            probeResult.isVisible = row.probe !is ProbeUi.Idle
            probeResult.text = when (val probe = row.probe) {
                ProbeUi.Idle -> ""
                ProbeUi.Running -> context.getString(R.string.probe_running)
                is ProbeUi.Done -> {
                    val failure = probe.health.failure
                    if (probe.health.ok || failure == null) {
                        context.getString(R.string.probe_ok, probe.health.latencyMs)
                    } else {
                        context.getString(failure.messageRes(R.string.error_list_other))
                    }
                }
            }

            val who = row.displayName
            probeButton.contentDescription = context.getString(R.string.cd_host_probe, who)
            stateButton.contentDescription =
                context.getString(if (active) R.string.cd_host_pause else R.string.cd_host_resume, who)
            labelButton.contentDescription = context.getString(R.string.cd_host_label, who)
            upButton.contentDescription = context.getString(R.string.cd_host_up, who)
            downButton.contentDescription = context.getString(R.string.cd_host_down, who)
            deleteButton.contentDescription = context.getString(R.string.cd_host_delete, who)

            probeButton.setOnClickListener { actions.onProbe(host) }
            stateButton.setOnClickListener { actions.onToggleState(host) }
            labelButton.setOnClickListener { actions.onEditLabel(host) }
            upButton.setOnClickListener { actions.onMove(host, -1) }
            downButton.setOnClickListener { actions.onMove(host, +1) }
            deleteButton.setOnClickListener { actions.onDelete(host) }
        }

        private companion object {
            const val DISABLED_ALPHA = 0.4f
        }
    }
}

private object HostDiff : DiffUtil.ItemCallback<HostRow>() {
    override fun areItemsTheSame(oldItem: HostRow, newItem: HostRow): Boolean = oldItem.host.id == newItem.host.id

    override fun areContentsTheSame(oldItem: HostRow, newItem: HostRow): Boolean = oldItem == newItem
}
