package com.guruswarupa.launch.apps.phone

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

/** Helpers for the standard Android contact requests (pick, view, add, edit) that other apps send. */
internal object ContactIntents {

    /** Works out which contact a content:// address from another app points at. */
    fun contactId(cr: ContentResolver, uri: Uri?): Long {
        uri ?: return -1L
        return try {
            val segs = uri.pathSegments
            when {
                segs.contains("lookup") -> ContactsContract.Contacts.lookupContact(cr, uri)?.let { ContentUris.parseId(it) } ?: -1L
                segs.firstOrNull() == "contacts" && segs.size == 2 -> ContentUris.parseId(uri)
                segs.firstOrNull() == "raw_contacts" && segs.size == 2 -> rawToContact(cr, ContentUris.parseId(uri))
                segs.firstOrNull() == "data" && segs.size >= 2 -> dataToContact(cr, ContentUris.parseId(uri))
                segs.firstOrNull() == "phone_lookup" && segs.size >= 2 -> phoneToContact(cr, Uri.decode(segs[1]))
                else -> -1L
            }
        } catch (_: Exception) { -1L }
    }

    private fun rawToContact(cr: ContentResolver, raw: Long): Long =
        cr.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.CONTACT_ID), "${ContactsContract.RawContacts._ID} = ?", arrayOf(raw.toString()), null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L

    private fun dataToContact(cr: ContentResolver, data: Long): Long =
        cr.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.CONTACT_ID), "${ContactsContract.Data._ID} = ?", arrayOf(data.toString()), null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L

    fun phoneToContact(cr: ContentResolver, number: String): Long =
        cr.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L

    /** Fills the contact editor from the extras apps put on an add-contact request. */
    fun prefill(intent: Intent): Triple<String?, String?, String?> {
        val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME) ?: intent.getStringExtra(ContactEditActivity.EXTRA_NAME)
        val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE) ?: intent.getStringExtra(ContactEditActivity.EXTRA_NUMBER)
            ?: intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let { Uri.decode(it) }
        val email = intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL) ?: intent.data?.takeIf { it.scheme == "mailto" }?.schemeSpecificPart
        return Triple(name, phone, email)
    }

    /** The data row to hand back for a "pick a phone number" or "pick an email" request. */
    fun phoneRows(cr: ContentResolver, contactId: Long): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        cr.query(CommonDataKinds.Phone.CONTENT_URI, arrayOf(CommonDataKinds.Phone._ID, CommonDataKinds.Phone.NUMBER), "${CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(contactId.toString()), null)
            ?.use { while (it.moveToNext()) out.add(it.getLong(0) to it.getString(1).orEmpty()) }
        return out
    }

    fun emailRows(cr: ContentResolver, contactId: Long): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        cr.query(CommonDataKinds.Email.CONTENT_URI, arrayOf(CommonDataKinds.Email._ID, CommonDataKinds.Email.ADDRESS), "${CommonDataKinds.Email.CONTACT_ID} = ?", arrayOf(contactId.toString()), null)
            ?.use { while (it.moveToNext()) out.add(it.getLong(0) to it.getString(1).orEmpty()) }
        return out
    }

    fun postalRows(cr: ContentResolver, contactId: Long): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        cr.query(CommonDataKinds.StructuredPostal.CONTENT_URI, arrayOf(CommonDataKinds.StructuredPostal._ID, CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS), "${CommonDataKinds.StructuredPostal.CONTACT_ID} = ?", arrayOf(contactId.toString()), null)
            ?.use { while (it.moveToNext()) out.add(it.getLong(0) to it.getString(1).orEmpty()) }
        return out
    }
}
