package com.djassistant.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compact, non-blocking floating status overlay shown for the duration of a
 * Wake Mode dialog window ("🎧 DJ / Слушаю... / 6s"). Deliberately built with
 * plain Android views + [WindowManager] rather than Compose — the :service
 * module stays Compose-free (Compose is confined to :ui), and a raw overlay
 * window has nothing to do with the app's own Activity/Compose tree anyway.
 *
 * Never blocks interaction with other apps: FLAG_NOT_TOUCHABLE +
 * FLAG_NOT_FOCUSABLE make it purely informational.
 */
@Singleton
class VoiceOverlayController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var micDot: View? = null
    private var pulseAnimator: ValueAnimator? = null
    private var statusText: TextView? = null
    private var countdownText: TextView? = null

    private fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(context)

    @Synchronized
    fun show(status: String) {
        if (!canDrawOverlay()) {
            DjLogger.service("Overlay: SYSTEM_ALERT_WINDOW not granted — skipping overlay")
            return
        }
        if (overlayView != null) {
            updateStatus(status)
            return
        }
        val view = buildView()
        overlayView = view
        val params = layoutParams()
        runCatching { windowManager.addView(view, params) }
            .onFailure { DjLogger.serviceError("Overlay: failed to add view: ${it.message}") }
        view.alpha = 0f
        view.scaleX = 0.85f
        view.scaleY = 0.85f
        view.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
        updateStatus(status)
        startPulse()
    }

    @Synchronized
    fun updateStatus(status: String) {
        statusText?.text = status
    }

    @Synchronized
    fun updateCountdown(seconds: Int?) {
        val label = countdownText ?: return
        if (seconds == null) {
            label.isVisible = false
        } else {
            label.isVisible = true
            label.text = "${seconds}s"
        }
    }

    @Synchronized
    fun hide() {
        val view = overlayView ?: return
        stopPulse()
        view.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(180)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(view)
                }
            })
            .start()
        overlayView = null
        statusText = null
        countdownText = null
        micDot = null
    }

    private fun removeView(view: View) {
        runCatching { windowManager.removeView(view) }
    }

    private fun startPulse() {
        val dot = micDot ?: return
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.18f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val scale = it.animatedValue as Float
                dot.scaleX = scale
                dot.scaleY = scale
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    @SuppressLint("SetTextI18n")
    private fun buildView(): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(210, 20, 20, 24))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                elevation = dp(6).toFloat()
            }
        }

        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                marginEnd = dp(10)
            }
        }
        micDot = dot
        card.addView(dot)

        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(context).apply {
            text = "🎧 DJ"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        statusText = status
        labels.addView(status)

        val countdown = TextView(context).apply {
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 11f
            isVisible = false
        }
        countdownText = countdown
        labels.addView(countdown)

        card.addView(labels)

        return FrameLayout(context).apply { addView(card) }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (100 * context.resources.displayMetrics.density).toInt()
        }
    }
}
