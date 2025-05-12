package de.leonhardsolbach.ktueues

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * Registry for tasks and their serializers
 */
class TaskRegistry(val json: Json = Json) {
    val tasks = mutableMapOf<String, Task<*>>()
    val serializers = mutableMapOf<String, KSerializer<*>>()
    val payloadTypes = mutableMapOf<String, KType>()
    private val scheduledTasks = mutableMapOf<String, ScheduledTask>()

    /**
     * Registers a task with the registry
     *
     * @param task The task to register
     * @param payloadClass The KClass of the payload type
     * @param serializer The serializer for the payload type
     */
    fun <P : Any> registerTask(
        task: Task<P>,
        payloadClass: KClass<P>,
        serializer: KSerializer<P>
    ) {
        tasks[task.taskType] = task
        serializers[task.taskType] = serializer
        payloadTypes[task.taskType] = payloadClass.createType(nullable = false)
    }


    /**
     * Registers a ScheduledTask with the registry.
     * The task type identifier **must** be unique across normal and scheduled tasks.
     */
    fun registerScheduledTask(task: ScheduledTask) {
        if (tasks.containsKey(task.taskType) || scheduledTasks.containsKey(task.taskType)) {
            throw IllegalArgumentException("Task type '${task.taskType}' is already registered")
        }
        scheduledTasks[task.taskType] = task
    }

    /**
     * Obtains a scheduled task by identifier.
     */
    fun getScheduledTask(taskIdentifier: String): ScheduledTask? = scheduledTasks[taskIdentifier]

    /**
     * Registers a task with the registry using a reified type parameter for the payload
     */
    inline fun <reified P : Any> registerTask(task: Task<P>) {
        tasks[task.taskType] = task
        serializers[task.taskType] = json.serializersModule.serializer<P>()
        payloadTypes[task.taskType] = typeOf<P>()
    }

    /**
     * Gets a task by its type identifier
     *
     * @param taskIdentifier The unique identifier for the task type
     * @return The task instance if registered, null otherwise
     */
    @Suppress("UNCHECKED_CAST")
    fun <P : Any> getTask(taskIdentifier: String): Task<P>? = tasks[taskIdentifier] as? Task<P>

    /**
     * Gets a serializer by task type identifier
     *
     * @param taskIdentifier The unique identifier for the task type
     * @return The serializer if registered, null otherwise
     */
    @Suppress("UNCHECKED_CAST")
    fun <P : Any> getSerializer(taskIdentifier: String): KSerializer<P>? =
        serializers[taskIdentifier] as? KSerializer<P>

    /**
     * Gets the payload type by task type identifier
     *
     * @param taskIdentifier The unique identifier for the task type
     * @return The KType if registered, null otherwise
     */
    fun getPayloadType(taskIdentifier: String): KType? = payloadTypes[taskIdentifier]
}