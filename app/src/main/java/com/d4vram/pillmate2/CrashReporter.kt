package com.d4vram.pillmate2

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val CRASH_FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val crashFile = File(appContext.filesDir, CRASH_FILE_NAME)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(Date())
            val stackTrace = throwable.stackTraceToStringSafe()

            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)

            kotlin.runCatching {
                crashFile.writeText(
                    buildString {
                        appendLine("timestamp=$timestamp")
                        appendLine("thread=${thread.name}")
                        appendLine()
                        appendLine(stackTrace)
                    }
                )
            }.onFailure { writeError ->
                Log.e(TAG, "Failed to persist crash log", writeError)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun Throwable.stackTraceToStringSafe(): String {
        return kotlin.runCatching {
            val sw = StringWriter()
            PrintWriter(sw).use { pw ->
                printStackTrace(pw)
            }
            sw.toString()
        }.getOrElse { fallback ->
            "Unable to capture stack trace: ${fallback.message}"
        }
    }
}
