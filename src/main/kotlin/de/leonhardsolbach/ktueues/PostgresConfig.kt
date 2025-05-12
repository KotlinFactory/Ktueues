package de.leonhardsolbach.ktueues

/**
 * Placeholder for a future Postgres backed queue implementation.  The class is
 * added so that the sealed interface [ConnectionConfig] already contains the
 * corresponding implementation.  At the moment it is **not** used anywhere in
 * the code base.
 */
data class PostgresConfig(
    val connectionString: String
) : ConnectionConfig
