package de.leonhardsolbach.ktueues

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default implementation of a Worker that processes tasks from a queue
 */
class Worker(
    private val queueAdapter: QueueAdapter,
    private val taskRegistry: TaskRegistry,
    private val workerId: String = UUID.randomUUID().toString(),
    private val queueName: String = "default",
    private val pollTimeout: Duration = 5000.milliseconds,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val taskDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json
) {
    private val logger = LoggerFactory.getLogger(Worker::class.java)
    private val workerScope = CoroutineScope(workerDispatcher + SupervisorJob())
    private var running = false

    /**
     * Starts the worker
     */
    fun start() {
        if (running) return
        running = true
        workerScope.launch {
            while (running && isActive) {
                try {
                    processNextTask()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Error processing task", e)
                    delay(1000) // Brief delay before retrying to prevent CPU spinning
                }
            }
        }
    }

    /**
     * Stops the worker
     * 
     * @param timeout How long to wait for graceful shutdown
     */
    suspend fun stop(timeout: Duration) {
        if (!running) return
        running = false
        try {
            withTimeout(timeout) {
                workerScope.coroutineContext.job.children.forEach { it.join() }
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn("Worker shutdown timed out after ${timeout}ms, cancelling remaining tasks")
        } finally {
            workerScope.cancel()
        }
    }

    private suspend fun processNextTask() {
        val dequeuedTask = queueAdapter.dequeue(workerId, queueName, pollTimeout) ?: return

        val taskIdentifier = dequeuedTask.taskIdentifier

        // Check for regular task first
        val task = taskRegistry.getTask<Any>(taskIdentifier)
        val scheduledTask = if (task == null) taskRegistry.getScheduledTask(taskIdentifier) else null

        if (task == null && scheduledTask == null) {
            logger.error("Unknown task type: $taskIdentifier")
            dequeuedTask.negativeAcknowledge(false, null)
            return
        }

        if (task != null) {
            val serializer = taskRegistry.getSerializer<Any>(taskIdentifier)

            if (serializer == null) {
                logger.error("Serializer not found for task type: $taskIdentifier")
                dequeuedTask.negativeAcknowledge(false, null)
                return
            }

            val payload = try {
                json.decodeFromString(serializer, dequeuedTask.serializedPayload)
            } catch (e: Exception) {
                logger.error("Failed to deserialize payload for task: $taskIdentifier", e)
                dequeuedTask.negativeAcknowledge(false, null)
                return
            }

            executeRegularTask(task, payload, dequeuedTask)
        } else if (scheduledTask != null) {
            executeScheduledTask(scheduledTask, dequeuedTask)
        }

        // Further handling is executed within helper functions above.
    }

    /**
     * Default implementation of TaskContext
     */
    private class DefaultTaskContext(override val coroutineScope: CoroutineScope, override val logger: org.slf4j.Logger) : TaskContext


    private fun CoroutineScope.launchTask(block: suspend () -> Unit) = launch {
        try {
            block()
        } catch (e: CancellationException) {
            logger.info("Task cancelled")
            throw e
        } catch (e: Exception) {
            logger.error("Task execution failed", e)
        }
    }

    private suspend fun <P : Any> executeRegularTask(
        task: Task<P>,
        payload: P,
        dequeuedTask: DequeuedTask
    ) {
        val taskIdentifier = task.taskType
        val taskScope = CoroutineScope(taskDispatcher + Job(workerScope.coroutineContext.job))
        val taskContext = DefaultTaskContext(taskScope, LoggerFactory.getLogger("Task-$taskIdentifier"))

        taskScope.launchTask {
            try {
                task.execute(payload, taskContext)
                dequeuedTask.acknowledge()
            } catch (e: CancellationException) {
                dequeuedTask.negativeAcknowledge(true, null)
                throw e
            } catch (e: Exception) {
                try {
                    task.onError(e, payload, dequeuedTask.currentAttempt, taskContext)
                } catch (handlerEx: Exception) {
                    logger.error("Error in onError handler", handlerEx)
                }

                val shouldRetry = dequeuedTask.currentAttempt < dequeuedTask.maxRetries
                val retryDelay = if (shouldRetry) task.nextRetryDelay(dequeuedTask.currentAttempt, e) else null

                if (shouldRetry && retryDelay != null && !retryDelay.isNegative()) {
                    dequeuedTask.negativeAcknowledge(true, retryDelay)
                } else {
                    dequeuedTask.negativeAcknowledge(false, null)
                }
            }
        }
    }

    private suspend fun executeScheduledTask(
        scheduledTask: ScheduledTask,
        dequeuedTask: DequeuedTask
    ) {
        val taskIdentifier = scheduledTask.taskType
        val taskScope = CoroutineScope(taskDispatcher + Job(workerScope.coroutineContext.job))
        val taskContext = DefaultTaskContext(taskScope, LoggerFactory.getLogger("ScheduledTask-$taskIdentifier"))

        taskScope.launchTask {
            try {
                scheduledTask.execute(taskContext)
            } catch (e: CancellationException) {
                dequeuedTask.acknowledge() // simply ack on cancellation
                throw e
            } catch (e: Exception) {
                try {
                    scheduledTask.onError(e, taskContext)
                } catch (handlerErr: Exception) {
                    logger.error("ScheduledTask onError failed", handlerErr)
                }
            } finally {
                // Never requeue cron instance – cron schedule will enqueue the next one.
                dequeuedTask.acknowledge()
            }
        }
    }
}