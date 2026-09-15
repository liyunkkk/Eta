package io.github.mangi.eta.agent.translation

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 屏幕翻译覆盖层服务。
 *
 * 双窗口架构：
 * 1. 主译文层：全屏、FLAG_NOT_TOUCHABLE 完全点击穿透，按原文坐标自适应贴合绘制译文，
 *    不拦截任何触摸事件，用户可正常操作底层应用；
 * 2. 悬浮操作胶囊：精致、可拖拽边缘吸附的极简悬浮窗，支持“单击翻译，再点击取消/清除”。
 */
internal class ScreenTranslationOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var translationRoot: FrameLayout? = null
    private var controlBubble: LinearLayout? = null
    private var bubbleStatusText: TextView? = null
    private var bubbleIcon: TextView? = null
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

        // ---------- 窗口 2：轻量美观悬浮操作胶囊 ----------
        val density = resources.displayMetrics.density
        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "EtaScreenTranslationBubble"
            val screenWidth = resources.displayMetrics.widthPixels
            x = screenWidth - (88 * density).roundToInt()
            y = (120 * density).roundToInt()
        }

        val bubble = buildControlBubble(density, wm, bubbleParams)

        runCatching { wm.addView(bubble, bubbleParams) }
            .onFailure { throwable ->
                AndroidAgentLogger.warnThrottled("eta_screen_translation_bubble_failed") {
                    "Screen translation bubble addView failed: ${throwable.javaClass.simpleName}"
                }
                runCatching { wm.removeView(translationLayer) }
                stopSelf()
                return
            }

        windowManager = wm
        translationRoot = translationLayer
        controlBubble = bubble
        isAttached.set(true)
        ScreenTranslationController.attachOverlay(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildControlBubble(
        density: Float,
        wm: WindowManager,
        params: WindowManager.LayoutParams,
    ): LinearLayout {
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (10 * density).roundToInt(),
                (6 * density).roundToInt(),
                (12 * density).roundToInt(),
                (6 * density).roundToInt(),
            )
            background = GradientDrawable().apply {
                setColor(BUBBLE_BACKGROUND_COLOR)
                cornerRadius = 18f * density
                setStroke((1 * density).roundToInt(), BUBBLE_STROKE_COLOR)
            }
            elevation = 6f * density
        }

        // 图标：🌐
        val iconView = TextView(this).apply {
            text = "🌐"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, (6 * density).roundToInt(), 0)
        }
        bubbleIcon = iconView
        bubble.addView(iconView)

        // 操作状态文本：翻译 / 取消
        val statusView = TextView(this).apply {
            text = getString(io.github.mangi.eta.R.string.screen_translation_active_hint)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        bubbleStatusText = statusView
        bubble.addView(statusView)

        // 单击：未翻译时触发翻译，已翻译时取消并清除
        bubble.setOnClickListener {
            if (ScreenTranslationController.hasActiveTranslations()) {
                ScreenTranslationController.clearAndStop(applicationContext)
            } else {
                ScreenTranslationController.requestRefresh()
            }
        }

        // 拖拽手势与吸附贴边
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > (8 * density).toInt() || abs(dy) > (8 * density).toInt()) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { wm.updateViewLayout(bubble, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    } else {
                        // 贴靠最近的屏幕边缘（左侧或右侧）
                        val screenWidth = resources.displayMetrics.widthPixels
                        val bubbleWidth = bubble.width.coerceAtLeast((72 * density).roundToInt())
                        val margin = (10 * density).roundToInt()
                        params.x = if (params.x + bubbleWidth / 2 < screenWidth / 2) {
                            margin
                        } else {
                            screenWidth - bubbleWidth - margin
                        }
                        runCatching { wm.updateViewLayout(bubble, params) }
                    }
                    true
                }
                else -> false
            }
        }

        return bubble
    }

    private fun hideOverlay() {
        val wm = windowManager
        val root = translationRoot
        val bubble = controlBubble
        if (wm != null) {
            if (root != null) runCatching { wm.removeView(root) }
            if (bubble != null) runCatching { wm.removeView(bubble) }
        }
        windowManager = null
        translationRoot = null
        controlBubble = null
        bubbleStatusText = null
        bubbleIcon = null
        isAttached.set(false)
        ScreenTranslationController.detachOverlay(this)
    }

    /** 主线程调用：更新悬浮球状态文字。 */
    internal fun updateStatusText(text: String) {
        bubbleStatusText?.text = text
    }

    /** 主线程调用：清空译文层。 */
    internal fun clearBlocks() {
        translationRoot?.removeAllViews()
        bubbleStatusText?.text = getString(io.github.mangi.eta.R.string.screen_translation_active_hint)
    }

    /**
     * 主线程调用：按最新区块自适应重绘译文层。
     * 解决宽度过小换行纵向溢出、文字重叠等排版缺陷。
     */
    internal fun renderBlocks(blocks: List<ScreenTranslationBlock>) {
        val root = translationRoot ?: return
        @SuppressLint("DrawAllocation")
        root.removeAllViews()

        val density = resources.displayMetrics.density
        val screenWidth = root.resources.displayMetrics.widthPixels

        var renderedCount = 0
        for (block in blocks) {
            val translated = block.translated ?: continue
            if (translated.isBlank()) continue
            if (translated.sameTranslationInputAs(block.source)) continue

            val bounds = block.boundsInScreen
            val left = bounds.left.coerceAtLeast(0)
            val top = bounds.top.coerceAtLeast(0)
            val origWidth = bounds.width()
            val origHeight = bounds.height()
            if (origWidth <= 0 || origHeight <= 0) continue

            // 中文字符宽度估算（约 14dp），保障短词不会被挤成 1 字符窄长竖条
            val approxCharWidth = 14f * density
            val estimatedWidth = (translated.length * approxCharWidth + 12f * density).roundToInt()
            val maxWidthAllowed = screenWidth - left - (8 * density).roundToInt()
            if (maxWidthAllowed <= 0) continue

            val isSingleLineCandidate = origHeight <= (34 * density).roundToInt()
            val targetWidth = if (isSingleLineCandidate) {
                maxOf(origWidth, estimatedWidth).coerceAtMost(maxWidthAllowed)
            } else {
                origWidth.coerceAtMost(maxWidthAllowed)
            }
            if (targetWidth <= 0) continue

            val textView = TextView(this).apply {
                text = translated
                setTextColor(0xF2FFFFFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL or Gravity.START

                if (origHeight <= (22 * density).roundToInt()) {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setPadding(
                        (3 * density).roundToInt(),
                        (1 * density).roundToInt(),
                        (3 * density).roundToInt(),
                        (1 * density).roundToInt(),
                    )
                    maxLines = 1
                } else if (origHeight <= (34 * density).roundToInt()) {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(
                        (4 * density).roundToInt(),
                        (2 * density).roundToInt(),
                        (4 * density).roundToInt(),
                        (2 * density).roundToInt(),
                    )
                    maxLines = 1
                } else {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(
                        (5 * density).roundToInt(),
                        (3 * density).roundToInt(),
                        (5 * density).roundToInt(),
                        (3 * density).roundToInt(),
                    )
                    maxLines = maxOf(2, (origHeight / (16 * density)).roundToInt() + 1)
                }

                background = GradientDrawable().apply {
                    setColor(BLOCK_BACKGROUND_COLOR)
                    cornerRadius = 4f * density
                    setStroke((1 * density).roundToInt(), BLOCK_STROKE_COLOR)
                }
            }

            val params = FrameLayout.LayoutParams(
                targetWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = left
                topMargin = top
            }
            root.addView(textView, params)
            renderedCount++
        }

        if (renderedCount > 0) {
            bubbleStatusText?.text = getString(io.github.mangi.eta.R.string.screen_translation_close)
        }
    }

    internal companion object {
        const val ACTION_HIDE = "io.github.mangi.eta.agent.translation.HIDE"
        private const val ACTION_SHOW = "io.github.mangi.eta.agent.translation.SHOW"

        // 现代微质感半透明配色
        private val BLOCK_BACKGROUND_COLOR = 0xEB202024.toInt()
        private val BLOCK_STROKE_COLOR = 0x33FFFFFF.toInt()
        private val BUBBLE_BACKGROUND_COLOR = 0xEB1C1C1E.toInt()
        private val BUBBLE_STROKE_COLOR = 0x40FFFFFF.toInt()

        fun show(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, ScreenTranslationOverlayService::class.java)
                    .setAction(ACTION_SHOW),
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