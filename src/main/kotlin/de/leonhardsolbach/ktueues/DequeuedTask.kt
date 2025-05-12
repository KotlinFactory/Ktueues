package de.leonhardsolbach.ktueues

import kotlin.time.Duration

/**
 * Represents a task that has been dequeued from a queue but not yet completed.
 * Contains the task data and methods to acknowledge or reject the task.
 */
data class DequeuedTask(
    /**
     * Unique ID for this delivery attempt
     */
    val id: String,
    
    /**
     * Type identifier used to determine which Task implementation to use
     */
    val taskIdentifier: String,
    
    /**
     * The serialized payload data
     */
    val serializedPayload: String,
    
    /**
     * Current attempt number for this task (starts at 1)
     */
    val currentAttempt: Int,
    
    /**
     * Maximum number of retry attempts allowed
     */
    val maxRetries: Int,
    
    /**
     * Function to acknowledge successful processing of the task
     */
    val acknowledge: suspend () -> Unit,
    
    /**
     * Function to signal failed processing of the task
     * @param requeue Whether to requeue the task for another attempt
     * @param delay Optional delay before the task is available for processing again
     */
    val negativeAcknowledge: suspend (requeue: Boolean, delay: Duration?) -> Unit
)