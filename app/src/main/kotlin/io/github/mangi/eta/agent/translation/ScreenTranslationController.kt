package io.github.mangi.eta.agent.translation

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.model.AgentConversationCodec
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentModelRetry
import io.github.mangi.eta.agent.model.ProviderClientFactory
import io.github.mangi.eta.agent.model.ProviderRequest
import io.github.mangi.eta.agent.model.ProviderRequestPurpose
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.repository.LanguageSettingsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * 屏幕翻译控制器：屏幕文本采集（无障碍节点树）→ 聚合 → 批量翻译（provider 直调）→ 覆盖层渲染。
 *
 * v1 策略：
 * - 文本来源为无障碍节点 text/desc + boundsInScreen，零截图零 OCR，坐标精确；
 * - 翻译直调 AgentProviderClient（复用 COMPACTION purpose 的轻量通道，无工具、无会话），
 *   避免为每屏翻译起一次 AgentRuntimeService 进程往返；
 * - 文本指纹（去空白哈希）缓存，未变化的区块直接复用上次译文，滚动/局部刷新只翻译增量；
 * - 单一工作线程串行处理，新采集请求到达时丢弃过期帧，防止排队堆积。
 */
internal object ScreenTranslationController {

    private const val TAG_PREFIX = "ScreenTranslation"

    /** 单次采集的最大节点数，与 captureNodeSnapshot 上限一致。 */
    private const val MAX_NODES = 120

    /** 参与翻译的单块文本最小长度：低于该值的碎字（如单个图标 label）不翻译。 */
    private const val MIN_TEXT_LENGTH = 2

    /** 单次批量翻译的文本总量上限（字符），防止整屏超长文本一次撑爆请求。 */
    private const val MAX_BATCH_CHARS = 6000

    /** 聚合：同一行内相邻块合并的垂直容差（dp）。 */
    private const val LINE_MERGE_GAP_DP = 8f

    /** 缓存上限：超过后清空重建，防止长会话内存增长。 */
    private const val CACHE_MAX_ENTRIES = 512

    /** 内容变化后的采集防抖（ms）。 */
    private const val CAPTURE_DEBOUNCE_MS = 350L

    /** 采集无障碍事件驱动重试间隔：无障碍服务未连接时退避重试。 */
    private const val SERVICE_RETRY_MS = 2_000L

    private val started = AtomicBoolean(false)
    private val renderPending = AtomicBoolean(false)
    private val frameSeq = AtomicLong(0)

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var mainHandler: Handler? = null
    private var appContext: Context? = null

    /** 译文缓存：文本指纹 -> 译文。 */
    private val translationCache = ConcurrentHashMap<String, String>()

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        mainHandler = Handler(android.os.Looper.getMainLooper())
        val thread = HandlerThread("eta-screen-translation").apply { start() }
        workerThread = thread
        workerHandler = Handler(thread.looper)
        AndroidAgentLogger.info("$TAG_PREFIX started")
        scheduleCapture(delayMs = 0)
    }

    fun stop(context: Context) {
        if (!started.compareAndSet(true, false)) return
        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        mainHandler = null
        appContext = null
        translationCache.clear()
        ScreenTranslationOverlayService.hide(context)
        AndroidAgentLogger.info("$TAG_PREFIX stopped")
    }

    fun isRunning(): Boolean = started.get()

    /** 内容变化触发重采集（防抖）。 */
    fun onScreenContentChanged() {
        if (!started.get()) return
        scheduleCapture()
    }

    /** 手动刷新入口。 */
    fun requestRefresh() {
        scheduleCapture(delayMs = 0)
    }

    private val pendingCapture = Runnable { runCapture() }

    private fun scheduleCapture(delayMs: Long = CAPTURE_DEBOUNCE_MS) {
        val handler = workerHandler ?: return
        handler.removeCallbacks(pendingCapture)
        if (delayMs <= 0) {
            handler.post(pendingCapture)
        } else {
            handler.postDelayed(pendingCapture, delayMs)
        }
    }

    // ------------------------------------------------------------------
    // 采集与聚合
    // ------------------------------------------------------------------

    private fun runCapture() {
        if (!started.get()) return
        val service = AgentAccessibilityService.current() ?: run {
            AndroidAgentLogger.warnThrottled("screen_translation_no_service") {
                "Screen translation capture skipped: accessibility service not connected"
            }
            // 服务未连接不重试采集（等待事件驱动或手动刷新），避免空转
            return
        }
        val seq = frameSeq.incrementAndGet()
        val startedAt = SystemClock.elapsedRealtime()
        val snapshot = runCatching { service.captureNodeSnapshot(MAX_NODES) }
            .getOrElse { throwable ->
                AndroidAgentLogger.warnThrottled("screen_translation_capture_failed") {
                    "Screen translation capture failed: ${throwable.javaClass.simpleName}"
                }
                return
            }
        val packageName = snapshot?.packageName.orEmpty()
        if (packageName.isBlank() || packageName == SELF_PACKAGE ||
            packageName == SYSTEM_UI_PACKAGE
        ) {
            // 不翻译自家界面与系统 UI
            return
        }
        val rawNodes = snapshot?.nodes.orEmpty()
        if (rawNodes.isEmpty()) {
            clearOverlay()
            return
        }
        val density = appContext?.resources?.displayMetrics?.density ?: 1f
        val blocks = aggregateNodes(rawNodes, density)
        if (blocks.isEmpty()) {
            clearOverlay()
            return
        }
        lastCaptureAt = SystemClock.elapsedRealtime()
        AndroidAgentLogger.debug {
            "$TAG_PREFIX action=capture seq=$seq package=$packageName " +
                "blocks=${blocks.size} nodes=${rawNodes.size} " +
                "elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}"
        }
        translateAndRender(blocks, seq)
    }

    @Volatile
    private var lastCaptureAt: Long = 0

    /**
     * 从无障碍节点聚合出可翻译文本块：
     * 1. 只取有文本的可见节点（text / desc）；
     * 2. 过滤过短文本、无字母噪声（纯数字/符号）、密码框与输入框；
     * 3. 同行相邻块（垂直中心接近）合并为一行，保持阅读顺序。
     */
    private fun aggregateNodes(
        nodes: List<AgentAccessibilityService.UiNode>,
        density: Float,
    ): List<ScreenTranslationBlock> {
        val candidates = ArrayList<ScreenTranslationBlock>(nodes.size)
        val seenTexts = HashSet<String>()
        for (node in nodes) {
            if (node.password || !node.enabled) continue
            if (node.editable) continue
            val text = pickNodeText(node) ?: continue
            if (text.length < MIN_TEXT_LENGTH) continue
            if (isNoiseText(text)) continue
            if (!seenTexts.add(text)) continue
            val bounds = node.bounds
            if (bounds.isEmpty) continue
            candidates.add(ScreenTranslationBlock(source = text, boundsInScreen = Rect(bounds)))
        }
        if (candidates.size <= 1) return candidates
        return mergeInline(candidates, density)
            .sortedWith(compareBy({ it.boundsInScreen.top }, { it.boundsInScreen.left }))
    }

    private fun pickNodeText(node: AgentAccessibilityService.UiNode): String? {
        val text = node.text.trim()
        if (text.isNotEmpty()) return text
        val desc = node.desc.trim()
        if (desc.isNotEmpty()) return desc
        return null
    }

    private fun isNoiseText(text: String): Boolean {
        var letters = 0
        for (ch in text) {
            if (ch.isLetter()) letters++
        }
        return letters == 0
    }

    /**
     * 同行合并：垂直中心差在容差内且 bounds 垂直投影重叠的相邻块合并。
     * 合并逻辑只依据几何信息，与文本内容无关。
     */
    private fun mergeInline(
        blocks: List<ScreenTranslationBlock>,
        density: Float,
    ): List<ScreenTranslationBlock> {
        if (blocks.size <= 1) return blocks
        val gap = (LINE_MERGE_GAP_DP * density).toInt()
        val sortedByTop = blocks.sortedBy { it.boundsInScreen.top }
        val rows = ArrayList<ScreenTranslationBlock>(blocks.size)
        var current: ScreenTranslationBlock? = null
        for (block in sortedByTop) {
            val head = current
            if (head == null) {
                current = block
                continue
            }
            val sameLine = abs(head.boundsInScreen.centerY() - block.boundsInScreen.centerY()) <= gap &&
                abs(head.boundsInScreen.height() - block.boundsInScreen.height()) <= gap * 2
            if (sameLine) {
                current = head.copy(
                    source = head.source + " " + block.source,
                    boundsInScreen = Rect(
                        minOf(head.boundsInScreen.left, block.boundsInScreen.left),
                        minOf(head.boundsInScreen.top, block.boundsInScreen.top),
                        maxOf(head.boundsInScreen.right, block.boundsInScreen.right),
                        maxOf(head.boundsInScreen.bottom, block.boundsInScreen.bottom),
                    ),
                )
            } else {
                rows.add(head)
                current = block
            }
        }
        current?.let(rows::add)
        return rows
    }

    // ------------------------------------------------------------------
    // 翻译
    // ------------------------------------------------------------------

    private fun translateAndRender(blocks: List<ScreenTranslationBlock>, seq: Long) {
        val workBlocks = blocks.map { it }.toMutableList()
        // 1. 缓存命中的直接复用
        val pending = ArrayList<Pair<Int, String>>() // index -> fingerprint
        blocks.forEachIndexed { index, block ->
            val fp = fingerprint(block.source)
            val cached = translationCache[fp]
            if (cached != null) {
                workBlocks[index] = block.copy(translated = cached)
            } else {
                pending.add(index to fp)
            }
        }
        // 2. 全部命中：直接渲染
        if (pending.isEmpty()) {
            renderOnMain(workBlocks)
            return
        }
        // 3. 分批翻译：单批不超过 MAX_BATCH_CHARS 字符
        var batchStart = 0
        while (batchStart < pending.size) {
            if (!started.get()) return
            var chars = 0
            var batchEnd = batchStart
            while (batchEnd < pending.size) {
                val nextChars = blocks[pending[batchEnd].first].source.length
                if (chars + nextChars > MAX_BATCH_CHARS && batchEnd > batchStart) break
                chars += nextChars
                batchEnd++
            }
            val batch = pending.subList(batchStart, batchEnd)
            val ok = runTranslationBatch(blocks, batch, workBlocks)
            if (!ok) return
            // 每批渲染一次，让用户尽早看到部分译文
            renderOnMain(workBlocks)
            batchStart = batchEnd
        }
        AndroidAgentLogger.debug {
            "$TAG_PREFIX action=translate_done seq=$seq total=${blocks.size} translated=${pending.size}"
        }
    }

    /**
     * 执行一次批量翻译：构建编号 JSON 列表 prompt，解析响应。
     * 返回 false 表示本帧放弃（服务停止/模型失败）。
     */
    private fun runTranslationBatch(
        blocks: List<ScreenTranslationBlock>,
        batch: List<Pair<Int, String>>,
        workBlocks: MutableList<ScreenTranslationBlock>,
    ): Boolean {
        val context = appContext ?: return false
        val config = runCatching { AgentModelClient.loadConfig() }.getOrNull()
            ?: run {
                postOverlayError("config_unavailable")
                return false
            }
        val provider = ProviderClientFactory.getClient(config)
        val controller = AgentRunController()
        val retry = AgentModelRetry()
        val messages = JSONArray()
        messages.put(systemPrompt(context))
        val items = JSONArray()
        batch.forEach { (index, _) ->
            items.put(
                JSONObject()
                    .put("id", index)
                    .put("text", blocks[index].source),
            )
        }
        val payload = JSONObject().put("items", items)
        messages.put(AgentConversationCodec.userTextMessage(payload.toString()))
        val request = ProviderRequest(
            config = config.copy(
                hostedWebSearchEnabled = false,
                extraBodyJson = "",
                customBody = emptyList(),
            ),
            messages = messages,
            tools = JSONArray(),
            purpose = ProviderRequestPurpose.COMPACTION,
        )
        val response = try {
            retry.complete(
                initialRound = 0,
                request = request,
                provider = provider,
                controller = controller,
                onEvent = {},
                onProviderEvent = { _, _ -> },
                discardAttemptReasoning = {},
            ).response
        } catch (failure: Exception) {
            AndroidAgentLogger.warnThrottled("screen_translation_model_failed") {
                "Screen translation model call failed: ${failure.message?.take(120)}"
            }
            postOverlayError("model_failed")
            return false
        }
        val content = response.assistantMessage.optString("content").trim()
        if (content.isBlank() || content == "null") {
            postOverlayError("empty_response")
            return false
        }
        val parsed = runCatching { parseTranslations(content) }.getOrNull()
        if (parsed == null) {
            AndroidAgentLogger.warnThrottled("screen_translation_parse_failed") {
                "Screen translation response unparseable: ${content.take(80)}"
            }
            postOverlayError("parse_failed")
            return false
        }
        for ((index, translation) in parsed) {
            if (translation.isBlank()) continue
            val fp = batch.firstOrNull { it.first == index }?.second ?: continue
            translationCache[fp] = translation
            if (translationCache.size > CACHE_MAX_ENTRIES) {
                // 容量保护：超限全清后重建（缓存价值主要在短会话滚动场景）
                translationCache.clear()
                translationCache[fp] = translation
            }
            workBlocks[index] = workBlocks[index].copy(translated = translation)
        }
        return true
    }

    /**
     * 解析模型返回。兼容三种格式：
     * 1. JSON 数组 [{"id":n,"text":"..."}]；
     * 2. JSON 对象 {"items":[{"id":n,"text":"..."}]}；
     * 3. 行格式 “id|译文” 每行一条（模型未按格式输出时的兜底）。
     */
    private fun parseTranslations(content: String): List<Pair<Int, String>>? {
        val trimmed = content.trim()
        // 剥离可能的 markdown 代码围栏
        val jsonText = strippedOfCodeFence(trimmed)
        if (jsonText.startsWith("[")) {
            runCatching { JSONArray(jsonText) }.getOrNull()?.let { array ->
                val out = ArrayList<Pair<Int, String>>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                    if (item != null) {
                        val id = item.optInt("id", -1)
                        val text = item.optString("text", "")
                        if (id >= 0) out.add(id to text)
                    } else {
                        val raw = array.optString(i, "")
                        val obj = runCatching { JSONObject(raw) }.getOrNull()
                        if (obj != null) {
                            val id = obj.optInt("id", -1)
                            val text = obj.optString("text", "")
                            if (id >= 0) out.add(id to text)
                        }
                    }
                }
                if (out.isNotEmpty()) return out
            }
        } else if (jsonText.startsWith("{")) {
            runCatching { JSONObject(jsonText) }.getOrNull()?.let { obj ->
                val items = obj.optJSONArray("items")
                if (items != null) {
                    val out = ArrayList<Pair<Int, String>>(items.length())
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val id = item.optInt("id", -1)
                        val text = item.optString("text", "")
                        if (id >= 0) out.add(id to text)
                    }
                    if (out.isNotEmpty()) return out
                }
            }
        }
        // 行格式兜底
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Pair<Int, String>>(lines.size)
        for (line in lines) {
            val sep = line.indexOf('|')
            if (sep > 0) {
                val id = runCatching { line.substring(0, sep).trim().toInt() }.getOrNull()
                if (id != null && id >= 0) {
                    out.add(id to line.substring(sep + 1).trim())
                }
            }
        }
        return if (out.isNotEmpty()) out else null
    }

    private fun strippedOfCodeFence(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON")
                .removePrefix("```").trim()
        }
        if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        return t
    }

    private fun systemPrompt(context: Context): JSONObject {
        val language = targetLanguageName(context)
        return JSONObject()
            .put("role", "system")
            .put(
                "content",
                "You are a screen translator. Translate each numbered text item into " + language +
                    ".\n" +
                    "Rules:\n" +
                    "- Preserve numbers, URLs, code identifiers, and proper nouns as-is.\n" +
                    "- Keep translations concise so they fit near the original text location on screen.\n" +
                    "- Translate all UI text including labels, buttons, and menu items.\n" +
                    "- Do not add explanations or notes.\n" +
                    "- Respond with a single JSON array where each element is " +
                    "{\"id\":<item id>,\"text\":\"<translation>\"}.\n" +
                    "- If a text item contains no translatable content, return it unchanged.",
            )
    }

    private fun targetLanguageName(context: Context): String {
        val locale = runCatching {
            LanguageSettingsRepository(context).selectedLocale()
        }.getOrNull()
        val effective = locale ?: java.util.Locale.getDefault()
        return effective.getDisplayName(java.util.Locale.ENGLISH)
    }

    private fun fingerprint(text: String): String {
        val normalized = text.filter { !it.isWhitespace() }
        return java.util.Objects.hash(normalized).toString(16)
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------


    private fun renderOnMain(blocks: List<ScreenTranslationBlock>) {
        val main = mainHandler ?: return
        val service = overlayService
        if (!renderPending.compareAndSet(false, true)) return
        main.post {
            try {
                renderPending.set(false)
                service?.renderBlocks(blocks)
            } catch (throwable: Throwable) {
                renderPending.set(false)
                AndroidAgentLogger.warnThrottled("screen_translation_render_failed") {
                    "Screen translation render failed: ${throwable.javaClass.simpleName}"
                }
            }
        }
    }

    private fun clearOverlay() {
        val main = mainHandler ?: return
        main.post { overlayService?.renderBlocks(emptyList()) }
    }

    private fun postOverlayError(reason: String) {
        // v1：模型失败时保留上一次成功渲染的译文，只记录日志
        AndroidAgentLogger.warnThrottled("screen_translation_error_$reason") {
            "Screen translation overlay error: $reason"
        }
    }

    // ------------------------------------------------------------------
    // 覆盖层服务引用（由 Service attach/detach 维护）
    // ------------------------------------------------------------------

    @Volatile
    private var overlayService: ScreenTranslationOverlayService? = null

    fun attachOverlay(service: ScreenTranslationOverlayService) {
        overlayService = service
    }

    fun detachOverlay(service: ScreenTranslationOverlayService) {
        if (overlayService === service) overlayService = null
    }

    private const val SELF_PACKAGE = "io.github.mangi.eta"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
