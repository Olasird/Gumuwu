package com.uwu.player

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class HorizontalContentAdapter(private var contentList: List<Content>) : RecyclerView.Adapter<HorizontalContentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.card_image)
        val itemView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.horizontal_content_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val content = contentList[position]
        holder.imageView.load(content.portadaUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
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