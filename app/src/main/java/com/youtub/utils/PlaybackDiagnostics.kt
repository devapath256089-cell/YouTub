/*
 * YouTub Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.youtub.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight on-device diagnostics logger.
 *
 * Writes timestamped playback/extraction events to
 * Android/data/com.youtub/files/youtub_debug.log so playback failures can be
 * diagnosed without logcat access. Rotates to .1 at 256 KB.
 */
object PlaybackDiagnostics {
    private const val MAX_BYTES = 256 * 1024
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logDir: File? = null

    fun init(context: Context) {
        if (logDir == null) {
            logDir = context.applicationContext.getExternalFilesDir(null)
        }
    }

    fun log(event: String, detail: String = "") {
        try {
            val dir = logDir ?: return
            val f = File(dir, "youtub_debug.log")
            if (f.length() > MAX_BYTES) {
                val old = File(dir, "youtub_debug.log.1")
                old.delete()
                f.renameTo(old)
            }
            val line = "${fmt.format(Date())} [$event] $detail".trimEnd() + "\n"
            f.appendText(line)
        } catch (_: Exception) {
            // diagnostics must never crash the app
        }
    }
}
