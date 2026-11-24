package com.myra.assistant.automation

import android.content.Context
import android.content.Intent
import android.net.Uri

object MediaAutomation {

    fun playYouTube(context: Context, query: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=$query")
        ).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun playSpotify(context: Context, query: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("spotify:search:$query")
        ).apply {
            setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
