package com.uwu.player

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(
    private val episodes: List<Episode>,
    private val seriesId: String
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.episode_number)
        val title: TextView = view.findViewById(R.id.episode_title)
        val itemView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.episode_item, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episode = episodes[position]
        holder.number.text = "${episode.numeroEpisodio}."
        holder.title.text = episode.titulo

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, EpisodePlayerActivity::class.java).apply {
                // CAMBIO: Enviamos la lista como un ArrayList, que es una forma concreta y segura.
                putExtra("EPISODES_LIST", ArrayList(episodes))
                putExtra("CURRENT_EPISODE_INDEX", position)
                putExtra("SERIES_ID", seriesId)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = episodes.size
}