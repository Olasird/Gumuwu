package com.uwu.player

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.util.Locale

class ContentAdapter(private var contentList: List<Content>) : RecyclerView.Adapter<ContentAdapter.ContentViewHolder>() {

    class ContentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.card_image)
        val titleView: TextView = view.findViewById(R.id.card_title)
        val labelView: TextView = view.findViewById(R.id.card_label)
        val seasonView: TextView = view.findViewById(R.id.card_season)
        val yearView: TextView = view.findViewById(R.id.card_year) // Referencia al año
        val itemView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.content_card, parent, false)
        return ContentViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        val content = contentList[position]
        holder.titleView.text = content.titulo
        holder.imageView.load(content.portadaUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
        }

        // Lógica para la etiqueta de año
        if (content.año > 0) {
            holder.yearView.visibility = View.VISIBLE
            holder.yearView.text = content.año.toString()
        } else {
            holder.yearView.visibility = View.GONE
        }

        if (content.tipo == "serie") {
            holder.labelView.text = "SERIE"
            holder.labelView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.blue_700))

            if (content.temporada > 0) {
                holder.seasonView.visibility = View.VISIBLE
                holder.seasonView.text = content.temporada.toString()
            } else {
                holder.seasonView.visibility = View.GONE
            }
        } else {
            holder.labelView.text = "PELÍCULA"
            holder.labelView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.red_500))
            holder.seasonView.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = if (content.tipo == "pelicula") {
                Intent(context, MovieDetailActivity::class.java)
            } else {
                Intent(context, SeriesDetailActivity::class.java)
            }
            intent.putExtra("CONTENT_EXTRA", content)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = contentList.size
}