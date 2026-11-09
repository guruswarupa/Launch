package com.guruswarupa.launch.apps.messages

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import com.guruswarupa.launch.apps.common.ContactNames
import android.telephony.PhoneNumberUtils

data class Conversation(val threadId: Long, val address: String, val snippet: String, val date: Long, val unread: Int, val lastType: Int)

data class Message(
    val id: Long, val address: String, val body: String, val date: Long, val type: Int, val read: Boolean,
    val mms: Boolean = false, val attachments: List<Attachment> = emptyList()
) {
    val outgoing: Boolean get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val sending: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

/** Reads and writes the phone's SMS store. Writing only works while Launch Messages is the default SMS app. */
object SmsRepo {

    fun isDefault(ctx: Context): Boolean = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun defaultRequestIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)

    fun contactName(ctx: Context, address: String): String? = ContactNames.lookup(ctx, address)

    fun clearNames() = ContactNames.clear()

    fun title(ctx: Context, address: String): String = contactName(ctx, address) ?: address

    fun conversations(ctx: Context, limit: Int = 4000): List<Conversation> {
        val order = LinkedHashMap<Long, Conversation>()
        val unread = HashMap<Long, Int>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ),
                null, null, "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    val thread = c.getLong(0)
                    if (c.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX && c.getInt(5) == 0) unread[thread] = (unread[thread] ?: 0) + 1
                    if (!order.containsKey(thread)) order[thread] = Conversation(thread, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), 0, c.getInt(4))
                }
            }
        } catch (_: Exception) {}
        // Picture messages count too: they can be the newest thing in a conversation.
        try {
            ctx.contentResolver.query(
                Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ),
                null, null, "${Telephony.Mms.DATE} DESC LIMIT 600"
            )?.use { c ->
                val seen = HashSet<Long>()
                while (c.moveToNext()) {
                    val id = c.getLong(0); val thread = c.getLong(1); val date = c.getLong(2) * 1000; val box = c.getInt(3)
                    val inbox = box == Telephony.Mms.MESSAGE_BOX_INBOX
                    if (inbox && c.getInt(4) == 0) unread[thread] = (unread[thread] ?: 0) + 1
                    if (!seen.add(thread)) continue
                    val existing = order[thread]
                    if (existing == null || date > existing.date) {
                        val (text, atts) = MmsStore.parts(ctx, id)
                        val snippet = if (text.isNotBlank()) text else when {
                            atts.any { it.isImage } -> ctx.getString(com.guruswarupa.launch.messages.R.string.msg_photo)
                            atts.any { it.isVideo } -> ctx.getString(com.guruswarupa.launch.messages.R.string.msg_video)
                            atts.any { it.isAudio } -> ctx.getString(com.guruswarupa.launch.messages.R.string.msg_audio)
                            else -> ctx.getString(com.guruswarupa.launch.messages.R.string.msg_picture_message)
                        }
                        val address = existing?.address?.takeIf { it.isNotBlank() } ?: MmsStore.otherAddress(ctx, id, inbox)
                        order[thread] = Conversation(thread, address, snippet, date, 0, if (inbox) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT)
                    }
                }
            }
        } catch (_: Exception) {}
        return order.values.sortedByDescending { it.date }.map { it.copy(unread = unread[it.threadId] ?: 0) }
    }

    fun messages(ctx: Context, threadId: Long): List<Message> {
        val out = ArrayList<Message>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) out.add(Message(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), c.getInt(4), c.getInt(5) == 1))
            }
        } catch (_: Exception) {}
        out.addAll(mmsMessages(ctx, threadId))
        out.sortBy { it.date }
        return out
    }

    private fun mmsMessages(ctx: Context, threadId: Long): List<Message> {
        val out = ArrayList<Message>()
        try {
            ctx.contentResolver.query(
                Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ),
                "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Mms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val box = c.getInt(2)
                    val inbox = box == Telephony.Mms.MESSAGE_BOX_INBOX
                    val (text, atts) = MmsStore.parts(ctx, id)
                    val type = when (box) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> Telephony.Sms.MESSAGE_TYPE_INBOX
                        Telephony.Mms.MESSAGE_BOX_OUTBOX -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        Telephony.Mms.MESSAGE_BOX_FAILED -> Telephony.Sms.MESSAGE_TYPE_FAILED
                        else -> Telephony.Sms.MESSAGE_TYPE_SENT
                    }
                    out.add(Message(id, "", text, c.getLong(1) * 1000, type, c.getInt(3) == 1, mms = true, attachments = atts))
                    if (!inbox) { /* sent boxes keep their type */ }
                }
            }
        } catch (_: Exception) {}
        return out
    }

    fun threadFor(ctx: Context, address: String): Long = try {
        Telephony.Threads.getOrCreateThreadId(ctx, address)
    } catch (_: Exception) { 0L }

    fun markRead(ctx: Context, threadId: Long) {
        if (!isDefault(ctx) || threadId == 0L) return
        try {
            ctx.contentResolver.update(Telephony.Mms.CONTENT_URI, ContentValues().apply { put(Telephony.Mms.READ, 1); put(Telephony.Mms.SEEN, 1) }, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
            val v = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
            ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, v, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        } catch (_: Exception) {}
    }

    fun deleteThread(ctx: Context, threadId: Long): Boolean = try {
        ctx.contentResolver.delete(Telephony.Mms.CONTENT_URI, "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        ctx.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString())) >= 0
    } catch (_: Exception) { false }

    fun deleteMessage(ctx: Context, id: Long): Boolean = try {
        ctx.contentResolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null) > 0
    } catch (_: Exception) { false }

    fun insertInbox(ctx: Context, address: String, body: String, date: Long): Uri? = try {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, date); put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v)
    } catch (_: Exception) { null }

    fun sameNumber(a: String, b: String): Boolean = try { PhoneNumberUtils.compare(a, b) } catch (_: Exception) { a == b }
}
