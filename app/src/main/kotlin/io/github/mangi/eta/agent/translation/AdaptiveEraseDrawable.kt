package io.github.mangi.eta.agent.translation

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * 屏幕自适应抹除遮罩：在原文字位置绘制纯色微圆角矩形，彻底遮盖原文字，
 * 避免译文与底层原文叠字重叠（移植自 overlay-translator 核心算法）。
 */
internal class AdaptiveEraseDrawable(
    private val fillColor: Int,
    private val strokeColor: Int? = null,
    private val cornerRadiusPx: Float = 6f,
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }

    private val strokePaint = strokeColor?.let {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = it
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
    }

    private val rectF = RectF()

    override fun draw(canvas: Canvas) {
        rectF.set(bounds)
        if (cornerRadiusPx > 0f) {
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, fillPaint)
            strokePaint?.let { canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, it) }
        } else {
            canvas.drawRect(rectF, fillPaint)
            strokePaint?.let { canvas.drawRect(rectF, it) }
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha.coerceIn(0, 255)
        strokePaint?.alpha = alpha.coerceIn(0, 255)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint?.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
