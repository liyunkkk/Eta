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

    @Query("UPDATE agent_task_queue SET status = :status, output_summary = :summary, completed_at = :completedAt WHERE task_id = :taskId")
    suspend fun markCompleted(taskId: String, status: String, summary: String?, completedAt: Long)

    @Query("UPDATE agent_task_queue SET status = 'FAILED', fail_reason = :failReason, completed_at = :completedAt WHERE task_id = :taskId")
    suspend fun markFailed(taskId: String, failReason: String?, completedAt: Long)

    @Query("DELETE FROM agent_task_queue WHERE task_id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM agent_task_queue WHERE conversation_id = :conversationId")
    suspend fun clearQueue(conversationId: String)

    @Query("SELECT COUNT(*) FROM agent_task_queue WHERE conversation_id = :conversationId")
    suspend fun countTasks(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM agent_task_queue WHERE conversation_id = :conversationId AND status = :status")
    suspend fun countTasksByStatus(conversationId: String, status: String): Int
}
