package com.guruswarupa.launch.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.RssArticle
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.ui.activities.WebAppActivity
import java.text.DateFormat
import java.util.Date

class RssFeedAdapter : RecyclerView.Adapter<RssFeedAdapter.RssArticleViewHolder>() {

    private var fontScale = 1.0f
    private var fontStyle = "default"
    private var fontIntensity = "regular"
    private var fontColor: Int? = null

    fun updateTypography(scale: Float, style: String, intensity: String, color: Int?) {
        fontScale = scale
        fontStyle = style
        fontIntensity = intensity
        fontColor = color
        notifyDataSetChanged()
    }

    private val differCallback = object : DiffUtil.ItemCallback<RssArticle>() {
        override fun areItemsTheSame(oldItem: RssArticle, newItem: RssArticle): Boolean {
            return oldItem.link == newItem.link
        }

        override fun areContentsTheSame(oldItem: RssArticle, newItem: RssArticle): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, differCallback)

    val articles: List<RssArticle> get() = differ.currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RssArticleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rss_article, parent, false)
        return RssArticleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RssArticleViewHolder, position: Int) {
        TypographyManager.applyToViewTree(holder.itemView, fontScale, fontStyle, fontIntensity, fontColor)
        holder.bind(differ.currentList[position])
    }

    override fun getItemCount(): Int = differ.currentList.size

    fun submitArticles(items: List<RssArticle>) {
        differ.submitList(items)
    }

    class RssArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail = itemView.findViewById<ImageView>(R.id.article_thumbnail)
        private val title = itemView.findViewById<TextView>(R.id.article_title)
        private val meta = itemView.findViewById<TextView>(R.id.article_meta)
        private val description = itemView.findViewById<TextView>(R.id.article_description)

        fun bind(article: RssArticle) {
            title.text = article.title
            val dateLabel = if (article.pubDate > 0L) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(article.pubDate))
            } else {
                "Latest"
            }
            meta.text = "${article.source}  •  $dateLabel"
            description.text = article.description.ifBlank { article.link }

            if (article.imageUrl.isNullOrBlank()) {
                thumbnail.visibility = View.GONE
                Glide.with(itemView).clear(thumbnail)
            } else {
                thumbnail.visibility = View.VISIBLE
                Glide.with(itemView)
                    .load(article.imageUrl)
                    .centerCrop()
                    .into(thumbnail)
            }

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, WebAppActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                    putExtra(WebAppActivity.EXTRA_WEB_APP_NAME, article.title.ifBlank { article.source })
                    putExtra(WebAppActivity.EXTRA_WEB_APP_URL, article.link)

                    putExtra(WebAppActivity.EXTRA_BLOCK_REDIRECTS, true)
                }
                itemView.context.startActivity(intent)
            }
        }
    }
}
