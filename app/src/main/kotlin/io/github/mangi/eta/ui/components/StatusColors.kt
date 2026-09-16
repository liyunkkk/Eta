package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.PermissionStatusUi
import io.github.mangi.eta.ui.model.RunStatusUi
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 语义状态色
val StatusSuccess = Color(0xFF00BD13)
val StatusWarning = Color(0xFFFFB200)
val StatusError: Color @Composable get() = MiuixTheme.colorScheme.error
val StatusRunning: Color @Composable get() = MiuixTheme.colorScheme.primary
val StatusIdle: Color @Composable get() = MiuixTheme.colorScheme.onSurfaceVariantSummary

// ── RunStatusUi 映射 ──────────────────────────────────────────────────

@Composable
fun RunStatusUi.color(): Color = when (this) {
    RunStatusUi.Running -> StatusRunning
    RunStatusUi.Success -> StatusSuccess
    RunStatusUi.Failed -> StatusError
    RunStatusUi.Cancelled -> StatusIdle
}

@Composable
fun RunStatusUi.label(): String = stringResource(when (this) {
    RunStatusUi.Running -> R.string.tool_status_running
    RunStatusUi.Success -> R.string.tool_status_success
    RunStatusUi.Failed -> R.string.tool_status_failed
    RunStatusUi.Cancelled -> R.string.status_cancelled
})

// ── PermissionStatusUi 映射 ───────────────────────────────────────────
@Composable
fun PermissionStatusUi.color(): Color = when (this) {
    PermissionStatusUi.Available -> StatusIdle
    PermissionStatusUi.Warning -> StatusWarning
    PermissionStatusUi.Missing -> StatusError
    PermissionStatusUi.Disabled -> StatusIdle
}
@Composable
fun PermissionStatusUi.label(): String = stringResource(when (this) {
    PermissionStatusUi.Available -> R.string.status_ready
    PermissionStatusUi.Warning -> R.string.status_needs_attention
    PermissionStatusUi.Missing -> R.string.status_unauthorized
    PermissionStatusUi.Disabled -> R.string.status_disabled
})
// ── TaskStatusUi 映射 (Miuix 统一设计语言) ─────────────────────────────
@Composable
fun io.github.mangi.eta.ui.model.TaskStatusUi.color(): Color = when (this) {
    io.github.mangi.eta.ui.model.TaskStatusUi.Pending -> StatusIdle
    io.github.mangi.eta.ui.model.TaskStatusUi.Running -> StatusRunning
    io.github.mangi.eta.ui.model.TaskStatusUi.Completed -> StatusSuccess
    io.github.mangi.eta.ui.model.TaskStatusUi.Failed -> StatusError
    io.github.mangi.eta.ui.model.TaskStatusUi.Skipped -> StatusWarning
}
@Composable
fun io.github.mangi.eta.ui.model.TaskStatusUi.label(): String = when (this) {
    io.github.mangi.eta.ui.model.TaskStatusUi.Pending -> "待执行"
    io.github.mangi.eta.ui.model.TaskStatusUi.Running -> "进行中"
    io.github.mangi.eta.ui.model.TaskStatusUi.Completed -> "已完成"
    io.github.mangi.eta.ui.model.TaskStatusUi.Failed -> "失败"
    io.github.mangi.eta.ui.model.TaskStatusUi.Skipped -> "已跳过"
}
