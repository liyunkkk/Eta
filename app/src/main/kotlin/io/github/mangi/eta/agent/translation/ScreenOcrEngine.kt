package io.github.mangi.eta.agent.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * 屏幕像素级 OCR 引擎：基于 Google ML Kit 离线端侧高精度识别。
 * 遍历全屏文本行，提取高精度 Bounding Box 像素坐标与采样背景色。
 */
internal object ScreenOcrEngine {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(bitmap: Bitmap): List<ScreenTranslationBlock> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    continuation.resume(text)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

        val width = bitmap.width
        val height = bitmap.height
        val results = ArrayList<ScreenTranslationBlock>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                if (text.length < 2 || isNoise(text)) continue
                val box = line.boundingBox ?: continue
                if (box.width() <= 0 || box.height() <= 0) continue

                // 规范化矩形坐标在屏幕位图范围内
                val safeRect = Rect(
                    box.left.coerceIn(0, width - 1),
                    box.top.coerceIn(0, height - 1),
                    box.right.coerceIn(1, width),
                    box.bottom.coerceIn(1, height),
                )
                if (safeRect.width() <= 4 || safeRect.height() <= 4) continue

                // 智能采样背景色
                val sampledColor = sampleBackgroundColor(bitmap, safeRect, width, height)
                results.add(
                    ScreenTranslationBlock(
                        source = text,
                        boundsInScreen = safeRect,
                        sampledBgColor = sampledColor,
                    ),
                )
            }
        }
        return results
    }

    private fun isNoise(text: String): Boolean {
        var letterOrDigit = 0
        for (ch in text) {
            if (ch.isLetterOrDigit()) letterOrDigit++
        }
        return letterOrDigit == 0
    }

    /**
     * 在文字框周围边缘采样背景像素，消除文字本身颜色的干扰，提取真实的底色。
     */
    private fun sampleBackgroundColor(bitmap: Bitmap, rect: Rect, width: Int, height: Int): Int {
        val samplePoints = listOf(
            Pair(rect.left - 2, rect.top - 2),
            Pair(rect.right + 2, rect.top - 2),
            Pair(rect.left - 2, rect.bottom + 2),
            Pair(rect.right + 2, rect.bottom + 2),
            Pair(rect.left - 3, rect.centerY()),
            Pair(rect.right + 3, rect.centerY()),
            Pair(rect.centerX(), rect.top - 3),
            Pair(rect.centerX(), rect.bottom + 3),
        )
        val validColors = ArrayList<Int>()
        for ((x, y) in samplePoints) {
            if (x in 0 until width && y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                // 忽略完全透明像素
                if (Color.alpha(pixel) >= 128) {
                    validColors.add(pixel)
                }
            }
        }
        if (validColors.isEmpty()) {
            return 0xFFF7F8FA.toInt() // 默认明亮背景
        }
        // 若采样点大部分亮度较高，取平均色；若大部分亮度较低，取深色
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        for (c in validColors) {
            rSum += Color.red(c)
            gSum += Color.green(c)
            bSum += Color.blue(c)
        }
        val count = validColors.size
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }
}
