package com.djassistant.feature.command

import com.djassistant.feature.intent.DjIntent

sealed class CommandResult {
    object Success : CommandResult()
    data class SuccessWithInfo(val message: String) : CommandResult()
    data class Failure(val reason: String) : CommandResult()
    data class NotSupported(val intent: DjIntent) : CommandResult()
}
