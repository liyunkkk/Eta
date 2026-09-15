package io.github.mangi.eta.agent.translation

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
 * 1. 主译文层：全屏、FLAG_NOT_TOUCHABLE 完全点击穿透，自适应贴合绘制译文，
 *    不拦截触摸事件，用户可正常操作底层应用；
 * 2. 液态玻璃悬浮胶囊：深浅色自适应、可拖拽贴边吸附，包含独立单次翻译/取消区域与独立关闭按钮。
 */
internal class ScreenTranslationOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var translationRoot: FrameLayout? = null
    private var controlBubble: LinearLayout? = null
    private var bubbleStatusText: TextView? = null
    private var bubbleIcon: TextView? = null
    private var bubbleCloseBtn: TextView? = null
    private var bubbleDivider: View? = null
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

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
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

        // ---------- 窗口 2：液态玻璃悬浮操作胶囊 ----------
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
            x = screenWidth - (110 * density).roundToInt()
            y = (130 * density).roundToInt()
        }

        val bubble = buildLiquidControlBubble(density, wm, bubbleParams)
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
    private fun buildLiquidControlBubble(
        density: Float,
        wm: WindowManager,
        params: WindowManager.LayoutParams,
    ): LinearLayout {
        val isDark = isDarkMode()
        val bgColor = if (isDark) BUBBLE_BG_DARK else BUBBLE_BG_LIGHT
        val strokeColor = if (isDark) BUBBLE_STROKE_DARK else BUBBLE_STROKE_LIGHT
        val textColor = if (isDark) TEXT_DARK else TEXT_LIGHT
        val subColor = if (isDark) SUBTEXT_DARK else SUBTEXT_LIGHT
        val dividerColor = if (isDark) DIVIDER_DARK else DIVIDER_LIGHT

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (10 * density).roundToInt(),
                (6 * density).roundToInt(),
                (8 * density).roundToInt(),
                (6 * density).roundToInt(),
            )
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 18f * density
                setStroke((1 * density).roundToInt(), strokeColor)
            }
            elevation = 6f * density
        }

        // 左侧操作区域：🌐 图标 + 状态文本
        val mainAction = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = false
        }

        val iconView = TextView(this).apply {
            text = "🌐"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, (5 * density).roundToInt(), 0)
        }
        bubbleIcon = iconView
        mainAction.addView(iconView)

        val statusView = TextView(this).apply {
            text = getString(io.github.mangi.eta.R.string.screen_translation_btn_translate)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        bubbleStatusText = statusView
        mainAction.addView(statusView)

        bubble.addView(mainAction)

        // 中间分割线
        val divider = View(this).apply {
            setBackgroundColor(dividerColor)
            val lp = LinearLayout.LayoutParams(
                (1 * density).roundToInt().coerceAtLeast(1),
                (13 * density).roundToInt(),
            ).apply {
                leftMargin = (8 * density).roundToInt()
                rightMargin = (6 * density).roundToInt()
            }
            layoutParams = lp
        }
        bubbleDivider = divider
        bubble.addView(divider)

        // 右侧独立关闭小按钮：✕
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(subColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                (4 * density).roundToInt(),
                (2 * density).roundToInt(),
                (4 * density).roundToInt(),
                (2 * density).roundToInt(),
            )
            includeFontPadding = false
        }
        bubbleCloseBtn = closeBtn
        bubble.addView(closeBtn)

        // 统一手势处理：分离拖拽与点击，彻底解决手势吞点击与取消无效问题
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        bubble.setOnTouchListener { _, event ->
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
                    if (abs(event.rawX - touchStartX) > touchSlop || abs(event.rawY - touchStartY) > touchSlop) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { wm.updateViewLayout(bubble, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 精确分发点击位置：根据相对 X 坐标判断是主区域还是关闭按钮
                        val touchX = event.x
                        val dividerLeft = divider.left
                        if (touchX >= dividerLeft - 4 * density) {
                            // 点击了关闭小按钮：完全退出屏幕翻译悬浮窗
                            ScreenTranslationController.stop(applicationContext)
                        } else {
                            // 点击了主区域：如果正在翻译或已有译文，则取消并清空；否则发起单次翻译
                            if (ScreenTranslationController.hasActiveTranslations() ||
                                ScreenTranslationController.isTranslating()
                            ) {
                                ScreenTranslationController.clearAndStop(applicationContext)
                            } else {
                                ScreenTranslationController.requestRefresh()
                            }
                        }
                    } else {
                        // 拖拽结束：吸附贴靠屏幕左侧或右侧边缘
                        val screenWidth = resources.displayMetrics.widthPixels
                        val bubbleWidth = bubble.width.coerceAtLeast((90 * density).roundToInt())
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
        bubbleCloseBtn = null
        bubbleDivider = null
        isAttached.set(false)
        ScreenTranslationController.detachOverlay(this)
    }

    /** 主线程调用：更新悬浮球状态文字与深浅色模式。 */
    internal fun updateStatusText(text: String) {
        bubbleStatusText?.text = text
    }

    /** 主线程调用：清空译文层。 */
    internal fun clearBlocks() {
        translationRoot?.removeAllViews()
        bubbleStatusText?.text = getString(io.github.mangi.eta.R.string.screen_translation_btn_translate)
    }

    /**
     * 主线程调用：按最新区块自适应重绘译文层（移植 overlay-translator 贴合渲染逻辑）。
     * 针对文字区域大小、行数与深浅色模式自适应排版。
     */
    internal fun renderBlocks(blocks: List<ScreenTranslationBlock>) {
        val root = translationRoot ?: return
        @SuppressLint("DrawAllocation")
        root.removeAllViews()

        val density = resources.displayMetrics.density
        val screenWidth = root.resources.displayMetrics.widthPixels
        val isDark = isDarkMode()
        val blockBgColor = if (isDark) BLOCK_BG_DARK else BLOCK_BG_LIGHT
        val blockStrokeColor = if (isDark) BLOCK_STROKE_DARK else BLOCK_STROKE_LIGHT
        val blockTextColor = if (isDark) BLOCK_TEXT_DARK else BLOCK_TEXT_LIGHT

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

            // 字符宽度估算（约 14dp），保障短文本不被压缩为窄长竖条
            val approxCharWidth = 13.5f * density
            val estimatedWidth = (translated.length * approxCharWidth + 10f * density).roundToInt()
            val maxWidthAllowed = screenWidth - left - (6 * density).roundToInt()
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
                setTextColor(blockTextColor)
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
                } else if (origHeight <= (36 * density).roundToInt()) {
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
                    setColor(blockBgColor)
                    cornerRadius = 4f * density
                    setStroke((1 * density).roundToInt(), blockStrokeColor)
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
            bubbleStatusText?.text = getString(io.github.mangi.eta.R.string.screen_translation_btn_cancel)
        }
    }

    internal companion object {
        const val ACTION_HIDE = "io.github.mangi.eta.agent.translation.HIDE"
        private const val ACTION_SHOW = "io.github.mangi.eta.agent.translation.SHOW"

        // 液态玻璃视觉配色（深浅色自适应）
        private val BUBBLE_BG_DARK = 0xEB1C1C1E.toInt()
        private val BUBBLE_BG_LIGHT = 0xF2F6F7F9.toInt()
        private val BUBBLE_STROKE_DARK = 0x33FFFFFF.toInt()
        private val BUBBLE_STROKE_LIGHT = 0x26000000.toInt()

        private val TEXT_DARK = 0xFFFFFFFF.toInt()
        private val TEXT_LIGHT = 0xFF1D1D1F.toInt()
        private val SUBTEXT_DARK = 0xFF8E8E93.toInt()
        private val SUBTEXT_LIGHT = 0xFF6C6C70.toInt()
        private val DIVIDER_DARK = 0x26FFFFFF.toInt()
        private val DIVIDER_LIGHT = 0x20000000.toInt()

        // 贴合译文卡片配色（深浅色自适应）
        private val BLOCK_BG_DARK = 0xF018181C.toInt()
        private val BLOCK_BG_LIGHT = 0xF4FFFFFF.toInt()
        private val BLOCK_STROKE_DARK = 0x33FFFFFF.toInt()
        private val BLOCK_STROKE_LIGHT = 0x26000000.toInt()
        private val BLOCK_TEXT_DARK = 0xFFF5F5F7.toInt()
        private val BLOCK_TEXT_LIGHT = 0xFF1C1C1E.toInt()

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