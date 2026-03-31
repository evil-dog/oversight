plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
}

fun isNonStable(version: String): Boolean {
    val unstable = listOf("alpha", "beta", "rc", "cr", "m", "preview", "snapshot", "dev", "ea")
    return unstable.any { version.lowercase().contains(it) }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf { isNonStable(candidate.version) }
}
