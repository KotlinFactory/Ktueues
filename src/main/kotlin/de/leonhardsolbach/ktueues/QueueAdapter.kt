package de.leonhardsolbach.ktueues

import kotlin.time.Duration

/**
 * Interface for queue backend adapters. This provides an abstraction over different
 * message brokers like Redis or RabbitMQ.
 */
interface QueueAdapter {
    /**
     * Enqueues a task for processing
     * 
     * @param taskIdentifier Unique identifier for the task type
     * @param serializedPayload String representation of the task payload
     * @param delay Optional delay before the task should be processed
     * @param maxRetries Maximum number of retry attempts for this task
     * @param currentAttempt The current attempt number (starts at 1)
     * @param queueName The name of the queue to enqueue the task into
     */
    suspend fun enqueue(
        taskIdentifier: String,
        serializedPayload: String,
        delay: Duration? = null,
        maxRetries: Int = 0,
        currentAttempt: Int = 1,
        queueName: String = "default"
    )

    /**
     * Enqueues a task for processing using a chron expression
     *
     * @param taskIdentifier Unique identifier for the task type
     * @param chron Chron expression for scheduling the task
     * @param queueName The name of the queue to enqueue the task into
     */
    suspend fun enqueueScheduled(
        taskIdentifier: String,
        chron: String? = null,
        queueName: String = "scheduled"
    )
    
    /**
     * Dequeues a task for processing
     * 
     * @param workerId Unique identifier for the worker instance
     * @param queueName The name of the queue to dequeue from
     * @param pollTimeout How long to wait for a task before returning null
     * @return A DequeuedTask if one is available, null otherwise
     */
    suspend fun dequeue(workerId: String, queueName: String, pollTimeout: Duration): DequeuedTask?


}