package de.leonhardsolbach.ktueues

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Main facade for the Ktueues task queue system
 */
class Ktueues(
    val queueAdapter: QueueAdapter,
    private val json: Json = Json,
    val numberOfWorkers: Int = 1,
    val defaultQueueName: String = "default",
    val defaultMaxRetries: Int = 3
) {
    val logger: Logger = LoggerFactory.getLogger(Ktueues::class.java)
    val taskRegistry = TaskRegistry(json)
    val taskDispatcher = TaskDispatcher(queueAdapter, json)
    private val workers = mutableListOf<Worker>()

    /**
     * Registers a task with the system
     */
    inline fun <reified P : Any> registerTask(task: Task<P>) {
        taskRegistry.registerTask(task)
        logger.info("Registered task '${task.taskType}'")
    }

    /**
     * Registers a ScheduledTask (cron / periodic) with this instance
     */
    fun registerScheduledTask(task: ScheduledTask) {
        taskRegistry.registerScheduledTask(task)
        logger.info("Registered scheduled task '${task.taskType}'")
    }

    /**
     * Stops the Ktueues system
     */
    suspend fun stop(timeout: Duration = 30.seconds) = coroutineScope {
        logger.info("Stopping Ktueues")

        // Stop the scheduler
        val schedulerJob = launch {
//            taskScheduler.stop(timeout)
            logger.info("Stopped task scheduler")
        }

        // Stop all workers
        val workerJobs = workers.map { worker ->
            launch {
                worker.stop(timeout)
            }
        }

        // Wait for all to complete
        (workerJobs + schedulerJob).joinAll()

        logger.info("Kueues stopped")
    }
}