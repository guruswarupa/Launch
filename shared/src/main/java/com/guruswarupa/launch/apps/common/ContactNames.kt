package com.guruswarupa.launch.apps.common

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** Looks up a contact's name from a phone number, with a small cache. */
object ContactNames {
    private val names = HashMap<String, String?>()

    fun lookup(ctx: Context, address: String): String? {
        if (address.isBlank() || address.any { it.isLetter() }) return null
        synchronized(names) { if (names.containsKey(address)) return names[address] }
        val name = try {
            ctx.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
        synchronized(names) { names[address] = name }
        return name
    }

    fun clear() { synchronized(names) { names.clear() } }
}
