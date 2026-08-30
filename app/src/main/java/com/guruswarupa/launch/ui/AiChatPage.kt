package com.guruswarupa.launch.ui

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.adapters.AiChatAdapter
import com.guruswarupa.launch.adapters.ChatMessage
import com.guruswarupa.launch.ai.llm.AssistantResult
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import kotlinx.coroutines.launch

/**
 * The AI chat page in the home-screen pager (see ScreenPagerManager.Page.AI_CHAT) — a
 * dedicated conversational screen, distinct from the one-shot "Ask AI" search result.
 * Chat history is kept in memory only for this MainActivity instance; nothing is persisted.
 */
class AiChatPage(
    private val activity: MainActivity,
    private val rootView: View
) {
    private val recyclerView: RecyclerView = rootView.findViewById(R.id.ai_chat_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.ai_chat_empty_state)
    private val emptyTitle: TextView = rootView.findViewById(R.id.ai_chat_empty_title)
    private val emptyMessage: TextView = rootView.findViewById(R.id.ai_chat_empty_message)
    private val inputField: EditText = rootView.findViewById(R.id.ai_chat_input)
    private val sendButton: ImageButton = rootView.findViewById(R.id.ai_chat_send_button)
    private val clearButton: ImageButton = rootView.findViewById(R.id.ai_chat_clear_button)
    private val contentContainer: View = rootView.findViewById(R.id.ai_chat_content_container)

    private val adapter = AiChatAdapter()
    private var isWaitingForResponse = false

    fun setup() {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        setupKeyboardAvoidance()

        sendButton.setOnClickListener { trySend() }
        inputField.setOnEditorActionListener { _, actionId, event ->
            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSendAction) {
                trySend()
                true
            } else {
                false
            }
        }

        clearButton.setOnClickListener {
            adapter.clear()
            refreshAvailability()
        }

        updateTypography()
        refreshAvailability()
    }

    /** Re-checks whether the model is ready. Called whenever this page becomes visible, since the model may finish downloading while the user is elsewhere. */
    fun onPageShown() {
        refreshAvailability()
    }

    /** Applies the user's configured font scale/style/intensity/color — same mechanism RssFeedPage uses — so this page matches the News/Widgets pages instead of looking themed differently. */
    fun updateTypography() {
        val prefs = activity.sharedPreferences
        val scale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100) / 100f
        val style = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val intensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
        val color = TypographyManager.getConfiguredFontColor(activity)

        adapter.updateTypography(scale, style, intensity, color)
        TypographyManager.applyToViewTree(contentContainer, scale, style, intensity, color)
    }

    /** Pushes the input bar above the on-screen keyboard by shrinking the content column with bottom padding equal to the IME's height, instead of relying on windowSoftInputMode (which would resize every page in the shared pager, not just this one). */
    private fun setupKeyboardAvoidance() {
        val initialBottomPadding = contentContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                initialBottomPadding + imeBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun trySend() {
        val text = inputField.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || isWaitingForResponse || !activity.onDeviceAssistant.isModelReady) return

        inputField.text?.clear()
        emptyState.isVisible = false
        adapter.addMessage(ChatMessage(text, isUser = true))
        val thinkingIndex = adapter.addMessage(ChatMessage(activity.getString(R.string.ask_ai_thinking), isUser = false))
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        setWaiting(true)

        activity.lifecycleScope.launch {
            val result = activity.onDeviceAssistant.ask(text)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            val answer = when (result) {
                is AssistantResult.Success -> result.text
                is AssistantResult.Error -> activity.getString(R.string.ask_ai_error, result.message)
            }
            adapter.replaceMessageAt(thinkingIndex, ChatMessage(answer, isUser = false))
            recyclerView.scrollToPosition(adapter.itemCount - 1)
            setWaiting(false)
        }
    }

    private fun setWaiting(waiting: Boolean) {
        isWaitingForResponse = waiting
        sendButton.isEnabled = !waiting && activity.onDeviceAssistant.isModelReady
    }

    private fun refreshAvailability() {
        val ready = activity.onDeviceAssistant.isModelReady
        inputField.isEnabled = ready
        sendButton.isEnabled = ready && !isWaitingForResponse

        if (adapter.itemCount == 0) {
            emptyState.isVisible = true
            if (ready) {
                emptyTitle.text = activity.getString(R.string.ai_chat_empty_title)
                emptyMessage.text = activity.getString(R.string.ai_chat_empty_message)
            } else {
                emptyTitle.text = activity.getString(R.string.ai_chat_not_ready_title)
                emptyMessage.text = activity.getString(R.string.ai_chat_not_ready_message)
            }
        } else {
            emptyState.isVisible = false
        }
    }
}
