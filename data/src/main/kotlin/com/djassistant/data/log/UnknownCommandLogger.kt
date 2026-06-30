package com.djassistant.data.log

interface UnknownCommandLogger {
    fun log(entry: UnknownCommandEntry)
    fun readRecent(limit: Int = 50): List<UnknownCommandEntry>
    fun exportPath(): String
    fun clear()
}
