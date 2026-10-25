plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("io.github.ben-manes.versions") version "0.66.0"
    id("io.github.takahirom.roborazzi") version "1.77.0" apply false
}

fun isNonStable(version: String): Boolean {
    val unstable = listOf("alpha", "beta", "rc", "cr", "m", "preview", "snapshot", "dev", "ea")
    return unstable.any { version.lowercase().contains(it) }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf { isNonStable(candidate.version) }
}
