package com.djassistant.feature.settings

data class DjSettings(
    val autoStartService: Boolean = false,
    val showDebugScreen: Boolean = false,
    val voskConfidenceThreshold: Float = 0.5f
)
