package com.myra.assistant.repository

import android.content.Context
import com.myra.assistant.model.AppCommand

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../repository/CommandExecutor.java).
 *
 * NOTE — faithfully reproduced, but read this: the decompiled `execute`
 * performs NO side effect at all. It only reports whether the command type
 * is one of the five recognised types (OPEN_APP, CALL, SMS, WHATSAPP_MSG,
 * WHATSAPP_CALL) and returns true/false accordingly; it never launches
 * anything, never touches the managers, and never reads `command.params`.
 * The hash-switch in the CFR output is the compiled form of a `when`
 * on the type string. This looks like a stub / placeholder in the original
 * app (MainViewModel dispatches commands itself instead), and no caller of
 * CommandExecutor was found anywhere in the surviving source or in the
 * decompiled tree. Do NOT "fix" it by adding dispatch logic — that would
 * invent behaviour that is not in the dex.
 */
class CommandExecutor(private val context: Context) {

    fun execute(command: AppCommand): Boolean {
        return when (command.type) {
            "OPEN_APP",
            "CALL",
            "SMS",
            "WHATSAPP_MSG",
            "WHATSAPP_CALL" -> true
            else -> false
        }
    }
}
