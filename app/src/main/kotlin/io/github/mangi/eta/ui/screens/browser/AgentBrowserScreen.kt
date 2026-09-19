package io.github.mangi.eta.ui.screens.browser

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.browser.AgentBrowserSession
import io.github.mangi.eta.agent.browser.BrowserSessionSnapshot
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import io.github.mangi.eta.ui.components.EtaCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Agent 与用户共享的浏览器会话。
 *
 * 采用一体化紧凑卡片设计：顶部为 48dp 单行多功能胶囊控制栏，
 * 最大化释放网页浏览视口；四周保留卡片边距与圆角，呈现悬浮质感。
 */
@Composable
internal fun AgentBrowserScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val noExternalAppMessage = stringResource(R.string.browser_no_external_app)
    val snapshot by AgentBrowserSession.snapshots.collectAsState()

    var address by remember { mutableStateOf("") }
    var isEditingAddress by remember { mutableStateOf(false) }
    var actionPending by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val addressFocusRequester = remember { FocusRequester() }

    LaunchedEffect(context.applicationContext) {
        AgentBrowserSession.initialize(context.applicationContext)
    }

    LaunchedEffect(snapshot.displayUrl, isEditingAddress) {
        if (!isEditingAddress) {
            address = snapshot.displayUrl
        }
    }

    LaunchedEffect(isEditingAddress) {
        if (isEditingAddress) {
            addressFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = isEditingAddress) {
        isEditingAddress = false
        address = snapshot.displayUrl
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun launchBrowserAction(action: () -> Unit) {
        if (actionPending) return
        actionPending = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } finally {
                actionPending = false
            }
        }
    }

    fun navigate() {
        if (actionPending) return
        val target = if (address == snapshot.displayUrl) {
            snapshot.url
        } else {
            address.trim()
        }
        if (target.isBlank()) return
        isEditingAddress = false
        focusManager.clearFocus()
        keyboard?.hide()
        launchBrowserAction {
            AgentBrowserSession.navigateFromUser(context.applicationContext, target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 12.dp)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        BrowserWindow(
            snapshot = snapshot,
            actionPending = actionPending,
            isEditingAddress = isEditingAddress,
            address = address,
            addressFocusRequester = addressFocusRequester,
            onAddressChange = { address = it },
            onStartEditing = {
                address = snapshot.url.ifBlank { snapshot.displayUrl }
                isEditingAddress = true
            },
            onStopEditing = {
                isEditingAddress = false
                address = snapshot.displayUrl
                focusManager.clearFocus()
                keyboard?.hide()
            },
            onNavigate = ::navigate,
            onCloseBrowser = onBack,
            onBack = { launchBrowserAction { AgentBrowserSession.goBackFromUser() } },
            onForward = { launchBrowserAction { AgentBrowserSession.goForwardFromUser() } },
            onRefresh = {
                if (snapshot.isLoading) {
                    scope.launch(Dispatchers.IO) {
                        AgentBrowserSession.stopFromUser()
                    }
                } else {
                    launchBrowserAction {
                        AgentBrowserSession.reloadFromUser()
                    }
                }
            },
            onOpenExternal = {
                val currentUrl = snapshot.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                if (currentUrl != null) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, currentUrl.toUri()))
                    }.onFailure {
                        Toast.makeText(context, noExternalAppMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReset = { showResetDialog = true },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showResetDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.ui_reset_browser_session_791b36),
            summary = stringResource(R.string.ui_this_will_close_the_current_page_and_clear_eta_brows_1cd331),
            onDismissRequest = { showResetDialog = false },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.browser_reset),
                confirmEnabled = !actionPending,
                onCancel = { showResetDialog = false },
                onConfirm = {
                    showResetDialog = false
                    address = ""
                    launchBrowserAction { AgentBrowserSession.resetFromUser() }
                },
            )
        }
    }
}

/**
 * 统一的悬浮浏览器窗口：顶栏、进度条与网页内容收进同一张卡片。
 */
@Composable
private fun BrowserWindow(
    snapshot: BrowserSessionSnapshot,
    actionPending: Boolean,
    isEditingAddress: Boolean,
    address: String,
    addressFocusRequester: FocusRequester,
    onAddressChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onNavigate: () -> Unit,
    onCloseBrowser: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternal: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        BrowserToolbar(
            snapshot = snapshot,
            actionPending = actionPending,
            isEditingAddress = isEditingAddress,
            address = address,
            addressFocusRequester = addressFocusRequester,
            onAddressChange = onAddressChange,
            onStartEditing = onStartEditing,
            onStopEditing = onStopEditing,
            onNavigate = onNavigate,
            onCloseBrowser = onCloseBrowser,
            onBack = onBack,
            onForward = onForward,
            onRefresh = onRefresh,
            onOpenExternal = onOpenExternal,
            onReset = onReset,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(
                    RoundedCornerShape(
                        bottomStart = CardDefaults.CornerRadius,
                        bottomEnd = CardDefaults.CornerRadius,
                    )
                ),
        ) {
            BrowserWebViewHost(modifier = Modifier.fillMaxSize())
            BrowserLoadingProgress(snapshot)
            BrowserStateOverlay(
                snapshot = snapshot,
                onRetry = onRefresh,
            )
        }
    }
}

/**
 * 单行一体化控制栏：高度 48dp，融合关闭、后退、前进、多功能胶囊与操作菜单。
 */
@Composable
private fun BrowserToolbar(
    snapshot: BrowserSessionSnapshot,
    actionPending: Boolean,
    isEditingAddress: Boolean,
    address: String,
    addressFocusRequester: FocusRequester,
    onAddressChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onNavigate: () -> Unit,
    onCloseBrowser: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternal: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEditingAddress) {
            IconButton(
                onClick = onStopEditing,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.browser_back),
                    modifier = Modifier.size(19.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (address.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.Language,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(addressFocusRequester),
                        singleLine = true,
                        textStyle = MiuixTheme.textStyles.body2.copy(
                            color = MiuixTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onNavigate() },
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (address.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.ui_url_or_domain_name_3ee97a),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (address.isNotEmpty()) {
                        IconButton(
                            onClick = { onAddressChange("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_clear_84fcd7),
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onNavigate,
                enabled = address.isNotBlank() && !actionPending,
                modifier = Modifier
                    .size(36.dp)
                    .alpha(if (address.isNotBlank() && !actionPending) 1f else 0.34f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.ui_access_7f5641),
                    modifier = Modifier.size(19.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        } else {
            IconButton(
                onClick = onCloseBrowser,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(19.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onBack,
                enabled = snapshot.canGoBack && !actionPending,
                modifier = Modifier
                    .size(36.dp)
                    .alpha(if (snapshot.canGoBack && !actionPending) 1f else 0.34f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.browser_back),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .clickable { onStartEditing() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (snapshot.url.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (snapshot.url.startsWith("https://")) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val displayHost = snapshot.host.ifBlank {
                        snapshot.title.ifBlank { stringResource(R.string.ui_url_or_domain_name_3ee97a) }
                    }
                    Text(
                        text = displayHost,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onRefresh,
                    enabled = snapshot.available && (snapshot.isLoading || !actionPending),
                    modifier = Modifier
                        .size(26.dp)
                        .alpha(if (snapshot.available) 1f else 0.34f),
                ) {
                    Icon(
                        imageVector = if (snapshot.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (snapshot.isLoading) {
                            stringResource(R.string.browser_stop_loading)
                        } else {
                            stringResource(R.string.browser_refresh)
                        },
                        modifier = Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onForward,
                enabled = snapshot.canGoForward && !actionPending,
                modifier = Modifier
                    .size(36.dp)
                    .alpha(if (snapshot.canGoForward && !actionPending) 1f else 0.34f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.browser_forward),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            OverlayIconDropdownMenu(
                entry = DropdownEntry(
                    items = listOfNotNull(
                        DropdownItem(
                            text = stringResource(R.string.browser_open_external),
                            enabled = snapshot.available,
                            onClick = onOpenExternal,
                        ),
                        DropdownItem(
                            text = stringResource(R.string.browser_reset_session),
                            enabled = snapshot.available && !actionPending,
                            onClick = onReset,
                        ),
                    ),
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    modifier = Modifier.size(19.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private enum class BrowserOverlay {
    None,
    Empty,
    Loading,
    Failed,
}

/**
 * 加载进度条悬浮在网页顶部，不占布局空间。
 */
@Composable
private fun BoxScope.BrowserLoadingProgress(snapshot: BrowserSessionSnapshot) {
    AnimatedVisibility(
        visible = snapshot.isLoading && snapshot.available,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LinearProgressIndicator(
            progress = snapshot.progress
                .takeIf { it in 1..99 }
                ?.let { it / 100f },
            modifier = Modifier.fillMaxWidth(),
            height = 2.5.dp,
        )
    }
}

/**
 * 内容状态浮层。已有提交页面时导航/刷新保持旧页面可见，只显示顶部进度条，
 * 避免每次加载都用占位页盖住当前内容造成闪烁。
 */
@Composable
private fun BoxScope.BrowserStateOverlay(
    snapshot: BrowserSessionSnapshot,
    onRetry: () -> Unit,
) {
    val overlay = when {
        !snapshot.available -> BrowserOverlay.Empty
        !snapshot.hasCommittedPage && snapshot.error != null -> BrowserOverlay.Failed
        !snapshot.hasCommittedPage -> BrowserOverlay.Loading
        else -> BrowserOverlay.None
    }
    Crossfade(
        targetState = overlay,
        label = "browser_overlay",
        modifier = Modifier.fillMaxSize(),
    ) { state ->
        when (state) {
            BrowserOverlay.Empty -> BrowserEmptyState(modifier = Modifier.fillMaxSize())
            BrowserOverlay.Loading -> BrowserLoadingState(
                host = snapshot.host,
                modifier = Modifier.fillMaxSize(),
            )
            BrowserOverlay.Failed -> BrowserFailedState(
                error = snapshot.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
            BrowserOverlay.None -> Unit
        }
    }
}

@Composable
private fun BrowserOverlayIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .squircleSurface(
                color = tint.copy(alpha = 0.10f),
                cornerRadius = 20.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = tint,
        )
    }
}

/**
 * 占位状态覆盖在 WebView 之上，拦截触摸，避免用户点到尚未完成渲染的页面。
 */
private fun Modifier.consumeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change ->
                change.consume()
            }
        }
    }
}

@Composable
private fun BrowserEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .consumeTouches()
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrowserOverlayIcon(
            icon = Icons.Rounded.Language,
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ui_the_browser_has_not_opened_the_web_page_yet_31e095),
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.ui_enter_the_url_in_the_address_bar_or_let_the_agent_br_e2ae90),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BrowserLoadingState(
    host: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .consumeTouches()
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.primary,
            size = 34.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (host.isBlank()) stringResource(R.string.browser_opening) else stringResource(R.string.browser_opening_host, host),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BrowserFailedState(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .consumeTouches()
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrowserOverlayIcon(
            icon = Icons.Rounded.GppMaybe,
            tint = StatusError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ui_the_webpage_cannot_be_opened_3db06d),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        TextButton(
            text = stringResource(R.string.ui_reload_5982c4),
            onClick = onRetry,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun BrowserWebViewHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backgroundColor = MiuixTheme.colorScheme.surfaceContainer.toArgb()
    val container = remember(context) {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(backgroundColor)
        }
    }
    DisposableEffect(container, context) {
        AgentBrowserSession.attachTo(container, context)
        onDispose { AgentBrowserSession.detachFrom(container) }
    }
    AndroidView(
        factory = { container },
        update = { view -> view.setBackgroundColor(backgroundColor) },
        modifier = modifier,
    )
}
