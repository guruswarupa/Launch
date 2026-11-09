package com.guruswarupa.launch.apps.phone

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telephony.PhoneNumberUtils

class Person(val id: Long, val name: String, val numbers: List<String>, val starred: Boolean, val photo: String?, val lookup: String?) {
    val t9: String = name.lowercase().map { ContactStore.letterDigit(it) }.joinToString("")
    val words: List<String> = name.lowercase().split(' ', '-', '.').filter { it.isNotEmpty() }.map { w -> w.map { ContactStore.letterDigit(it) }.joinToString("") }
}

/** What the contact editor can change. [rawId] is null for a new contact. */
class ContactDraft(
    val rawId: Long?, val first: String, val last: String, val company: String, val notes: String,
    val phones: List<Pair<Int, String>>, val emails: List<Pair<Int, String>>, val account: Pair<String?, String?>?
)

object ContactStore {

    private val letterMap: Map<Char, Char> = buildMap {
        "abc".forEach { put(it, '2') }; "def".forEach { put(it, '3') }; "ghi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }; "mno".forEach { put(it, '6') }; "pqrs".forEach { put(it, '7') }
        "tuv".forEach { put(it, '8') }; "wxyz".forEach { put(it, '9') }
    }

    fun letterDigit(c: Char): Char = if (c.isDigit()) c else letterMap[c] ?: '0'

    fun load(cr: ContentResolver): List<Person> {
        val map = LinkedHashMap<Long, MutableList<String>>()
        val meta = HashMap<Long, Array<String?>>()
        val starred = HashSet<Long>()
        try {
            cr.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.DISPLAY_NAME, CommonDataKinds.Phone.NUMBER,
                    CommonDataKinds.Phone.STARRED, CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI, CommonDataKinds.Phone.LOOKUP_KEY
                ), null, null, "${CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(2) ?: continue
                    val list = map.getOrPut(id) { ArrayList() }
                    if (list.none { PhoneNumberUtils.compare(it, number) }) list.add(number)
                    meta.getOrPut(id) { arrayOf(c.getString(1), c.getString(4), c.getString(5)) }
                    if (c.getInt(3) == 1) starred.add(id)
                }
            }
        } catch (_: Exception) {}
        return map.map { (id, nums) ->
            val m = meta[id]!!
            Person(id, m[0].orEmpty().ifBlank { nums.first() }, nums, id in starred, m[1], m[2])
        }
    }

    fun setStarred(cr: ContentResolver, contactId: Long, starred: Boolean): Boolean = try {
        val v = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        cr.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), v, null, null) > 0
    } catch (_: Exception) { false }

    fun delete(cr: ContentResolver, contactId: Long): Boolean = try {
        cr.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), null, null) > 0
    } catch (_: Exception) { false }

    /** Accounts that already hold contacts (e.g. Google or the phone), so new contacts can be saved to them. */
    fun accounts(cr: ContentResolver): List<Pair<String?, String?>> {
        val out = LinkedHashSet<Pair<String?, String?>>()
        try {
            cr.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.DELETED} = 0", null, null)?.use { c ->
                while (c.moveToNext()) out.add(c.getString(0) to c.getString(1))
            }
        } catch (_: Exception) {}
        return out.toList()
    }

    fun firstRawId(cr: ContentResolver, contactId: Long): Long? = try {
        cr.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0", arrayOf(contactId.toString()), "${ContactsContract.RawContacts._ID} ASC")
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    } catch (_: Exception) { null }

    fun load(cr: ContentResolver, rawId: Long): ContactDraft? {
        var first = ""; var last = ""; var company = ""; var notes = ""
        val phones = ArrayList<Pair<Int, String>>(); val emails = ArrayList<Pair<Int, String>>()
        try {
            cr.query(ContactsContract.Data.CONTENT_URI, null, "${ContactsContract.Data.RAW_CONTACT_ID} = ?", arrayOf(rawId.toString()), null)?.use { c ->
                val mime = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val d1 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
                val d2 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
                val d3 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA3)
                while (c.moveToNext()) {
                    when (c.getString(mime)) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> { first = c.getString(d2).orEmpty(); last = c.getString(d3).orEmpty()
                            if (first.isEmpty() && last.isEmpty()) first = c.getString(d1).orEmpty() }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> c.getString(d1)?.takeIf { it.isNotBlank() }?.let { phones.add(c.getInt(d2) to it) }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> c.getString(d1)?.takeIf { it.isNotBlank() }?.let { emails.add(c.getInt(d2) to it) }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> company = c.getString(d1).orEmpty()
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> notes = c.getString(d1).orEmpty()
                    }
                }
            }
        } catch (_: Exception) { return null }
        return ContactDraft(rawId, first, last, company, notes, phones, emails, null)
    }

    fun save(cr: ContentResolver, d: ContactDraft): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        val existing = d.rawId != null
        if (existing) {
            val mimes = arrayOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                CommonDataKinds.Organization.CONTENT_ITEM_TYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            mimes.forEach { m ->
                ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?", arrayOf(d.rawId.toString(), m)).build())
            }
        } else {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, d.account?.second)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, d.account?.first).build())
        }
        fun data(mime: String, build: ContentProviderOperation.Builder.() -> Unit) {
            val b = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            if (existing) b.withValue(ContactsContract.Data.RAW_CONTACT_ID, d.rawId) else b.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            b.withValue(ContactsContract.Data.MIMETYPE, mime)
            b.apply(build)
            ops.add(b.build())
        }
        if (d.first.isNotBlank() || d.last.isNotBlank()) data(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
            withValue(CommonDataKinds.StructuredName.GIVEN_NAME, d.first.trim()); withValue(CommonDataKinds.StructuredName.FAMILY_NAME, d.last.trim())
        }
        d.phones.filter { it.second.isNotBlank() }.forEach { (type, number) ->
            data(CommonDataKinds.Phone.CONTENT_ITEM_TYPE) { withValue(CommonDataKinds.Phone.NUMBER, number.trim()); withValue(CommonDataKinds.Phone.TYPE, type) }
        }
        d.emails.filter { it.second.isNotBlank() }.forEach { (type, address) ->
            data(CommonDataKinds.Email.CONTENT_ITEM_TYPE) { withValue(CommonDataKinds.Email.ADDRESS, address.trim()); withValue(CommonDataKinds.Email.TYPE, type) }
        }
        if (d.company.isNotBlank()) data(CommonDataKinds.Organization.CONTENT_ITEM_TYPE) { withValue(CommonDataKinds.Organization.COMPANY, d.company.trim()) }
        if (d.notes.isNotBlank()) data(CommonDataKinds.Note.CONTENT_ITEM_TYPE) { withValue(CommonDataKinds.Note.NOTE, d.notes.trim()) }
        return try { cr.applyBatch(ContactsContract.AUTHORITY, ops); true } catch (_: Exception) { false }
    }
}
