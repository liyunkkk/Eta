package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable

import io.github.mangi.eta.data.db.TaskAttachment

@Immutable
enum class TaskStatusUi {
    Pending,
    Running,
    Completed,
    Failed,
    Skipped,
}

@Immutable
data class TaskItemUi(
    val taskId: String,
    val conversationId: String,
    val title: String,
    val prompt: String,
    val orderIndex: Int,
    val status: TaskStatusUi,
    val failReason: String? = null,
    val outputSummary: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
    /** 随任务指令携带的图片与文件附件。 */
    val attachments: List<TaskAttachment> = emptyList(),
) {
    val imageAttachments: List<TaskAttachment> get() = attachments.filter { it.kind == TaskAttachment.KIND_IMAGE }
    val fileAttachments: List<TaskAttachment> get() = attachments.filter { it.kind == TaskAttachment.KIND_FILE }
    val hasAttachments: Boolean get() = attachments.isNotEmpty()
}

@Immutable
data class TaskQueueUiState(
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val runningCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val activeTask: TaskItemUi? = null,
    val pendingTasks: List<TaskItemUi> = emptyList(),
    val historyTasks: List<TaskItemUi> = emptyList(),
) {
    val isIdle: Boolean get() = runningCount == 0 && pendingCount == 0
    val hasTasks: Boolean
        get() = totalCount > 0 || activeTask != null || pendingTasks.isNotEmpty() || historyTasks.isNotEmpty()
    val summaryBadge: String
        get() = "$totalCount | ✓$completedCount ⟳$runningCount" + if (failedCount > 0) " ⚠$failedCount" else ""
}