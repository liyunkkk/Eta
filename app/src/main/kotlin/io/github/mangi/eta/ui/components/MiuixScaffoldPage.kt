package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceVisualStyle
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.layout.WidePageContent
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Eta 二级列表页的统一骨架：手机折叠大标题、宽屏固定小标题、顶栏毛玻璃、横屏安全区、
 * 宽屏内容居中、滚动边界触感与越界回弹均由此处统一提供。
 */
@Composable
fun MiuixScaffoldPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberTopBarBackdrop()
    val topBarColor = topBarContainerColor(backdrop)
    // 问题3：SIRI 风格下二级页背景必须透明，让壳层弥散光晕透出。
    // Miuix Scaffold 默认 containerColor = colorScheme.surface = #FFFFFF 不透明硬底，
    // 会把顶栏下方整块盖成纯白，与顶栏透出的光晕形成割裂（实测 y>=400 全为 255,255,255）。
    // 一处改动即覆盖全部使用本骨架的二级页，DEFAULT 模式行为不变。
    val isSiriStyle = LocalAppearanceSettings.current.visualStyle == AppearanceVisualStyle.SIRI
    val containerColor = if (isSiriStyle) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = containerColor,
        topBar = {
            TopBarBackdrop(backdrop) {
                AdaptiveTopAppBar(
                    title = title,
                    color = topBarColor,
                    navigationIcon = { MiuixBackButton(onClick = onBack) },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        WidePageContent { sidePadding ->
            // 保留 MiuixTheme 注入的默认越界工厂，让短内容页也能回弹到顶栏采样区。
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalCutoutPadding()
                    .captureForTopBar(backdrop)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    top = innerPadding.calculateTopPadding(),
                    end = sidePadding,
                ),
            ) {
                content()
                item(key = "bottom_spacer") {
                    MiuixPageBottomSpacer()
                }
            }
        }
    }
}

/**
 * 自定义内容二级页的低层骨架。调用方负责把顶部 padding、横向安全区与 nested scroll
 * 接入自己的内容；[sidePadding] 用于在宽屏限制实际内容宽度，滚动容器本身仍应保持全宽。
 */
@Composable
fun MiuixScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (
        paddingValues: PaddingValues,
        scrollBehavior: ScrollBehavior,
        sidePadding: Dp,
    ) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberTopBarBackdrop()
    val topBarColor = topBarContainerColor(backdrop)
    // 问题3：与 MiuixScaffoldPage 同一处理，SIRI 下透明以透出壳层弥散光晕。
    val isSiriStyle = LocalAppearanceSettings.current.visualStyle == AppearanceVisualStyle.SIRI
    val containerColor = if (isSiriStyle) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = containerColor,
        topBar = {
            TopBarBackdrop(backdrop) {
                AdaptiveTopAppBar(
                    title = title,
                    color = topBarColor,
                    navigationIcon = { MiuixBackButton(onClick = onBack) },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().captureForTopBar(backdrop)) {
            WidePageContent { sidePadding ->
                content(paddingValues, scrollBehavior, sidePadding)
            }
        }
    }
}

@Composable
fun MiuixPageBottomSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .height(24.dp)
            .navigationBarsPadding(),
    )
}
