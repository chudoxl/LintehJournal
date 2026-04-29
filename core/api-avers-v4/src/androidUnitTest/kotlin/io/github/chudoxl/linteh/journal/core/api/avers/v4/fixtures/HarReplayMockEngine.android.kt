package io.github.chudoxl.linteh.journal.core.api.avers.v4.fixtures

import java.io.File

/**
 * Android JVM (Robolectric) actual: read sanitized HAR files from the project root via
 * the `fixtures.dir` system property. Set by `tasks.withType<Test>` in the module's
 * build.gradle.kts.
 */
actual fun loadHarFile(relativePath: String): String {
    val fixturesDir = System.getProperty("fixtures.dir")
        ?: error(
            "fixtures.dir system property not set — check core/api-avers-v4/build.gradle.kts " +
                "tasks.withType<Test>.systemProperty registration",
        )
    return File(fixturesDir, relativePath).readText(Charsets.UTF_8)
}
