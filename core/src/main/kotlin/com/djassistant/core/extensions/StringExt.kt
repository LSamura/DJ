package com.djassistant.core.extensions

private val WAKE_WORD_PREFIXES = listOf("диджей", "dj")

fun String.normalizeCommand(): String =
    this.lowercase().trim()

fun String.stripWakeWord(): String {
    val normalized = normalizeCommand()
    for (prefix in WAKE_WORD_PREFIXES) {
        if (normalized.startsWith(prefix)) {
            return normalized.removePrefix(prefix).trim().trimStart(',', ' ')
        }
    }
    return normalized
}

fun String.containsWakeWord(): Boolean {
    val normalized = normalizeCommand()
    return WAKE_WORD_PREFIXES.any { normalized.startsWith(it) }
}
