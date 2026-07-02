package com.djassistant.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
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
 *
 * IMPORTANT (Sprint 3.3 root-cause fix): [VoiceEngine] calls this controller
 * from its own coroutine scope, which runs on [kotlinx.coroutines.Dispatchers.IO]
 * — a plain background thread with no [Looper]. View mutation and
 * [WindowManager.addView]/[WindowManager.removeView] both require a Looper
 * on the calling thread; calling them directly from that coroutine crashed
 * the whole app (not just this component) the moment the overlay was first
 * shown, i.e. the instant the wake word was recognized. Every public method
 * here therefore posts its work onto the main thread via [mainHandler]
 * instead of executing inline, and all mutable view state is only ever
 * touched from within a posted block, so there is no cross-thread access at
 * all — not just a try/catch masking the symptom.
 */
@Singleton
class VoiceOverlayController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Only ever read/written from within a block posted to mainHandler.
    private var overlayView: View? = null
    private var micDot: View? = null
    private var pulseAnimator: ValueAnimator? = null
    private var statusText: TextView? = null
    private var countdownText: TextView? = null

    private fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(context)

    fun show(status: String) = runOnMain {
        if (!canDrawOverlay()) {
            DjLogger.service("Overlay: SYSTEM_ALERT_WINDOW not granted — skipping overlay")
            return@runOnMain
        }
        if (overlayView != null) {
            updateStatusInternal(status)
            return@runOnMain
        }
        val view = buildView()
        overlayView = view
        val params = layoutParams()
        val added = runCatching { windowManager.addView(view, params) }
            .onFailure { DjLogger.serviceError("Overlay: failed to add view", it) }
            .isSuccess
        if (!added) {
            overlayView = null
            statusText = null
            countdownText = null
            micDot = null
            return@runOnMain
        }
        view.alpha = 0f
        view.scaleX = 0.85f
        view.scaleY = 0.85f
        view.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
        updateStatusInternal(status)
        startPulse()
    }

    fun updateStatus(status: String) = runOnMain {
        updateStatusInternal(status)
    }

    private fun updateStatusInternal(status: String) {
        statusText?.text = status
    }

    fun updateCountdown(seconds: Int?) = runOnMain {
        val label = countdownText ?: return@runOnMain
        if (seconds == null) {
            label.isVisible = false
        } else {
            label.isVisible = true
            label.text = "${seconds}s"
        }
    }

    fun hide() = runOnMain {
        val view = overlayView ?: return@runOnMain
        stopPulse()
        overlayView = null
        statusText = null
        countdownText = null
        micDot = null
        view.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(180)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(view)
                }
            })
            .start()
    }

    /** Immediately tears down the overlay with no animation — used when the engine is stopping. */
    fun hideImmediately() = runOnMain {
        val view = overlayView ?: return@runOnMain
        stopPulse()
        overlayView = null
        statusText = null
        countdownText = null
        micDot = null
        removeView(view)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching(block).onFailure { DjLogger.serviceError("Overlay: error on main thread", it) }
        } else {
            mainHandler.post {
                runCatching(block).onFailure { DjLogger.serviceError("Overlay: error on main thread", it) }
            }
        }
    }

    private fun removeView(view: View) {
        runCatching { windowManager.removeView(view) }
            .onFailure { DjLogger.serviceError("Overlay: failed to remove view", it) }
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
