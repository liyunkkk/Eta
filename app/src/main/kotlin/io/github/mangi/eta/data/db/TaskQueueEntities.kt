package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "agent_task_queue",
    indices = [
        Index(value = ["conversation_id", "order_index"]),
        Index(value = ["status"]),
    ],
)
internal data class TaskQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "prompt")
    val prompt: String,
    @ColumnInfo(name = "attachments_json")
    val attachmentsJson: String? = null,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "fail_reason")
    val failReason: String? = null,
    @ColumnInfo(name = "output_summary")
    val outputSummary: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
)

internal object TaskQueueStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val SKIPPED = "SKIPPED"
}
