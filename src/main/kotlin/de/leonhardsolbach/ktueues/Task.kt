package de.leonhardsolbach.ktueues

import kotlin.time.Duration

/**
 * Core interface for all dispatchable tasks in the queue.
 * @param P The payload type for this task, which must be serializable.
 */
interface Task<P : Any> {
    /**
     * A unique string identifier for this task type.
     * Used by workers to look up the correct Task implementation and serializer for the payload.
     */
    val taskType: String
    
    /**
     * The main execution logic for the task.
     * This function will be called by a worker when a task of this type is dequeued.
     * 
     * @param payload The deserialized payload for this task instance.
     * @param context Provides access to application services, logging, and the task's coroutine scope.
     */
    suspend fun execute(payload: P, context: TaskContext)
    
    /**
     * Called when an unhandled error occurs during execute() and before a retry attempt.
     * 
     * @param error The error that occurred.
     * @param payload The payload of the failed task.
     * @param attempt The current attempt number (1 for the first failure, 2 for the second, etc.).
     * @param context The task context.
     */
    suspend fun onError(error: Throwable, payload: P, attempt: Int, context: TaskContext) {
        context.logger.error("Task $taskType failed on attempt $attempt", error)
    }
    
    /**
     * Determines the delay before the next retry attempt.
     * Return null or a non-positive duration to not retry further based on this logic.
     * 
     * @param attempt The current attempt number that just failed.
     * @param lastError The error that caused the current attempt to fail.
     * @return The Duration to wait before the next attempt, or null to not retry.
     */
    suspend fun nextRetryDelay(attempt: Int, lastError: Throwable): Duration? = null
}