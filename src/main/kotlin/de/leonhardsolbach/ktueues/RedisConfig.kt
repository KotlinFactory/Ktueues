package de.leonhardsolbach.ktueues

import kotlinx.serialization.Serializable

/**
 * Redis specific configuration for Ktueues.  This class is deliberately small and
 * contains only the properties that are necessary for establishing a connection
 * using the Jedis client which is used in the [de.leonhardsolbach.ktueues.redis]
 * package.
 *
 * If a `connectionString` is provided it has precedence over the individual
 * host/port/password/database fields because the underlying Redis client will
 * parse the URI on its own.
 */
@Serializable
data class RedisConfig(
    val host: String = "localhost",
    val port: Int = 6379,
    val password: String? = null,
    val database: Int = 0,
    val connectionString: String? = null
) : ConnectionConfig
