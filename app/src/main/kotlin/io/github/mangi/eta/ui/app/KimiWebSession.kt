package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.kimi.KimiWebEndpoint
import io.github.mangi.eta.agent.terminal.DaemonLogsResult
import io.github.mangi.eta.agent.terminal.DaemonStartResult
import io.github.mangi.eta.agent.terminal.DetachedTaskStatus
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import kotlinx.coroutines.delay

/** Kimi 启动和复用的事务边界：只有本次创建且未打开浏览器的任务会被回收。 */
internal class KimiWebSession(
    private val tasks: Tasks,
    private val openUrl: (String) -> Boolean,
    private val waitAttempts: Int = 30,
    private val waitIntervalMs: Long = 500,
) {
    interface Tasks {
        fun list(): List<DetachedTaskStatus>
        fun start(environment: TerminalEnvironment, identity: String): DaemonStartResult
        fun logs(id: String): DaemonLogsResult
        fun stop(id: String)
    }

    suspend fun launch(environment: TerminalEnvironment, identity: String, backend: LinuxExecutionBackend): KimiWebLaunchResult {
        var createdTaskId: String? = null
        var opened = false
        try {
            val existing = tasks.list().firstOrNull {
                it.running && it.task.environment == environment && it.task.identity == identity &&
                    it.task.backend == backend && it.task.command.trim() in setOf(COMMAND, "kimi web")
            }
            val taskId = existing?.task?.id ?: when (val started = tasks.start(environment, identity)) {
                is DaemonStartResult.Started -> started.task.id.also { createdTaskId = it }
                is DaemonStartResult.Failed -> return KimiWebLaunchResult.Failed(started.code)
            }
            repeat(waitAttempts) {
                val status = tasks.list().firstOrNull { it.task.id == taskId }
                if (status == null || !status.running) return KimiWebLaunchResult.Failed("KIMI_EXITED")
                val logs = tasks.logs(taskId)
                if (!logs.ok) return KimiWebLaunchResult.Failed(logs.code.ifBlank { "LOGS_UNAVAILABLE" })
                val url = addressFromLogs(logs.text)
                if (url != null) {
                    opened = openUrl(url)
                    return if (opened) KimiWebLaunchResult.Opened(url) else KimiWebLaunchResult.Failed("BROWSER_UNAVAILABLE")
                }
                delay(waitIntervalMs)
            }
            return KimiWebLaunchResult.Failed("URL_TIMEOUT")
        } finally {
            if (!opened) createdTaskId?.let(tasks::stop)
        }
    }

    companion object {
        const val COMMAND = "kimi web --no-open"

        /**
         * 从启动横幅解析带 token 的可打开地址。
         *
         * 横幅经 chalk 分色，`#token=` 片段前会插入 ANSI 转义序列，必须先剥离再匹配；
         * 同时服务端在端口被占用时会向上递增重试，因此不能假定固定端口。
         */
        fun addressFromLogs(text: String): String? = KimiWebEndpoint.parseOpenableUrl(text)

        /** 解析完整端点（origin + token），供 REST 直连使用。 */
        fun endpointFromLogs(text: String): KimiWebEndpoint.Endpoint? = KimiWebEndpoint.parse(text)
    }
}
