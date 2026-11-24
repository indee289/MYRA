package com.myra.assistant.utils

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import java.util.Locale

/**
 * Resolves a spoken contact name to a phone number using the system
 * contacts provider: exact (LIKE) match first, then a fuzzy match based on
 * Levenshtein distance.
 *
 * Restored from CFR-decompiled bytecode of the user's own MYRA app
 * (com.myra.assistant). CFR failed to decompile [resolveExact],
 * [fuzzyResolve] and [getPhoneNumber]; those three were reconstructed from
 * the dex2jar bytecode with javap (see the notes on each method) — the
 * signatures, queries, thresholds and log messages are taken from the
 * bytecode, not guessed.
 */
object ContactResolver {

    private const val TAG = "MYRA_CONTACT"

    fun resolve(context: Context, name: String): String {
        if (name.isBlank()) return ""
        val exact = resolveExact(context, name)
        if (exact.isNotEmpty()) {
            Log.d(TAG, "Exact match found: $name -> $exact")
            return exact
        }
        val fuzzy = fuzzyResolve(context, name)
        if (fuzzy.isNotEmpty()) {
            Log.d(TAG, "Fuzzy match found: $name -> $fuzzy")
            return fuzzy
        }
        Log.w(TAG, "No contact found for: $name")
        return ""
    }

    fun clean(number: String): String {
        return Regex("[^0-9+]").replace(number, "")
    }

    fun fuzzyResolve(context: Context, name: String): String {
        if (name.isBlank()) return ""
        var bestPhone = ""
        try {
            var bestScore = Double.MAX_VALUE
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf("_id", "display_name"),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(1) ?: continue
                    val distance = levenshteinDistance(
                        name.lowercase(Locale.ROOT),
                        displayName.lowercase(Locale.ROOT)
                    )
                    // A substring match (either way round) halves the score.
                    val factor = if (displayName.contains(name, ignoreCase = true) ||
                        name.contains(displayName, ignoreCase = true)
                    ) 0.5 else 1.0
                    val score = distance * factor
                    if (score < bestScore && score < 3.0) {
                        bestScore = score
                        bestPhone = getPhoneNumber(context, cursor.getString(0)!!)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fuzzy resolve error: ${e.message}")
            return ""
        }
        return bestPhone
    }

    private fun resolveExact(context: Context, name: String): String {
        var result = ""
        try {
            val pattern = "%$name%"
            result = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf("data1"),
                "display_name LIKE ?",
                arrayOf(pattern),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Exact resolve error: ${e.message}")
            result = ""
        }
        return result
    }

    private fun getPhoneNumber(context: Context, contactId: String): String {
        // Per the bytecode: no log on failure, just "" (also "" when the
        // cursor is null or the number itself is null).
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf("data1"),
                "contact_id = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2.toDouble()
        if (len2 == 0) return len1.toDouble()
        val dp = Array(len1 + 1) { DoubleArray(len2 + 1) }
        // NOTE — deliberate quirk, reproduced from the shipped bytecode, do
        // not "fix": in the dex these two initialisation loops are guarded
        // by `if (len <= 0)`, so for non-empty strings they never run and
        // the first row/column stay 0.0. Verified by executing a 1:1
        // transliteration of the bytecode: e.g. distance("amit", "sumit")
        // == 1.0 and distance("a", "abc") == 1.0, where textbook
        // Levenshtein gives 2.0 in both cases. fuzzyResolve's < 3.0
        // threshold was tuned against this behaviour, so changing it would
        // change which contact wins.
        if (len1 <= 0) {
            for (i in 0..len1) {
                dp[i][0] = i.toDouble()
            }
        }
        if (len2 <= 0) {
            for (j in 0..len2) {
                dp[0][j] = j.toDouble()
            }
        }
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0.0 else 1.0
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1.0,
                    minOf(dp[i][j - 1] + 1.0, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[len1][len2]
    }
}
