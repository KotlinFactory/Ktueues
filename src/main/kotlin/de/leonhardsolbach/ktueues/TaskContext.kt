package de.leonhardsolbach.ktueues

import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger

/**
 * Context object passed to tasks during execution, providing access to services,
 * logging, coroutine scope, and other runtime resources.
 */
interface TaskContext {
    /**
     * The coroutine scope associated with this task execution.
     * Used for launching child coroutines that will be properly managed.
     */
    val coroutineScope: CoroutineScope
    
    /**
     * Logger for the task to use during execution.
     */
    val logger: Logger
}