package com.guruswarupa.launch.ui.activities

import android.content.Context

/** Remembers where each video was left so it can pick up from there next time. */
object VideoResume {
    private const val PREFS = "video_resume"
    private const val MAX_ITEMS = 60

    data class Entry(val id: String, val position: Long, val duration: Long, val updated: Long, val name: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Positions near the start or the end are not worth resuming, so those clear the entry. */
    private fun worthResuming(position: Long, duration: Long) =
        position >= 5_000L && (duration <= 0L || position <= duration - 10_000L)

    fun save(ctx: Context, id: String, name: String, position: Long, duration: Long) {
        val p = prefs(ctx)
        if (!worthResuming(position, duration)) {
            if (p.contains(id)) p.edit().remove(id).apply()
            return
        }
        val edit = p.edit()
        edit.putString(id, "$position|$duration|${System.currentTimeMillis()}|$name")
        val all = p.all
        if (all.size >= MAX_ITEMS) {
            all.entries.mapNotNull { e -> parse(e.key, e.value as? String)?.let { it } }
                .sortedBy { it.updated }.take(all.size - MAX_ITEMS + 1).forEach { edit.remove(it.id) }
        }
        edit.apply()
    }

    fun get(ctx: Context, id: String): Entry? = parse(id, prefs(ctx).getString(id, null))

    fun clear(ctx: Context, id: String) { prefs(ctx).edit().remove(id).apply() }

    fun recent(ctx: Context, limit: Int = 10): List<Entry> =
        prefs(ctx).all.mapNotNull { (k, v) -> parse(k, v as? String) }.sortedByDescending { it.updated }.take(limit)

    private fun parse(id: String, raw: String?): Entry? {
        raw ?: return null
        val parts = raw.split("|", limit = 4)
        if (parts.size < 4) return null
        return Entry(id, parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: 0L, parts[2].toLongOrNull() ?: 0L, parts[3])
    }
}
