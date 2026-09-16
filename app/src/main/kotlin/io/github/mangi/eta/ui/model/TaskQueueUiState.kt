package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable

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
)

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
    val summaryBadge: String
        get() = "$totalCount | ✓$completedCount ⟳$runningCount" + if (failedCount > 0) " ⚠$failedCount" else ""
}