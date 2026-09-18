package io.github.mangi.eta.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TaskQueueDao {
    @Query("SELECT * FROM agent_task_queue WHERE conversation_id = :conversationId ORDER BY order_index ASC")
    fun observeTasks(conversationId: String): Flow<List<TaskQueueEntity>>

    @Query("SELECT * FROM agent_task_queue WHERE conversation_id = :conversationId ORDER BY order_index ASC")
    suspend fun getTasks(conversationId: String): List<TaskQueueEntity>

    @Query("SELECT * FROM agent_task_queue WHERE conversation_id = :conversationId AND status = 'PENDING' ORDER BY order_index ASC LIMIT 1")
    suspend fun getNextPendingTask(conversationId: String): TaskQueueEntity?

    @Query("SELECT * FROM agent_task_queue WHERE task_id = :taskId LIMIT 1")
    suspend fun getTask(taskId: String): TaskQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskQueueEntity>)
    @Update
    suspend fun updateTask(task: TaskQueueEntity)

    /** 追加指令就地改写该任务的指令与附件，不影响其排队次序与状态。 */
    @Query("UPDATE agent_task_queue SET prompt = :prompt, attachments_json = :attachmentsJson WHERE task_id = :taskId")
    suspend fun updateTaskInstruction(taskId: String, prompt: String, attachmentsJson: String)


    @Query("UPDATE agent_task_queue SET status = :status, output_summary = :summary, completed_at = :completedAt WHERE task_id = :taskId")
    suspend fun markCompleted(taskId: String, status: String, summary: String?, completedAt: Long)

    @Query("UPDATE agent_task_queue SET status = 'FAILED', fail_reason = :failReason, completed_at = :completedAt WHERE task_id = :taskId")
    suspend fun markFailed(taskId: String, failReason: String?, completedAt: Long)
    @Query("DELETE FROM agent_task_queue WHERE task_id = :taskId")
    suspend fun deleteTask(taskId: String)

    /**
     * 失败任务重试：把原任务重置为待执行并清空失败痕迹，保留同一 task_id 与 order_index。
     * 只有仍处于 FAILED 的记录会被改动，避免误伤已被重新调度的任务。
     */
    @Query(
        "UPDATE agent_task_queue SET status = 'PENDING', fail_reason = NULL, " +
            "output_summary = NULL, completed_at = NULL WHERE task_id = :taskId AND status = 'FAILED'"
    )
    suspend fun resetFailedTask(taskId: String): Int

    /**
     * 带指令与附件的失败任务重编辑：乐观锁仍限定 status = 'FAILED'，
     * 执行失败（任务已被并发改动）时返回 0。
     */
    @Query(
        "UPDATE agent_task_queue SET title = :title, prompt = :prompt, attachments_json = :attachmentsJson, " +
            "status = 'PENDING', fail_reason = NULL, output_summary = NULL, completed_at = NULL " +
            "WHERE task_id = :taskId AND status = 'FAILED'"
    )
    suspend fun retryFailedTask(
        taskId: String,
        title: String,
        prompt: String,
        attachmentsJson: String,
    ): Int


    @Query("DELETE FROM agent_task_queue WHERE conversation_id = :conversationId")
    suspend fun clearQueue(conversationId: String)

    @Query("SELECT COUNT(*) FROM agent_task_queue WHERE conversation_id = :conversationId")
    suspend fun countTasks(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM agent_task_queue WHERE conversation_id = :conversationId AND status = :status")
    suspend fun countTasksByStatus(conversationId: String, status: String): Int
}
