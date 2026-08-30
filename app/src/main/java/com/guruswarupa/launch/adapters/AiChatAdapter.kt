package com.guruswarupa.launch.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.TypographyManager

data class ChatMessage(val text: String, val isUser: Boolean)

class AiChatAdapter : RecyclerView.Adapter<AiChatAdapter.ChatMessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

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

    fun addMessage(message: ChatMessage): Int {
        messages.add(message)
        val index = messages.lastIndex
        notifyItemInserted(index)
        return index
    }

    fun replaceMessageAt(index: Int, message: ChatMessage) {
        if (index !in messages.indices) return
        messages[index] = message
        notifyItemChanged(index)
    }

    fun clear() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatMessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_chat_message, parent, false)
        return ChatMessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatMessageViewHolder, position: Int) {
        holder.bind(messages[position])
        TypographyManager.applyToViewTree(holder.itemView, fontScale, fontStyle, fontIntensity, fontColor)
    }

    class ChatMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: TextView = itemView.findViewById(R.id.chat_message_bubble)

        fun bind(message: ChatMessage) {
            bubble.text = message.text
            bubble.setBackgroundResource(
                if (message.isUser) R.drawable.bg_chat_bubble_user else R.drawable.bg_chat_bubble_assistant
            )
            val params = bubble.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (message.isUser) Gravity.END else Gravity.START
            bubble.layoutParams = params
        }
    }
}
