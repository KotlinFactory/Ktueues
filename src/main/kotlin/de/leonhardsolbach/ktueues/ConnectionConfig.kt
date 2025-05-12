package de.leonhardsolbach.ktueues

/**
 * Marker interface for describing how Ktueues should connect to its backing queue
 * implementation.  Each supported queue backend should add its own implementation
 * – for example [RedisConfig] or a future `PostgresConfig`.
 *
 * This is implemented as a Kotlin *sealed* interface so that when the list of
 * supported configurations is exhaustively matched the compiler can verify that
 * all cases are handled (very similar to Swift enums with associated values).
 * TODO: Sealed interface might be problematic in with later planned submodules (one for redis, postgres)
 */
sealed interface ConnectionConfig
