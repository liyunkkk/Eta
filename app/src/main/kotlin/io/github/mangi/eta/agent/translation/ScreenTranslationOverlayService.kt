package io.github.mangi.eta.agent.translation

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * 屏幕翻译覆盖层服务。
 *
 * 双窗口架构：
 * 1. 主译文层：全屏、FLAG_NOT_TOUCHABLE 完全点击穿透，按原文坐标绘制译文，
 *    不拦截任何触摸事件，用户可正常操作底层应用；
 * 2. 控制胶囊：小尺寸可点击窗口，仅承载关闭按钮，是唯一可交互区域。
 */
internal class ScreenTranslationOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var translationRoot: FrameLayout? = null
    private var controlPill: LinearLayout? = null
    private val isAttached = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideOverlay()
            else -> showOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (isAttached.get()) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            stopSelf()
            return
        }
        // ---------- 窗口 1：全屏穿透译文层 ----------
        val translationLayer = FrameLayout(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        val translationParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "EtaScreenTranslation"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { wm.addView(translationLayer, translationParams) }
            .onFailure { throwable ->
                AndroidAgentLogger.warnThrottled("eta_screen_translation_add_failed") {
                    "Screen translation overlay addView failed: type=${throwable.javaClass.simpleName}"
                }
                stopSelf()
                return
            }
        // ---------- 窗口 2：可点击控制胶囊 ----------
        val density = resources.displayMetrics.density
        val pill = buildControlPill(density)
        val pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            title = "EtaScreenTranslationControl"
            topMargin = (52 * density).roundToInt()
            rightMargin = (10 * density).roundToInt()
        }
        runCatching { wm.addView(pill, pillParams) }
            .onFailure { throwable ->
                AndroidAgentLogger.warnThrottled("eta_screen_translation_pill_failed") {
                    "Screen translation control pill addView failed: ${throwable.javaClass.simpleName}"
                }
                runCatching { wm.removeView(translationLayer) }
                stopSelf()
                return
            }
        windowManager = wm
        translationRoot = translationLayer
        controlPill = pill
        isAttached.set(true)
        ScreenTranslationController.attachOverlay(this)
    }

    private fun buildControlPill(density: Float): LinearLayout {
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.setBackgroundColor(PILL_COLOR)
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(
            (12 * density).roundToInt(),
            (6 * density).roundToInt(),
            (10 * density).roundToInt(),
            (6 * density).roundToInt(),
        )
        val label = TextView(this)
        label.text = getString(io.github.mangi.eta.R.string.screen_translation_active_hint)
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        pill.addView(
            label,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val close = TextView(this)
        close.text = getString(io.github.mangi.eta.R.string.screen_translation_close)
        close.setTextColor(0xFFFFD60A.toInt())
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        close.typeface = Typeface.DEFAULT_BOLD
        close.setOnClickListener {
            ScreenTranslationController.stop(applicationContext)
        }
        pill.addView(close)
        return pill
    }

    private fun hideOverlay() {
        val wm = windowManager
        val root = translationRoot
        val pill = controlPill
        if (wm != null) {
            if (root != null) runCatching { wm.removeView(root) }
            if (pill != null) runCatching { wm.removeView(pill) }
        }
        windowManager = null
        translationRoot = null
        controlPill = null
        isAttached.set(false)
        ScreenTranslationController.detachOverlay(this)
    }

    /** 主线程调用：按最新区块重建译文层子视图。 */
    internal fun renderBlocks(blocks: List<ScreenTranslationBlock>) {
        val root = translationRoot ?: return
        @SuppressLint("DrawAllocation")
        root.removeAllViews()
        val density = resources.displayMetrics.density
        for (block in blocks) {
            val translated = block.translated ?: continue
            if (translated.isBlank()) continue
            val left = block.boundsInScreen.left.roundToInt()
            val top = block.boundsInScreen.top.roundToInt()
            val width = block.boundsInScreen.width().roundToInt()
            if (width <= 0) continue
            val textView = TextView(this)
            textView.text = translated
            textView.setTextColor(Color.WHITE)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            textView.typeface = Typeface.DEFAULT_BOLD
            textView.setLineSpacing(2f, 1f)
            textView.includeFontPadding = false
            textView.setPadding(
                (4 * density).roundToInt(),
                (2 * density).roundToInt(),
                (4 * density).roundToInt(),
                (2 * density).roundToInt(),
            )
            textView.setBackgroundColor(BLOCK_BACKGROUND_COLOR)
            val params = FrameLayout.LayoutParams(
                width.coerceAtMost(root.resources.displayMetrics.widthPixels - left),
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            params.leftMargin = left
            params.topMargin = top
            textView.layoutParams = params
            root.addView(textView)
        }
    }

    private companion object {
        val BLOCK_BACKGROUND_COLOR = 0xE6333333.toInt()
        val PILL_COLOR = 0xD91C1C1E.toInt()
    }

    internal companion object {
        const val ACTION_HIDE = "io.github.mangi.eta.agent.translation.HIDE"

        fun show(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, ScreenTranslationOverlayService::class.java)
                    .setAction("io.github.mangi.eta.agent.translation.SHOW"),
            )
        }

        fun hide(context: Context) {
            val intent = Intent(
                context.applicationContext,
                ScreenTranslationOverlayService::class.java,
            ).setAction(ACTION_HIDE)
            context.applicationContext.startService(intent)
        }
    }
}
