package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import org.json.JSONArray

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
    /**
     * 任务指令随附的图片/文件附件，JSON 数组；空表示纯文本任务。
     *
     * `defaultValue` 必须与 [EtaDatabase.MIGRATION_22_23] 的 `DEFAULT '[]'` 完全一致：
     * Room 在打开库时会比对"实体期望的建表 SQL"与"迁移后实际表结构"，两者默认值
     * 不一致会直接抛 `Migration didn't properly handle`，表现为升级即崩溃。
     */
    @ColumnInfo(name = "attachments_json", defaultValue = "'[]'")
    val attachmentsJson: String = "[]",
)

/**
 * 任务附件模型：图片以 dataUrl 内联，文件以工作区路径引用。
 * 单独抽成可序列化的小结构，便于 DAO 存储与单元测试。
 */
@Serializable
internal data class TaskAttachment(
    /** "image" 或 "file"。 */
    val kind: String,
    /** 图片为 dataUrl；文件为绝对路径或工作区引用。 */
    val value: String,
    val mime: String = "",
) {
    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_FILE = "file"

        fun image(dataUrl: String, mime: String = ""): TaskAttachment =
            TaskAttachment(KIND_IMAGE, dataUrl, mime)

        fun file(path: String): TaskAttachment = TaskAttachment(KIND_FILE, path)
    }
}

/** 任务附件与 JSON 的互转。解析失败时退化为空列表，绝不让脏数据拖垮队列。 */
internal object TaskAttachmentCodec {
    fun encode(attachments: List<TaskAttachment>): String {
        if (attachments.isEmpty()) return "[]"
        val array = JSONArray()
        attachments.forEach { attachment ->
            array.put(
                JSONArray()
                    .put(attachment.kind)
                    .put(attachment.value)
                    .put(attachment.mime),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<TaskAttachment> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "[]") return emptyList()
        return runCatching {
            val array = JSONArray(text)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONArray(index) ?: continue
                    val kind = item.optString(0).trim()
                    val value = item.optString(1)
                    if (kind.isEmpty() || value.isEmpty()) continue
                    add(TaskAttachment(kind, value, item.optString(2)))
                }
            }
        }.getOrElse { emptyList() }
    }
}

internal object TaskQueueStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val SKIPPED = "SKIPPED"
}
