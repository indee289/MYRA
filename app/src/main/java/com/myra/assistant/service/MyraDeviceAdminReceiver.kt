package com.myra.assistant.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). NOTE on package: the kapt stub for this class
 * lives under com.myra.assistant.receiver, but the dex class, the
 * AndroidManifest declaration (.service.MyraDeviceAdminReceiver) and the
 * surviving SettingsActivity.kt import all use
 * com.myra.assistant.service.MyraDeviceAdminReceiver, so this file is in
 * the service package.
 */
class MyraDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin Enabled ✅", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Device Admin Disabled ❌", Toast.LENGTH_SHORT).show()
    }
}
