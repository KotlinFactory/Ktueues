package de.leonhardsolbach.ktueues

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.time.Duration

/**
 * Responsible for dispatching tasks to the queue
 */
class TaskDispatcher(val queueAdapter: QueueAdapter, val json: Json = Json) {



    /**
     * Dispatches a task to the queue
     *
     * @param task The task implementation to use
     * @param payload The payload data for the task
     * @param delay Optional delay before the task should be processed
     * @param maxRetries Maximum number of retry attempts for this task
     * @param queueName The name of the queue to enqueue the task into
     */
    suspend inline fun <reified P : Any> dispatch(
        task: Task<P>,
        payload: P,
        delay: Duration? = null,
        maxRetries: Int = 0,
        queueName: String = "default"
    ) {
        val serializedPayload = json.encodeToString(payload)
        queueAdapter.enqueue(
            taskIdentifier = task.taskType,
            serializedPayload = serializedPayload,
            delay = delay,
            maxRetries = maxRetries,
            queueName = queueName
        )
    }


    suspend inline fun registerScheduled(
        task: ScheduledTask,
        chron: String? = null,
        queueName: String = "scheduled"
    ) {
        //
        queueAdapter.enqueueScheduled(
            taskIdentifier = task.taskType,
            chron = chron,
            queueName = queueName
        )
    }


    /**
     * Dispatches a task to the queue using just the task identifier
     *
     * @param taskIdentifier Unique identifier for the task type
     * @param payload The payload data for the task
     * @param payloadType The KType of the payload for serialization
     * @param delay Optional delay before the task should be processed
     * @param maxRetries Maximum number of retry attempts for this task
     * @param queueName The name of the queue to enqueue the task into
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <P : Any> dispatch(
        taskIdentifier: String,
        payload: P,
        payloadType: KType,
        delay: Duration? = null,
        maxRetries: Int = 0,
        queueName: String = "default"
    ) {
        val serializer = json.serializersModule.serializer(payloadType)
        val serializedPayload = json.encodeToString(serializer, payload)
        queueAdapter.enqueue(
            taskIdentifier = taskIdentifier,
            serializedPayload = serializedPayload,
            delay = delay,
            maxRetries = maxRetries,
            queueName = queueName
        )
    }

    /**
     * Dispatches a task to the queue using a reified type parameter
     *
     * @param taskIdentifier Unique identifier for the task type
     * @param payload The payload data for the task
     * @param delay Optional delay before the task should be processed
     * @param maxRetries Maximum number of retry attempts for this task
     * @param queueName The name of the queue to enqueue the task into
     */
    suspend inline fun <reified P : Any> dispatch(
        taskIdentifier: String,
        payload: P,
        delay: Duration? = null,
        maxRetries: Int = 0,
        queueName: String = "default"
    ) {
        val serializedPayload = json.encodeToString(payload)
        queueAdapter.enqueue(
            taskIdentifier = taskIdentifier,
            serializedPayload = serializedPayload,
            delay = delay,
            maxRetries = maxRetries,
            queueName = queueName
        )
    }
}