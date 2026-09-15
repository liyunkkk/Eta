package io.github.mangi.eta.agent.translation

import android.graphics.Rect

/**
 * 屏幕翻译的一个翻译区块：原文 + 屏幕坐标 + 译文。
 *
 * [boundsInScreen] 来自无障碍节点的 [android.view.accessibility.AccessibilityNodeInfo.getBoundsInScreen]。
 */
internal data class ScreenTranslationBlock(
    val source: String,
    val boundsInScreen: Rect,
    val translated: String? = null,
)

/**
 * 判断两段文本是否可以跳过翻译（近似相同）。
 * 精确相等或去除空白后相等都视为相同。
 */
internal fun String.sameTranslationInputAs(other: String): Boolean {
    if (this == other) return true
    return this.filter { !it.isWhitespace() } == other.filter { !it.isWhitespace() }
}
