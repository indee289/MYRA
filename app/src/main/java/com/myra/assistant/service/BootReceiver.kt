package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). Restarts call monitoring after device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startService(Intent(context, CallMonitorService::class.java))
        }
    }
}
