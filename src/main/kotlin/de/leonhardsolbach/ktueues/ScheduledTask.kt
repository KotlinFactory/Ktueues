package de.leonhardsolbach.ktueues

interface ScheduledTask {
    /**
     * A unique string identifier for this task type.
     * Used by workers to look up the correct Task implementation and serializer for the payload.
     */
    val taskType: String

    /**
     * The main execution logic for the task.
     * This function will be called automatically by a worker when the chron expression is met.
     *
     * @param context Provides access to application services, logging, and the task's coroutine scope.
     */
    suspend fun execute(context: TaskContext)

    /**
     * Called when an unhandled error occurs during execute() and before a retry attempt.
     *
     * @param error The error that occurred.
     * @param context The task context.
     */
    suspend fun onError(error: Throwable, context: TaskContext) {
        context.logger.error("Scheduled Task $taskType failed on attempt", error)
    }


}