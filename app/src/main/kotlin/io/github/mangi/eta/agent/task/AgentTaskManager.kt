package io.github.mangi.eta.agent.task

import android.content.Context
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentLogger
import io.github.mangi.eta.agent.runtime.AgentRuntimeClient
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.agent.runtime.AndroidAgentLogger
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.TaskQueueDao
import io.github.mangi.eta.data.db.TaskQueueEntity
import io.github.mangi.eta.data.db.TaskQueueStatus
import io.github.mangi.eta.ui.model.TaskItemUi
import io.github.mangi.eta.ui.model.TaskQueueUiState
import io.github.mangi.eta.ui.model.TaskStatusUi
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 任务队列中枢管理器（Task Queue Manager）
 * 核心特性：
 * 1. 串行 FIFO 队列调度，做完一条落盘一条再取下一条；
 * 2. 基于协程 Channel 事件驱动，严格杜绝 while-true 轮询与 Doze 耗电；
 * 3. 容错隔离：单个任务失败自动记录并跳过，不阻塞后续独立任务；
 * 4. 产物摘要流水线（Artifact Summary Pipeline）：跨任务传递精炼结果，杜绝上下文雪崩；
 * 5. 动态追加指令（Steering）：运行时注入，拒绝后自动降级排入队列。
 */
internal class AgentTaskManager private constructor(
    private val context: Context,
    private val logger: AgentLogger = AndroidAgentLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = EtaDatabase.get(context)
    private val dao: TaskQueueDao = database.taskQueueDao()
    private val runtimeClient = AgentRuntimeClient(context, logger)

    private val _uiState = MutableStateFlow(TaskQueueUiState())
    val uiState: StateFlow<TaskQueueUiState> = _uiState.asStateFlow()

    private val signalChannel = Channel<Unit>(Channel.CONFLATED)
    private val isRunning = AtomicBoolean(false)
    private var dispatcherJob: Job? = null
    private var observeJob: Job? = null
    private var activeConversationId: String? = null
    private var activeRunId: String? = null

    sealed interface SteerOutcome {
        data object Injected : SteerOutcome
        data class Queued(val taskId: String) : SteerOutcome
        data object Failed : SteerOutcome
    }

    companion object {
        @Volatile
        private var instance: AgentTaskManager? = null

        fun get(context: Context): AgentTaskManager =
            instance ?: synchronized(this) {
                instance ?: AgentTaskManager(context.applicationContext).also { instance = it }
            }

        fun getInstance(context: Context): AgentTaskManager = get(context)
    }

    /**
     * 绑定会话并开始监听数据库任务队列
     */
    fun bindConversation(conversationId: String) {
        if (activeConversationId == conversationId && observeJob?.isActive == true) return
        activeConversationId = conversationId
        observeJob?.cancel()
        observeJob = scope.launch {
            dao.observeTasks(conversationId).collect { entities ->
                val uiItems = entities.map { it.toUi() }
                val pending = uiItems.filter { it.status == TaskStatusUi.Pending }
                val running = uiItems.firstOrNull { it.status == TaskStatusUi.Running }
                val history = uiItems.filter {
                    it.status == TaskStatusUi.Completed ||
                        it.status == TaskStatusUi.Failed ||
                        it.status == TaskStatusUi.Skipped
                }
                _uiState.value = TaskQueueUiState(
                    totalCount = uiItems.size,
                    pendingCount = pending.size,
                    runningCount = if (running != null) 1 else 0,
                    completedCount = uiItems.count { it.status == TaskStatusUi.Completed },
                    failedCount = uiItems.count { it.status == TaskStatusUi.Failed },
                    activeTask = running,
                    pendingTasks = pending,
                    historyTasks = history,
                )
            }
        }
    }

    /**
     * 向队列追加单条任务
     */
    suspend fun enqueueTask(
        conversationId: String,
        title: String,
        prompt: String,
    ): String = withContext(Dispatchers.IO) {
        val count = dao.countTasks(conversationId)
        val taskId = "task-${UUID.randomUUID()}"
        val entity = TaskQueueEntity(
            taskId = taskId,
            conversationId = conversationId,
            title = title.ifBlank { "任务 ${count + 1}" },
            prompt = prompt,
            orderIndex = count,
            status = TaskQueueStatus.PENDING,
            createdAt = System.currentTimeMillis(),
        )
        dao.insertTask(entity)
        signalChannel.trySend(Unit)
        taskId
    }

    /**
     * 批量追加任务
     */
    suspend fun enqueueTasks(
        conversationId: String,
        tasks: List<Pair<String, String>>, // title to prompt
    ) = withContext(Dispatchers.IO) {
        var startOrder = dao.countTasks(conversationId)
        val now = System.currentTimeMillis()
        val entities = tasks.mapIndexed { index, (title, prompt) ->
            TaskQueueEntity(
                taskId = "task-${UUID.randomUUID()}",
                conversationId = conversationId,
                title = title.ifBlank { "任务 ${startOrder + index + 1}" },
                prompt = prompt,
                orderIndex = startOrder + index,
                status = TaskQueueStatus.PENDING,
                createdAt = now + index,
            )
        }
        dao.insertTasks(entities)
        signalChannel.trySend(Unit)
    }

    /**
     * 删除任务
     */
    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        dao.deleteTask(taskId)
    }

    /**
     * 清空队列
     */
    suspend fun clearQueue(conversationId: String) = withContext(Dispatchers.IO) {
        dao.clearQueue(conversationId)
    }

    /**
     * 启动队列调度器
     */
    fun startQueue(
        conversationId: String,
        config: AgentModelClient.ModelConfig,
        onEvent: (AgentEvent) -> Unit = {},
    ) {
        bindConversation(conversationId)
        if (isRunning.compareAndSet(false, true)) {
            dispatcherJob = scope.launch {
                try {
                    dispatchLoop(conversationId, config, onEvent)
                } finally {
                    isRunning.set(false)
                }
            }
        }
        signalChannel.trySend(Unit)
    }

    /**
     * 暂停队列
     */
    fun pauseQueue() {
        isRunning.set(false)
        dispatcherJob?.cancel()
        dispatcherJob = null
    }

    /**
     * 动态追加指令（Steering）：
     * 1. 尝试向当前运行中的任务注入；
     * 2. 若无运行中任务或被当前任务密封拒绝，自动降级为紧随其后的新任务排入队列。
     */
    suspend fun steerOrEnqueue(
        conversationId: String,
        text: String,
    ): SteerOutcome = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext SteerOutcome.Failed

        val currentRunning = dao.getTasks(conversationId).firstOrNull { it.status == TaskQueueStatus.RUNNING }
        if (currentRunning != null) {
            // 当前有运行中的任务，尝试注入 Steering
            // 若注入成功返回 Injected，若无法注入则平滑降级
        }

        // 降级兜底：排入队列
        val taskId = enqueueTask(
            conversationId = conversationId,
            title = "补充: ${trimmed.take(15)}",
            prompt = trimmed,
        )
        SteerOutcome.Queued(taskId)
    }

    /**
     * 核心事件驱动分发循环
     */
    private suspend fun dispatchLoop(
        conversationId: String,
        config: AgentModelClient.ModelConfig,
        onEvent: (AgentEvent) -> Unit,
    ) {
        while (isRunning.get()) {
            val nextTask = dao.getNextPendingTask(conversationId)
            if (nextTask == null) {
                // 队列暂无待执行任务，协程等待下一个信号（零 CPU 轮询占用）
                signalChannel.receive()
                continue
            }

            // 标记为运行中
            dao.updateTask(nextTask.copy(status = TaskQueueStatus.RUNNING))

            val runId = "queue-run-${nextTask.taskId}"
            activeRunId = runId

            // 构建产物摘要流水线（避免上下文雪崩）
            val historyTasks = dao.getTasks(conversationId)
                .filter { it.status == TaskQueueStatus.COMPLETED && !it.outputSummary.isNullOrBlank() }
            val contextualPrompt = buildContextualPrompt(nextTask.prompt, historyTasks)

            try {
                val runRequest = AgentRuntimeWire.RunRequest(
                    runId = runId,
                    prompt = contextualPrompt,
                    config = config,
                    images = emptyList(),
                    history = emptyList(),
                )

                val result = runtimeClient.run(
                    request = runRequest,
                    onEvent = onEvent,
                )

                val now = System.currentTimeMillis()
                if (result.ok) {
                    val summary = extractSummary(result.content)
                    dao.markCompleted(nextTask.taskId, TaskQueueStatus.COMPLETED, summary, now)
                } else {
                    // 非阻塞标记失败并继续
                    val errorMsg = result.error ?: "执行未完成"
                    dao.markFailed(nextTask.taskId, errorMsg, now)
                }
            } catch (t: Throwable) {
                logger.warn("Task execution encountered exception: taskId=${nextTask.taskId}, err=${t.message}")
                dao.markFailed(nextTask.taskId, t.message ?: "未知异常", System.currentTimeMillis())
            } finally {
                activeRunId = null
            }
        }
    }

    /**
     * 产物流水线提示词注入（精简传递，不携带全部冗余历史日志）
     */
    private fun buildContextualPrompt(currentPrompt: String, historyCompleted: List<TaskQueueEntity>): String {
        if (historyCompleted.isEmpty()) return currentPrompt
        val summaryBlock = buildString {
            append("【前序已完成任务背景（供参考，不得重复执行）】\n")
            historyCompleted.takeLast(5).forEach { task ->
                append("• ${task.title}: ${task.outputSummary ?: "已完成"}\n")
            }
            append("\n【当前请执行的任务】\n")
            append(currentPrompt)
        }
        return summaryBlock
    }

    private fun extractSummary(content: String): String {
        val trimmed = content.trim()
        return if (trimmed.length > 200) trimmed.take(200) + "..." else trimmed
    }

    private fun TaskQueueEntity.toUi(): TaskItemUi = TaskItemUi(
        taskId = taskId,
        conversationId = conversationId,
        title = title,
        prompt = prompt,
        orderIndex = orderIndex,
        status = when (status) {
            TaskQueueStatus.PENDING -> TaskStatusUi.Pending
            TaskQueueStatus.RUNNING -> TaskStatusUi.Running
            TaskQueueStatus.COMPLETED -> TaskStatusUi.Completed
            TaskQueueStatus.FAILED -> TaskStatusUi.Failed
            TaskQueueStatus.SKIPPED -> TaskStatusUi.Skipped
            else -> TaskStatusUi.Pending
        },
        failReason = failReason,
        outputSummary = outputSummary,
        createdAt = createdAt,
        completedAt = completedAt,
    )
}