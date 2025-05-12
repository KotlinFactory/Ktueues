package de.leonhardsolbach.ktueues.redis

import de.leonhardsolbach.ktueues.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPooled
import java.net.URI
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Redis based implementation of [QueueAdapter] using the Jedis client library.
 *
 * The previous implementation relied on *Kreds* which does not support all
 * authentication variants.  Jedis on the other hand supports TLS and ACL
 * authentication and therefore satisfies our requirements.
 */
class RedisQueueAdapter(
    private val redisConfig: RedisConfig = RedisConfig(),
    private val json: Json = Json
) : QueueAdapter {

    private val logger = LoggerFactory.getLogger(RedisQueueAdapter::class.java)

    /* --------------------------------------------------------------------- */
    /* Jedis client setup                                                    */
    /* --------------------------------------------------------------------- */

    /**
     * Underlying Jedis client.  [JedisPooled] is thread-safe and internally
     * manages a small connection pool so it can be safely shared between
     * coroutines.
     */
    private val client: JedisPooled = buildClient()

    private fun buildClient(): JedisPooled =
        redisConfig.connectionString?.let { uriString ->
            JedisPooled(URI(uriString))
        } ?: run {
            val hostAndPort = HostAndPort(redisConfig.host, redisConfig.port)
            val cfg = DefaultJedisClientConfig.builder()
                .password(redisConfig.password)
                .database(redisConfig.database)
                .build()
            JedisPooled(hostAndPort, cfg)
        }

    /** Mutex to ensure the Lua script is loaded only once. */
    private val scriptLoadMutex = Mutex()
    private var moveAndPopSha: String? = null

    /* --------------------------------------------------------------------- */
    /* Public QueueAdapter implementation                                    */
    /* --------------------------------------------------------------------- */

    override suspend fun enqueue(
        taskIdentifier: String,
        serializedPayload: String,
        delay: Duration?,
        maxRetries: Int,
        currentAttempt: Int,
        queueName: String
    ) = withContext(Dispatchers.IO) {
        val message = InternalTaskMessage(
            id = UUID.randomUUID().toString(),
            taskIdentifier = taskIdentifier,
            payload = serializedPayload,
            attempt = currentAttempt,
            maxRetries = maxRetries
        )

        val msgString = json.encodeToString(message)

        if (delay == null || delay.isNegative() || delay.inWholeMilliseconds == 0L) {
            client.lpush(queueKey(queueName), msgString)
        } else {
            val score = System.currentTimeMillis() + delay.inWholeMilliseconds
            safeZAdd(delayedKey(queueName), score.toDouble(), msgString)
        }
        logger.info(msgString)
    }

    override suspend fun enqueueScheduled(
        taskIdentifier: String,
        chron: String?,
        queueName: String
    ) = withContext(Dispatchers.IO) {
        val expression = chron ?: "* * * * *" // default: once every minute

        // Persist definition & initial next run timestamp in a single Lua script so that
        // we do not need to bother with the high-level API variations.
        val now = System.currentTimeMillis()
        val next = computeNextExecutionMillis(expression)

        // language=Lua
        val script = """
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
            return 1
        """

        client.eval(
            script,
            listOf(CRON_DEFINITION_HASH, CRON_NEXT_ZSET),
            listOf(taskIdentifier, expression, next.toString())
        )

        logger.info("Registered cron task $taskIdentifier with expression '$expression' (next run: $next)")
    }

    override suspend fun dequeue(workerId: String, queueName: String, pollTimeout: Duration): DequeuedTask? =
        withContext(Dispatchers.IO) {

            ensureLuaScriptLoaded()

            val timeoutSeconds = pollTimeout.coerceAtLeast(Duration.ZERO).inWholeSeconds.toInt()

            val result = client.evalsha(
                moveAndPopSha!!,
                listOf(
                    delayedKey(queueName), // KEYS[1]
                    queueKey(queueName),   // KEYS[2]
                    CRON_NEXT_ZSET,        // KEYS[3]
                    CRON_DEFINITION_HASH   // KEYS[4]
                ),
                listOf(System.currentTimeMillis().toString(), timeoutSeconds.toString())
            ) as? List<*>

            // The Lua script returns either nil or a two-element array
            if (result == null || result.size != 2) return@withContext null

            val msgString = result[1] as? String ?: return@withContext null

            val internal = try {
                json.decodeFromString(InternalTaskMessage.serializer(), msgString)
            } catch (e: Exception) {
                logger.error("Failed to parse task message, dropping: $msgString", e)
                return@withContext null
            }

            DequeuedTask(
                id = internal.id,
                taskIdentifier = internal.taskIdentifier,
                serializedPayload = internal.payload,
                currentAttempt = internal.attempt,
                maxRetries = internal.maxRetries,
                acknowledge = {
                    // Using the simple list-pop strategy so nothing to do here
                },
                negativeAcknowledge = negativeAcknowledge@{ requeue: Boolean, delay: Duration? ->
                    if (!requeue) return@negativeAcknowledge

                    val nextAttempt = internal.attempt + 1
                    val newMsg = internal.copy(attempt = nextAttempt)
                    val encoded = json.encodeToString(newMsg)

                    if (delay == null || delay.isNegative() || delay.inWholeMilliseconds == 0L) {
                        client.rpush(queueKey(queueName), encoded)
                    } else {
                        val score = System.currentTimeMillis() + delay.inWholeMilliseconds
                        safeZAdd(delayedKey(queueName), score.toDouble(), encoded)
                    }
                }
            )
        }

    /* --------------------------------------------------------------------- */
    /* Helper / internal                                                     */
    /* --------------------------------------------------------------------- */

    @Serializable
    private data class InternalTaskMessage(
        val id: String,
        val taskIdentifier: String,
        val payload: String,
        val attempt: Int,
        val maxRetries: Int
    )

    private fun queueKey(queue: String) = "ktueues:queue:$queue"
    private fun delayedKey(queue: String) = "ktueues:delayed:$queue"

    private suspend fun ensureLuaScriptLoaded() {
        if (moveAndPopSha != null) return
        scriptLoadMutex.withLock {
            if (moveAndPopSha != null) return

            // language=Lua
            val script = """
                ---
                -- KEYS[1] = delayed zset
                -- KEYS[2] = ready list
                -- KEYS[3] = cron next zset
                -- KEYS[4] = cron definition hash
                -- ARGV[1] = now (millis)
                -- ARGV[2] = blpop timeout (seconds)
                local now        = tonumber(ARGV[1])

                -- Move due delayed tasks
                local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now)
                if (#due > 0) then
                    for i, v in ipairs(due) do
                        redis.call('ZREM', KEYS[1], v)
                        redis.call('RPUSH', KEYS[2], v)
                    end
                end

                -- Move due cron tasks
                local cronDue = redis.call('ZRANGEBYSCORE', KEYS[3], '-inf', now)
                if (#cronDue > 0) then
                    for i, t in ipairs(cronDue) do
                        redis.call('ZREM', KEYS[3], t)
                        -- Get cron expression
                        local expr = redis.call('HGET', KEYS[4], t)
                        if (expr) then
                            -- Schedule next execution (simple: +60s) if expression present
                            local next = now + 60000
                            redis.call('ZADD', KEYS[3], next, t)
                        end
                        -- Push to ready list with empty payload. Workers are expected to look up the task by identifier.
                        local msg = cjson.encode({
                            id = tostring(now) .. ':' .. t,
                            taskIdentifier = t,
                            payload = "{}",
                            attempt = 1,
                            maxRetries = 0
                        })
                        redis.call('RPUSH', KEYS[2], msg)
                    end
                end

                -- Finally, block pop
                return redis.call('BLPOP', KEYS[2], ARGV[2])
            """

            moveAndPopSha = client.scriptLoad(script)
            logger.debug("Loaded move-and-pop Lua script, sha=$moveAndPopSha")
        }
    }

    /**
     * Extremely naive *cron* handling – for the purposes of this example it will simply
     * execute the task once every minute (60 000 ms).
     */
    private fun computeNextExecutionMillis(@Suppress("UNUSED_PARAMETER") expression: String): Long =
        System.currentTimeMillis() + 60_000

    /**
     * Helper that performs a simple ZADD using EVAL to keep the dependency surface small.
     */
    private fun safeZAdd(key: String, score: Double, member: String) {
        client.zadd(key, score, member)
    }

    companion object {
        private const val CRON_DEFINITION_HASH = "ktueues:cron:definitions"
        private const val CRON_NEXT_ZSET = "ktueues:cron:next"
    }
}
