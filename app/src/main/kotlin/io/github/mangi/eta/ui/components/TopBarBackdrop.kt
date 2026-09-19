package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.data.model.AppearanceVisualStyle
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.app.LocalBlurEnabled
import io.github.mangi.eta.ui.app.LocalTopBarBlurStyle
import io.github.mangi.eta.ui.theme.drawSiriBackdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberTopBarBackdrop(): LayerBackdrop? {
    if (!LocalBlurEnabled.current || !isRuntimeShaderSupported()) return null
    val isSiriStyle = LocalAppearanceSettings.current.visualStyle == AppearanceVisualStyle.SIRI
    val isDark = isSystemInDarkTheme()
    // 注意：rememberLayerBackdrop 的 onDraw 不是 @Composable 上下文，
    // 颜色必须在组合期先取出来。
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        if (isSiriStyle) {
            // SIRI：backdrop 底必须是弥散光晕，否则顶栏毛玻璃采样到不透明白底，
            // 表现为「顶部不透明」——这正是之前顶栏发白的根因。
            drawSiriBackdrop(isDark)
        } else {
            drawRect(surfaceColor)
        }
        drawContent()
    }
}

@Composable
internal fun TopBarBackdrop(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    // SIRI 风格顶栏是覆盖在弥散光晕之上的毛玻璃，需大幅降低底色不透明度，
    // 否则整条白色实底会把顶部的薄荷绿光晕盖死。
    val isSiriStyle = LocalAppearanceSettings.current.visualStyle == AppearanceVisualStyle.SIRI
    val surfaceAlpha = if (isSiriStyle) SiriTopBarSurfaceAlpha else TopBarSurfaceAlpha
    val progressiveAlpha = if (isSiriStyle) SiriProgressiveTopBarSurfaceAlpha else ProgressiveTopBarSurfaceAlpha
    val modifier = when {
        backdrop == null -> if (isSiriStyle) Modifier else Modifier.background(surfaceColor)
        LocalTopBarBlurStyle.current == AppearanceTopBarBlurStyle.PROGRESSIVE -> {
            Modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                blurRadius = ProgressiveTopBarBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(surfaceColor.copy(alpha = progressiveAlpha)),
                    ),
                ),
            )
        }
        else -> {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = TopBarBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(surfaceColor.copy(alpha = surfaceAlpha)),
                    ),
                ),
            )
        }
    }
    Box(modifier = modifier) { content() }
}

internal fun Modifier.captureForTopBar(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop)

@Composable
internal fun topBarContainerColor(backdrop: LayerBackdrop?): Color =
    if (backdrop == null) MiuixTheme.colorScheme.surface else Color.Transparent

private const val TopBarBlurRadius = 25f
private const val TopBarSurfaceAlpha = 0.8f
private const val ProgressiveTopBarBlurRadius = 10f
private const val ProgressiveTopBarSurfaceAlpha = 0.3f
// SIRI 风格：顶栏近乎全透明，仅保留毛玻璃折射，让弥散光晕透出。
private const val SiriTopBarSurfaceAlpha = 0.08f
private const val SiriProgressiveTopBarSurfaceAlpha = 0.05f
