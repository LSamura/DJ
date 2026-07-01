package com.djassistant.feature.command

import com.djassistant.feature.intent.DjIntent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandRegistry @Inject constructor() {

    private val commands = mutableListOf<DjCommand>()

    fun register(command: DjCommand) {
        commands.add(command)
    }

    val allCommands: List<DjCommand> get() = commands.toList()

    fun findByIntent(intent: DjIntent): DjCommand? =
        commands.firstOrNull { it.intent::class == intent::class }
}
