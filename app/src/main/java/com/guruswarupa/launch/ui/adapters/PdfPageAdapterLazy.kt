package com.guruswarupa.launch.ui.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class PdfPageAdapterLazy(
    private val pdfRenderer: PdfRenderer,
    private val totalPages: Int
) : RecyclerView.Adapter<PdfPageAdapterLazy.PageViewHolder>() {

    // Use LruCache with memory-size based eviction instead of unbounded ConcurrentHashMap
    private val maxMemory = Runtime.getRuntime().maxMemory() / 1024
    private val cacheSize = (maxMemory / 8).toInt() // Use 1/8th of available memory
    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }
    
    private val renderExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val pendingTasks = ConcurrentHashMap<Int, Future<*>>()

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.pdf_page_image)
        val pageNumber: TextView = view.findViewById(R.id.pdf_page_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {

        holder.pageNumber.text = "Page ${position + 1}"


        val cachedBitmap = bitmapCache.get(position)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            holder.imageView.setImageBitmap(cachedBitmap)
            return
        }


        pendingTasks[position]?.cancel(true)


        val future = renderExecutor.submit {
            try {
                pdfRenderer.openPage(position).use { page ->
                    val scale = 2.0f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    bitmapCache.put(position, bitmap)

                    holder.imageView.post {
                        if (holder.bindingAdapterPosition == position && !bitmap.isRecycled) {
                            holder.imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                holder.imageView.post {
                    if (holder.bindingAdapterPosition == position) {
                        holder.imageView.setImageResource(android.R.drawable.ic_dialog_alert)
                        holder.pageNumber.text = "Error loading page ${position + 1}"
                    }
                }
            } finally {
                pendingTasks.remove(position)
            }
        }

        pendingTasks[position] = future
    }

    override fun getItemCount() = totalPages

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.imageView.setImageBitmap(null)
    }

    fun clearCache() {
        bitmapCache.evictAll()
        pendingTasks.values.forEach { it.cancel(true) }
        pendingTasks.clear()
        renderExecutor.shutdownNow()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        clearCache()
    }
}
