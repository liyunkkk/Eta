package io.github.mangi.eta.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Siri 风格主题装配（配色 + 字阶）。
 *
 * 唯一事实基准：参考源码/siri_ui_ref/设计规格.md（5 张截图实测）。
 * 截图存在环境绿光污染，G 通道被系统性抬高；下列色值为去污染估算，
 * 渐变方向与明暗关系可信，绝对色相装机后可一眼校准。
 *
 * 覆盖策略（保守）：仅覆盖项目实际读取、且影响观感的字段，
 * 其余沿用 Miuix 默认值，避免破坏 Miuix 内部对比度契约。
 */

// ---- Siri 浅色态色板（基准：Apple_HIG_设计基准.md） ----
private val SiriLightBg = Color(0xFFF8F9FA)       // 页面底色 暖白
private val SiriLightCard = Color(0xFFFFFFFF)     // 卡片（设置页等实底面）
private val SiriLightTextPrimary = Color(0xFF171717)   // neutral-900
private val SiriLightTextSecondary = Color(0xFF737373) // neutral-500
private val SiriAccentTeal = Color(0xFF2FA79B)    // Siri 青（主 accent）

// ---- Siri 深色态（按浅色同结构降明度推导） ----
private val SiriDarkBg = Color(0xFF0B0C0E)        // 与 SiriBackdrop.darkBase 一致
private val SiriDarkSurface = Color(0xFF1C1C1E)
private val SiriDarkSurfaceHigh = Color(0xFF2C2C2E)
private val SiriDarkTextPrimary = Color(0xFFFFFFFF)
private val SiriDarkTextSecondary = Color(0xFF98989D)

/** Siri 浅色配色：在 Miuix 浅色默认基础上覆盖实读字段。 */
fun siriLightColors(): Colors = lightColorScheme().copy(
    primary = SiriAccentTeal,
    onPrimary = Color.White,
    primaryContainer = SiriAccentTeal.copy(alpha = 0.16f),
    onPrimaryContainer = SiriLightTextPrimary,
    secondaryContainer = SiriLightCard,
    onSecondaryContainer = SiriLightTextPrimary,
    // 问题1：OverlayDialog 的默认 backgroundColor 取 MiuixTheme.colorScheme.background
    // （DialogContentLayout.kt:445）。此处一并半透明化，让输入路径/供应商等弹窗
    // 与 OverlayListPopup 保持同一玻璃观感。项目内无其它 background 消费点。
    background = SiriLightBg.copy(alpha = 0.88f),
    onBackground = SiriLightTextPrimary,
    surface = SiriLightCard,
    onSurface = SiriLightTextPrimary,
    surfaceVariant = SiriLightCard,
    onSurfaceSecondary = SiriLightTextSecondary,
    onSurfaceVariantSummary = SiriLightTextSecondary,
    onSurfaceVariantActions = SiriLightTextSecondary.copy(alpha = 0.62f),
    disabledOnSurface = SiriLightTextSecondary.copy(alpha = 0.45f),
    // 问题1：Miuix 全部二级弹窗/浮层（OverlayListPopup / WindowListPopup）的容器背景
    // 在框架内部硬取 surfaceContainer（ListPopup.kt:605），因此在这里统一改为半透明
    // 白玻璃，让弹窗一次覆盖到位，与主界面液态玻璃观感一致，而非此前的纯白硬底。
    surfaceContainer = SiriLightCard.copy(alpha = 0.86f),
    onSurfaceContainer = SiriLightTextPrimary,
    onSurfaceContainerVariant = SiriLightTextSecondary,
    surfaceContainerHigh = SiriLightCard.copy(alpha = 0.93f),
    onSurfaceContainerHigh = SiriLightTextSecondary,
    outline = Color(0xFFD9D9DE),
    windowDimming = Color.Black.copy(alpha = 0.30f),
)

/** Siri 深色配色：在 Miuix 深色默认基础上覆盖实读字段。 */
fun siriDarkColors(): Colors = darkColorScheme().copy(
    primary = SiriAccentTeal.copy(alpha = 0.92f),
    onPrimary = Color.White,
    primaryContainer = SiriAccentTeal.copy(alpha = 0.24f),
    onPrimaryContainer = SiriDarkTextPrimary,
    secondaryContainer = SiriDarkSurface,
    onSecondaryContainer = SiriDarkTextPrimary,
    background = SiriDarkBg.copy(alpha = 0.88f),
    onBackground = SiriDarkTextPrimary,
    surface = SiriDarkSurface,
    onSurface = SiriDarkTextPrimary,
    surfaceVariant = SiriDarkSurface,
    onSurfaceSecondary = SiriDarkTextSecondary,
    onSurfaceVariantSummary = SiriDarkTextSecondary,
    onSurfaceVariantActions = SiriDarkTextSecondary.copy(alpha = 0.62f),
    disabledOnSurface = SiriDarkTextSecondary.copy(alpha = 0.45f),
    surfaceContainer = SiriDarkSurface.copy(alpha = 0.86f),
    onSurfaceContainer = SiriDarkTextPrimary,
    onSurfaceContainerVariant = SiriDarkTextSecondary,
    surfaceContainerHigh = SiriDarkSurfaceHigh.copy(alpha = 0.93f),
    onSurfaceContainerHigh = SiriDarkTextSecondary,
    outline = Color(0xFF3A3A3C),
    windowDimming = Color.Black.copy(alpha = 0.60f),
)

/**
 * Siri 字阶：不换字体，仅调整现有 Miuix textStyles 的字号 / 字重 / 行高。
 * 仅覆盖项目实读档位，其余沿用默认。
 */
fun siriTextStyles(): TextStyles = defaultTextStyles(
    // 空态大标题 "Meet Apple Foundation Advanced"：粗黑大字。
    title1 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15f.em),
    title2 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2f.em),
    // 顶栏标题：17sp Semibold。
    headline1 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    headline2 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    // 副标题 3 行 / 段落正文：行高放宽。
    paragraph = TextStyle(fontSize = 16.sp, lineHeight = 1.35f.em),
    body1 = TextStyle(fontSize = 16.sp, lineHeight = 1.3f.em),
    body2 = TextStyle(fontSize = 14.sp, lineHeight = 1.3f.em),
    // 建议 chips / 辅助说明。
    footnote1 = TextStyle(fontSize = 13.sp, lineHeight = 1.25f.em),
    footnote2 = TextStyle(fontSize = 11.sp, lineHeight = 1.2f.em),
    subtitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)
