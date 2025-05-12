plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "de.leonhardsolbach"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Core Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.20")

    // Redis client (Jedis)
    implementation("redis.clients:jedis:5.1.0")


    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.10")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}