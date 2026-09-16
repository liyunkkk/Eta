package io.github.mangi.eta.agent.tool

import android.content.Context
import io.github.mangi.eta.agent.terminal.AlpineEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.RootShellTerminalController
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import org.json.JSONObject
import java.io.File

/**
 * 内置 Kimi Code 编程子代理协同调度工具。
 * 将复杂的多文件重构、代码修改与脚本实现任务委派给 Linux 容器中的 Kimi Code 运行，
 * 并自动捕获 Git 状态与执行差异回传给主智能体。
 */
internal class KimiCodeSubagentTool(
    private val context: Context,
    private val terminalController: RootShellTerminalController,
) {

    fun delegate(args: JSONObject): String {
        val task = args.optString("task").trim()
        if (task.isBlank()) {
            return errorJson("INVALID_ARGUMENTS", "必须提供 task 任务描述")
        }

        val projectPath = args.optString("project_path").trim().ifBlank { "/workspace" }
        val timeoutSeconds = args.optInt("timeout_seconds", 120).coerceIn(10, 600)

        // 1. 检查 Linux 环境就绪状态
        val distribution = LinuxEnvironmentSettingsRepository.current(context)
        val terminalEnv = distribution.terminalEnvironment
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution)

        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) {
            return errorJson(
                "LINUX_NOT_READY",
                "Linux PRoot 容器环境尚未就绪，无法启动 Kimi Code 子代理。请先在应用内配置并启动 Linux 环境。"
            )
        }

        // 2. 检查 Kimi Code 安装状态
        val markerFile = File(rootfs, AlpineEnvironmentPaths.KIMI_TOOLS_MARKER)
        if (!markerFile.exists()) {
            return errorJson(
                "KIMI_NOT_INSTALLED",
                "Linux 环境中尚未安装 Kimi Code 组件。请在 Eta 的 Linux 环境管理中安装 kimi 扩展包后重试。"
            )
        }

        val identity = TerminalRuntime.defaultIdentity(terminalEnv, rootfs.absolutePath)

        // 3. 构造无头执行脚本并执行
        // 将 task 写入临时文件以防特殊字符破坏 bash 命令
        val escapedTask = JSONObject.quote(task)
        val script = buildString {
            append("cd ").append(shellQuote(projectPath)).append(" 2>/dev/null || cd /workspace\n")
            append("echo ").append(escapedTask).append(" > /tmp/.kimi_subagent_task.txt\n")
            append("export TERM=dumb NO_COLOR=1\n")
            append("kimi --prompt \"$(cat /tmp/.kimi_subagent_task.txt)\" --non-interactive 2>&1 || kimi -p \"$(cat /tmp/.kimi_subagent_task.txt)\" 2>&1\n")
            append("EXIT_CODE=$?\n")
            append("echo '===GIT_STATUS_BEGIN==='\n")
            append("git status --short 2>/dev/null || true\n")
            append("echo '===GIT_DIFF_STAT==='\n")
            append("git diff --stat 2>/dev/null || true\n")
            append("echo '===SUBAGENT_END==='\n")
            append("exit \$EXIT_CODE\n")
        }

        val terminalJsonStr = terminalController.terminalAction(
            action = "open_and_exec",
            command = script,
            cwd = projectPath,
            timeoutMs = timeoutSeconds * 1000,
            identity = identity,
            mergeStderr = true,
            sessionId = null,
            jobId = null,
            async = false,
            offsetChars = 0,
            maxChars = 64_000,
            closeIfDone = true,
            environment = terminalEnv.wireName,
        )

        val termResult = runCatching { JSONObject(terminalJsonStr) }.getOrNull()
        val stdout = termResult?.optString("stdout").orEmpty()
        val exitCode = termResult?.optInt("exit_code", -1) ?: -1
        val timedOut = termResult?.optBoolean("timed_out", false) ?: false

        // 解析输出中的 Git 状态
        val gitStatus = extractBetween(stdout, "===GIT_STATUS_BEGIN===", "===GIT_DIFF_STAT===").trim()
        val gitDiff = extractBetween(stdout, "===GIT_DIFF_STAT===", "===SUBAGENT_END===").trim()
        val cleanOutput = stdout.substringBefore("===GIT_STATUS_BEGIN===").trim()

        return JSONObject()
            .put("ok", exitCode == 0)
            .put("tool", "delegate_to_kimi_code")
            .put("task", task)
            .put("project_path", projectPath)
            .put("exit_code", exitCode)
            .put("timed_out", timedOut)
            .put("output", cleanOutput.takeLast(4000))
            .put("git_modified_files", gitStatus.ifBlank { "无 git 变更或非 git 仓库" })
            .put("git_diff_stat", gitDiff.ifBlank { "无代码增删差异" })
            .put(
                "message",
                if (exitCode == 0) "Kimi Code 子代理执行完成并已交付变更。"
                else "Kimi Code 子代理执行遇到异常 (ExitCode: $exitCode)。"
            )
            .toString()
    }

    private fun extractBetween(source: String, startTag: String, endTag: String): String {
        val startIdx = source.indexOf(startTag)
        if (startIdx == -1) return ""
        val contentStart = startIdx + startTag.length
        val endIdx = source.indexOf(endTag, contentStart)
        return if (endIdx != -1) source.substring(contentStart, endIdx) else source.substring(contentStart)
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun errorJson(code: String, message: String): String =
        JSONObject()
            .put("ok", false)
            .put("code", code)
            .put("message", message)
            .toString()
}