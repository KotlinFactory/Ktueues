# Ktueues – Kotlin Coroutine Task Queues

Light-weight, embeddable job-/task-queue abstraction written in Kotlin.  
Ships with a production-ready Redis backend and supports:

* FIFO processing with multiple independent queues
* Delayed jobs ("run after X minutes")
* Cron / periodic jobs via simple cron expressions
* Configurable retry logic & back-off handled by each `Task`
* Structured, coroutine-friendly execution model

> The project is in **experimental** – state do not use in mission-critical systems yet.

---

## Quick start

```kotlin
val ktueues = KtueuesBuilder.create()
    .withRedisHost("localhost")
    .withRedisPort(6379)
    .build()

// 1) Regular task -------------------------------------------------------------

data class HelloPayload(val name: String)

class HelloTask : Task<HelloPayload> {
    override val taskType = "hello"

    override suspend fun execute(payload: HelloPayload, context: TaskContext) {
        context.logger.info("Hello ${payload.name} from worker!")
    }
}

ktueues.registerTask(HelloTask())

// Dispatch one immediately
ktueues.taskDispatcher.dispatch(HelloTask(), HelloPayload("world"))

// 2) Cron / periodic task -----------------------------------------------------

class CleanupTask : ScheduledTask {
    override val taskType = "db-cleanup"

    override suspend fun execute(context: TaskContext) {
        context.logger.info("Running database cleanup …")
    }
}

ktueues.registerScheduledTask(CleanupTask())

// Tell the queue to run it every 5 minutes
ktueues.taskDispatcher.registerScheduled(CleanupTask(), "0 */5 * * * ?")

// -----------------------------------------------------------------------------
// Start workers (normally you would do this in your application bootstrap)

val worker = Worker(ktueues.queueAdapter, ktueues.taskRegistry)
worker.start()

```

## Project structure

```
src/main/kotlin/
    de.leonhardsolbach.ktueues.*   – Core types (Task, Worker, …)
    de.leonhardsolbach.ktueues.redis.RedisQueueAdapter – Redis backend
```

### Key concepts

| Type            | Responsibility |
|-----------------|----------------|
| `Task<P>`       | Unit of work with payload `P`. Implements `execute()` and optional retry logic. |
| `ScheduledTask` | Same as `Task` but **without** payload – triggered by cron expression instead of dispatch. |
| `QueueAdapter`  | Backend abstraction (Redis implementation provided). |
| `Worker`        | Coroutine that blocks on the queue, deserialises payload and calls the appropriate task. |
| `TaskDispatcher`| Convenience wrapper to enqueue / schedule jobs. |

### Redis data-model

* `ktueues:queue:{name}`   – List containing ready-to-run jobs (RPUSH / BLPOP)
* `ktueues:delayed:{name}` – ZSET with score = "visible at" epoch-ms (for delayed jobs)
* `ktueues:cron:definitions`    – Hash containing `taskType → cron expression`
* `ktueues:cron:next`           – ZSET with score = next execution timestamp (ms)

> A small Lua script moves due items from the ZSETs into the ready list and performs the blocking pop – guaranteeing atomicity without polling.

## Building / running

Requires **JDK 17+** and **Gradle**:

```bash
./gradlew build        # compile & run tests
./gradlew publishToMavenLocal  # optional
```

---

### TODO / ideas

* Expose builder for custom cron calculation (currently naïve 60-second step)
* Cluster-wide worker leases to avoid duplicate cron execution
* Metrics + health endpoints

PRs welcome ☺︎
