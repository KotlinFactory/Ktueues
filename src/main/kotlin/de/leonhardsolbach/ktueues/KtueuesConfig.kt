package de.leonhardsolbach.ktueues

import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Central configuration object for Ktueues.  All properties have sane defaults
 * so that a minimal, fully-working configuration can be obtained simply by
 * creating the class without arguments:
 *
 *     val cfg = KtueuesConfig()
 *
 * The intent is that consumers override only the parameters they care about –
 * either directly via `copy( … )` or indirectly through the [KtueuesBuilder]
 * DSL.
 */
data class KtueuesConfig(
    // General runtime behaviour ------------------------------------------------
    val numberOfWorkers: Int = 1,
    val pollTimeout: Duration = 5.seconds,

    // Task defaults ------------------------------------------------------------
    val defaultQueueName: String = "default",
    val defaultMaxRetries: Int = 3,

    // Serialization ------------------------------------------------------------
    val json: Json = Json {
        ignoreUnknownKeys = true
    },

    // Backend connection -------------------------------------------------------
    val connectionConfig: ConnectionConfig = RedisConfig()
)
