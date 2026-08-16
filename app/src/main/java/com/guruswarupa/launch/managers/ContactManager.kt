package com.guruswarupa.launch.managers

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor





class ContactManager(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val backgroundExecutor: Executor
) {
    private val contactsList: MutableList<String> = mutableListOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var contactsLoaded = false
    @Volatile
    private var contactsLoading = false





    /** Guarantees [onComplete] always runs on the main thread, regardless of which
     *  path in [loadContacts] triggers it - callers touch RecyclerView/UI state
     *  from this callback and must never receive it on a background thread. */
    private fun complete(onComplete: ((List<String>) -> Unit)?, result: List<String>) {
        if (onComplete == null) return
        mainHandler.post { onComplete(result) }
    }

    fun loadContacts(onComplete: ((List<String>) -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            complete(onComplete, emptyList())
            return
        }

        if (contactsLoaded && contactsList.isNotEmpty()) {
            complete(onComplete, ArrayList(contactsList))
            return
        }

        if (contactsLoading) {
            complete(onComplete, ArrayList(contactsList))
            return
        }

        contactsLoading = true

        backgroundExecutor.execute {
            try {
                val tempContactsList = mutableListOf<String>()
                val seenNames = HashSet<String>()
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        if (name != null && seenNames.add(name)) {
                            tempContactsList.add(name)
                        }
                    }
                }


                tempContactsList.sort()

                contactsList.clear()
                contactsList.addAll(tempContactsList)
                contactsLoaded = true
                contactsLoading = false

                complete(onComplete, ArrayList(contactsList))
            } catch (e: Exception) {
                e.printStackTrace()
                contactsLoading = false
                complete(onComplete, emptyList())
            }
        }
    }





    fun loadContactsEagerly() {
        loadContacts { loadedList ->


        }
    }

    fun getContactsList(): List<String> = contactsList.toList()

    fun hasLoadedContacts(): Boolean = contactsLoaded
}
