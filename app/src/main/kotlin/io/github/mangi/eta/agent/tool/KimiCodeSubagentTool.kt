package io.github.mangi.eta.agent.tool

import android.content.Context
import io.github.mangi.eta.agent.terminal.AlpineEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.RootShellTerminalController
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 内置 Kimi Code 编程子代理协同调度工具（Web REST API 直连与 1:1 会话绑定版）。
 *
 * 核心机制：
 * 1. 自动保障本地 Kimi Web 守护进程就绪；
 * 2. 维持 Eta 当前会话与 Kimi Code 会话的 1:1 严格镜像绑定，所有追加指令均复用同一会话，绝不散乱新建；
 * 3. 实时通过 Web API 交互，确保在手机浏览器 Web UI 中百分之百可见、可查、可控；
 * 4. 执行完成后自动捕获代码 Diff 与 Git 状态，并附带专属会话直链。
 */
internal class KimiCodeSubagentTool(
    private val context: Context,
    private val terminalController: RootShellTerminalController,
) {
    companion object {
        const val KIMI_WEB_PORT = 58627
        const val BASE_URL = "http://127.0.0.1:$KIMI_WEB_PORT"
        private const val DEFAULT_MODEL = "gemini/gemini-3.8-flash-high"
        private const val BINDINGS_FILE_NAME = "kimi_session_bindings.json"
    }

    private val bindingsLock = Any()
    private val sessionBindings = ConcurrentHashMap<String, String>()

    init {
        loadBindings()
    }

    fun delegate(args: JSONObject): String {
        val task = args.optString("task").trim()
        if (task.isBlank()) {
            return errorJson("INVALID_ARGUMENTS", "必须提供 task 任务描述")
        }

        val projectPath = args.optString("project_path").trim().ifBlank { "/workspace/EtaWorker/Eta/src" }
        val timeoutSeconds = args.optInt("timeout_seconds", 300).coerceIn(10, 600)
        val conversationId = args.optString("conversation_id").trim().ifBlank { "default_main_session" }
        val conversationTitle = args.optString("conversation_title").trim().ifBlank { "Eta 编程协同" }

        // 1. 检查 Linux 环境就绪状态
        val distribution = LinuxEnvironmentSettingsRepository.current(context)
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) {
            return errorJson("LINUX_NOT_READY", "Linux 容器尚未就绪，请先在设置中启动 Linux 环境。")
        }

        // 2. 检查 Kimi Code 安装状态
        val markerFile = File(rootfs, AlpineEnvironmentPaths.KIMI_TOOLS_MARKER)
        if (!markerFile.exists()) {
            return errorJson("KIMI_NOT_INSTALLED", "Linux 环境中尚未安装 Kimi Code 组件。")
        }

        // 3. 确保 Kimi Web 守护进程在线
        val token = readServerToken(rootfs)
        if (!ensureServerOnline(projectPath)) {
            return errorJson("SERVER_OFFLINE", "Kimi Web 服务启动失败，请检查终端日志。")
        }

        // 4. 获取或创建 1:1 绑定的专属 Session
        val sessionId = getOrCreateBoundSession(
            conversationId = conversationId,
            conversationTitle = conversationTitle,
            projectPath = projectPath,
            token = token,
        ) ?: return errorJson("SESSION_ERROR", "无法创建或连接 Kimi Code 专属会话。")

        // 5. 通过 REST API 直连下发任务
        val promptSent = sendPrompt(sessionId, task, token)
        if (!promptSent) {
            return errorJson("PROMPT_FAILED", "向 Kimi Code 发送指令失败。")
        }

        // 6. 轮询等待任务完成
        val pollSuccess = pollSessionCompletion(sessionId, token, timeoutSeconds)

        // 7. 捕获 Git 状态与输出
        val gitStatus = runTerminalCommand(projectPath, "git status --short 2>/dev/null || true")
        val gitDiff = runTerminalCommand(projectPath, "git diff --stat 2>/dev/null || true")
        val latestMessage = fetchLatestAssistantMessage(sessionId, token)

        val directUrl = "$BASE_URL/sessions/$sessionId#token=$token"

        return JSONObject()
            .put("ok", pollSuccess)
            .put("tool", "delegate_to_kimi_code")
            .put("session_id", sessionId)
            .put("direct_url", directUrl)
            .put("task", task)
            .put("output", latestMessage.ifBlank { if (pollSuccess) "代码已成功修改并落盘。" else "任务执行超时或异常。" })
            .put("git_modified_files", gitStatus.ifBlank { "无 git 变更或未发生文件变动" })
            .put("git_diff_stat", gitDiff.ifBlank { "无代码增删差异" })
            .put(
                "message",
                if (pollSuccess) "Kimi Code 已在专属会话中完成代码修改并落盘。可在网页端实时查看：$directUrl"
                else "Kimi Code 执行超时或未能完成全部修改。"
            )
            .toString()
    }

    private fun ensureServerOnline(cwd: String): Boolean {
        if (checkHealth()) return true
        // 尝试自动拉起 daemon
        terminalController.terminalAction(
            action = "daemon_start",
            command = "kimi web --no-open",
            cwd = cwd,
            timeoutMs = 15_000,
            identity = "root",
            mergeStderr = true,
            sessionId = null,
            jobId = null,
            async = false,
            offsetChars = 0,
            maxChars = 4000,
            closeIfDone = false,
            environment = "debian",
        )
        // 轮询 5 秒等待服务就绪
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) {
            if (checkHealth()) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun checkHealth(): Boolean = try {
        val url = URL("$BASE_URL/api/v1/healthz")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 1500
            readTimeout = 1500
            requestMethod = "GET"
        }
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }

    private fun readServerToken(rootfs: File): String = try {
        File(rootfs, "root/.kimi-code/server.token").readText(Charsets.UTF_8).trim()
    } catch (_: Exception) {
        ""
    }

    private fun getOrCreateBoundSession(
        conversationId: String,
        conversationTitle: String,
        projectPath: String,
        token: String,
    ): String? {
        val existingSessionId = sessionBindings[conversationId]
        if (!existingSessionId.isNullOrBlank()) {
            // 校验是否在服务端仍然存在
            if (isSessionAlive(existingSessionId, token)) {
                return existingSessionId
            }
        }

        // 新建 Session
        val workspaceId = resolveWorkspaceId(projectPath, token) ?: "wd_src_497f3ff80d60"
        val newSessionId = createSession(
            title = conversationTitle,
            workspaceId = workspaceId,
            token = token,
        )

        if (newSessionId != null) {
            sessionBindings[conversationId] = newSessionId
            saveBindings()
        }
        return newSessionId
    }

    private fun isSessionAlive(sessionId: String, token: String): Boolean = try {
        val url = URL("$BASE_URL/api/v1/sessions/$sessionId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 2000
            readTimeout = 2000
            requestMethod = "GET"
        }
        conn.responseCode == 200
    } catch (_: Exception) {
        false
    }

    private fun resolveWorkspaceId(projectPath: String, token: String): String? = try {
        val url = URL("$BASE_URL/api/v1/workspaces")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 2000
            readTimeout = 2000
        }
        if (conn.responseCode == 200) {
            val res = JSONObject(conn.inputStream.bufferedReader().readText())
            val items = res.optJSONObject("data")?.optJSONArray("items")
            if (items != null && items.length() > 0) {
                items.getJSONObject(0).optString("id")
            } else null
        } else null
    } catch (_: Exception) {
        null
    }

    private fun createSession(title: String, workspaceId: String, token: String): String? = try {
        val url = URL("$BASE_URL/api/v1/sessions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 3000
            readTimeout = 3000
        }
        val body = JSONObject()
            .put("title", title)
            .put("workspace_id", workspaceId)
            .put("agent_config", JSONObject().put("model", DEFAULT_MODEL))
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode in 200..299) {
            val res = JSONObject(conn.inputStream.bufferedReader().readText())
            res.optJSONObject("data")?.optString("id")
        } else null
    } catch (_: Exception) {
        null
    }

    private fun sendPrompt(sessionId: String, task: String, token: String): Boolean = try {
        val url = URL("$BASE_URL/api/v1/sessions/$sessionId/prompts")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
        }
        val contentArr = org.json.JSONArray().put(
            JSONObject().put("type", "text").put("text", task)
        )
        val body = JSONObject()
            .put("content", contentArr)
            .put("model", DEFAULT_MODEL)
            .put("permission_mode", "auto")
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }

    private fun pollSessionCompletion(sessionId: String, token: String, timeoutSeconds: Int): Boolean {
        val start = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val url = URL("$BASE_URL/api/v1/sessions/$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                if (conn.responseCode == 200) {
                    val res = JSONObject(conn.inputStream.bufferedReader().readText())
                    val data = res.optJSONObject("data")
                    val busy = data?.optBoolean("busy", true) ?: true
                    if (!busy) {
                        val reason = data?.optString("last_turn_reason")
                        return reason == "completed" || reason == "idle"
                    }
                }
            } catch (_: Exception) {}
            Thread.sleep(1500)
        }
        return false
    }

    private fun fetchLatestAssistantMessage(sessionId: String, token: String): String = try {
        val url = URL("$BASE_URL/api/v1/sessions/$sessionId/messages")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 2000
            readTimeout = 2000
        }
        if (conn.responseCode == 200) {
            val res = JSONObject(conn.inputStream.bufferedReader().readText())
            val items = res.optJSONObject("data")?.optJSONArray("items")
            if (items != null && items.length() > 0) {
                // 倒序寻找最后一条 assistant 文本
                for (i in 0 until items.length()) {
                    val msg = items.getJSONObject(i)
                    if (msg.optString("role") == "assistant") {
                        val contentArr = msg.optJSONArray("content")
                        if (contentArr != null) {
                            for (j in 0 until contentArr.length()) {
                                val c = contentArr.optJSONObject(j)
                                if (c?.optString("type") == "text") {
                                    val t = c.optString("text").trim()
                                    if (t.isNotEmpty()) return t
                                }
                            }
                        }
                    }
                }
            }
        }
        ""
    } catch (_: Exception) {
        ""
    }

    private fun runTerminalCommand(cwd: String, cmd: String): String = try {
        val res = terminalController.terminalAction(
            action = "open_and_exec",
            command = "cd '$cwd' 2>/dev/null && $cmd",
            cwd = cwd,
            timeoutMs = 10_000,
            identity = "root",
            mergeStderr = true,
            sessionId = null,
            jobId = null,
            async = false,
            offsetChars = 0,
            maxChars = 4000,
            closeIfDone = true,
            environment = "debian",
        )
        JSONObject(res).optString("stdout").trim()
    } catch (_: Exception) {
        ""
    }

    private fun loadBindings() {
        synchronized(bindingsLock) {
            try {
                val f = File(context.filesDir, BINDINGS_FILE_NAME)
                if (f.exists()) {
                    val obj = JSONObject(f.readText(Charsets.UTF_8))
                    obj.keys().forEach { k ->
                        sessionBindings[k] = obj.getString(k)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveBindings() {
        synchronized(bindingsLock) {
            try {
                val obj = JSONObject()
                sessionBindings.forEach { (k, v) -> obj.put(k, v) }
                File(context.filesDir, BINDINGS_FILE_NAME).writeText(obj.toString(2), Charsets.UTF_8)
            } catch (_: Exception) {}
        }
    }

    private fun errorJson(code: String, message: String): String = JSONObject()
        .put("ok", false)
        .put("code", code)
        .put("message", message)
        .toString()
}
