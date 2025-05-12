package de.leonhardsolbach.ktueues

import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * Builder for creating a configured Kueues instance
 */
class KtueuesBuilder {
    private var config = KtueuesConfig()
    
    /**
     * Sets the configuration for the Kueues system
     */
    fun withConfig(config: KtueuesConfig): KtueuesBuilder {
        this.config = config
        return this
    }
    
    /**
     * Sets the number of worker instances to run
     */
    fun withWorkers(count: Int): KtueuesBuilder {
        config = config.copy(numberOfWorkers = count)
        return this
    }
    
    /**
     * Sets the polling timeout
     */
    fun withPollTimeout(timeout: Duration): KtueuesBuilder {
        config = config.copy(pollTimeout = timeout)
        return this
    }
    
    /**
     * Sets the default queue name
     */
    fun withDefaultQueue(name: String): KtueuesBuilder {
        config = config.copy(defaultQueueName = name)
        return this
    }
    
    /**
     * Sets the default maximum retry attempts
     */
    fun withDefaultMaxRetries(maxRetries: Int): KtueuesBuilder {
        config = config.copy(defaultMaxRetries = maxRetries)
        return this
    }
    
    /**
     * Sets the JSON serializer
     */
    fun withJson(json: Json): KtueuesBuilder {
        config = config.copy(json = json)
        return this
    }
    
    /* --------------------------------------------------------------------- */
    /* Redis specific helpers (syntactic sugar)                               */
    /* --------------------------------------------------------------------- */

    /**
     * Convenience method for directly supplying a fully configured
     * [RedisConfig] instance.  Under the hood this populates
     * [KtueuesConfig.connectionConfig].
     */
    fun withRedisConfig(redisConfig: RedisConfig): KtueuesBuilder {
        config = config.copy(connectionConfig = redisConfig)
        return this
    }

    /**
     * Convenience methods that mutate the currently configured [RedisConfig].
     * If the connection config is not a [RedisConfig] yet it will be replaced
     * by a new one with default values (except for the property that is being
     * changed here).
     */
    fun withRedisHost(host: String): KtueuesBuilder {
        val current = (config.connectionConfig as? RedisConfig) ?: RedisConfig()
        config = config.copy(connectionConfig = current.copy(host = host))
        return this
    }

    fun withRedisPort(port: Int): KtueuesBuilder {
        val current = (config.connectionConfig as? RedisConfig) ?: RedisConfig()
        config = config.copy(connectionConfig = current.copy(port = port))
        return this
    }

    fun withRedisPassword(password: String?): KtueuesBuilder {
        val current = (config.connectionConfig as? RedisConfig) ?: RedisConfig()
        config = config.copy(connectionConfig = current.copy(password = password))
        return this
    }

    fun withRedisDatabase(database: Int): KtueuesBuilder {
        val current = (config.connectionConfig as? RedisConfig) ?: RedisConfig()
        config = config.copy(connectionConfig = current.copy(database = database))
        return this
    }

    fun withRedisConnectionString(connectionString: String): KtueuesBuilder {
        val current = (config.connectionConfig as? RedisConfig) ?: RedisConfig()
        config = config.copy(connectionConfig = current.copy(connectionString = connectionString))
        return this
    }
    
    /**
     * Creates the Kueues instance with the configured settings
     */
    fun build(): Ktueues {
        // Select the appropriate QueueAdapter based on the configured backend.
        val adapter: QueueAdapter = when (val backend = config.connectionConfig) {
            is RedisConfig -> de.leonhardsolbach.ktueues.redis.RedisQueueAdapter(
                redisConfig = backend,
                json = config.json
            )
            else -> throw IllegalArgumentException(
                "The provided connection configuration $backend is not supported yet."
            )
        }

        // Create the Ktueues instance based on the collected configuration.
        return Ktueues(
            queueAdapter = adapter,
            json = config.json,
            numberOfWorkers = config.numberOfWorkers,
            defaultQueueName = config.defaultQueueName,
            defaultMaxRetries = config.defaultMaxRetries
        )
    }
    
    companion object {
        /**
         * Creates a new builder with default settings
         */
        fun create(): KtueuesBuilder = KtueuesBuilder()
    }
}