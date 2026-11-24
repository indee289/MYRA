package com.myra.assistant.managers

import android.content.Context
import android.provider.ContactsContract

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../managers/ContactManager.java).
 */
class ContactManager(private val context: Context) {

    /**
     * Returns [number] as-is when it is non-empty. Otherwise looks the
     * contact up by display name (LIKE "%name%") in the phone contacts and
     * returns the first matching number, or "" when nothing is found
     * (including when the query itself returns a null cursor).
     *
     * The CFR projection is the literal column "data1", which is exactly
     * [ContactsContract.CommonDataKinds.Phone.NUMBER]; the constant is
     * used here. Callers (MainViewModel) always pass "" as [number] and
     * the contact name as [name].
     */
    fun resolveNumber(number: String, name: String): String {
        if (number.isNotEmpty()) {
            return number
        }
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "display_name LIKE ?",
            arrayOf("%$name%"),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) ?: "" else ""
        } ?: ""
    }
}
