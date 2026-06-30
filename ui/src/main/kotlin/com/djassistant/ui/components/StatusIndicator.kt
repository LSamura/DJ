package com.djassistant.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.djassistant.service.ServiceMode
import com.djassistant.ui.theme.StatusError
import com.djassistant.ui.theme.StatusListening
import com.djassistant.ui.theme.StatusProcessing
import com.djassistant.ui.theme.StatusRunning
import com.djassistant.ui.theme.StatusStopped

@Composable
fun StatusIndicator(
    mode: ServiceMode,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val color = mode.toColor()
    val shouldPulse = mode is ServiceMode.Listening || mode is ServiceMode.Recognizing

    if (shouldPulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "alpha"
        )
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .alpha(alpha)
                .background(color)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun ServiceMode.toColor(): Color = when (this) {
    is ServiceMode.Stopped -> StatusStopped
    is ServiceMode.Starting -> StatusListening
    is ServiceMode.Running -> StatusRunning
    is ServiceMode.Listening -> StatusListening
    is ServiceMode.Recognizing -> StatusListening
    is ServiceMode.Processing -> StatusProcessing
    is ServiceMode.Error -> StatusError
}
