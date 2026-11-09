package com.guruswarupa.launch.apps.phone

import android.content.Context

/** Small lookup used by other Launch apps, such as Messages, to pick a recipient. */
object PhoneContacts {
    data class Entry(val name: String, val number: String)

    fun search(ctx: Context, query: String): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter { it.isDigit() }
        val out = ArrayList<Entry>()
        for (person in ContactStore.load(ctx.contentResolver)) {
            val nameMatch = person.name.lowercase().contains(q)
            person.numbers.forEach { n ->
                if (nameMatch || (digits.length >= 2 && n.filter { it.isDigit() }.contains(digits))) out.add(Entry(person.name, n))
            }
            if (out.size >= 12) break
        }
        return out
    }
}
