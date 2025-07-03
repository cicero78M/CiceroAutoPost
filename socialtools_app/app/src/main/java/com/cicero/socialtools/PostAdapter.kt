package com.cicero.socialtools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PostAdapter(private var items: List<InstagramGraphApi.Post>) : RecyclerView.Adapter<PostAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image_post)
        val caption: TextView = view.findViewById(R.id.text_caption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val post = items[position]
        Glide.with(holder.itemView).load(post.mediaUrl).into(holder.image)
        holder.caption.text = post.caption ?: ""
    }

    fun update(data: List<InstagramGraphApi.Post>) {
        items = data
        notifyDataSetChanged()
    }
}
