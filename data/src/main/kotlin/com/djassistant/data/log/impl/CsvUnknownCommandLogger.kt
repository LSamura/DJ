package com.djassistant.data.log.impl

import android.content.Context
import com.djassistant.data.log.UnknownCommandEntry
import com.djassistant.data.log.UnknownCommandLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val LOG_FILE_NAME = "unknown_commands.csv"
private const val MAX_FILE_SIZE_BYTES = 512 * 1024L // 512 KB
private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

@Singleton
class CsvUnknownCommandLogger @Inject constructor(
    @ApplicationContext private val context: Context
) : UnknownCommandLogger {

    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())

    override fun log(entry: UnknownCommandEntry) {
        runCatching {
            rotateIfNeeded()
            val timestamp = dateFormat.format(Date(entry.timestampMs))
            val line = "${timestamp},\"${entry.rawText.escapeCsv()}\",\"${entry.recognizedAs.escapeCsv()}\"\n"
            logFile.appendText(line)
        }.onFailure { Timber.e(it, "Failed to log unknown command") }
    }

    override fun readRecent(limit: Int): List<UnknownCommandEntry> {
        if (!logFile.exists()) return emptyList()
        return runCatching {
            logFile.readLines()
                .takeLast(limit)
                .mapNotNull { parseLine(it) }
                .reversed()
        }.getOrDefault(emptyList())
    }

    override fun exportPath(): String = logFile.absolutePath

    override fun clear() {
        runCatching { logFile.delete() }
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
            val lines = logFile.readLines()
            val kept = lines.takeLast(lines.size / 2)
            logFile.writeText(kept.joinToString("\n") + "\n")
        }
    }

    private fun parseLine(line: String): UnknownCommandEntry? {
        val parts = line.split(",")
        if (parts.size < 3) return null
        return runCatching {
            UnknownCommandEntry(
                rawText = parts[1].trim('"'),
                recognizedAs = parts[2].trim('"'),
                timestampMs = dateFormat.parse(parts[0])?.time ?: System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun String.escapeCsv() = replace("\"", "\"\"")
}
