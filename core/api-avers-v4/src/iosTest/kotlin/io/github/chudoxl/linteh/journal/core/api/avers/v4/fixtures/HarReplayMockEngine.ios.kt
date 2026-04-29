package io.github.chudoxl.linteh.journal.core.api.avers.v4.fixtures

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv

/**
 * iOS Native actual for [loadHarFile]. Resolves the absolute path of the project's
 * `fixtures/sanitized/` directory via the `FIXTURES_DIR` environment variable that
 * Gradle exports onto the Kotlin/Native test executable (see
 * `core/api-avers-v4/build.gradle.kts` — `KotlinNativeTarget` test binaries block).
 *
 * Why an env-var (and not [System.getProperty]): Kotlin/Native has no JVM properties;
 * env-vars are the standard bridge for test-time configuration. POSIX `getenv` is
 * provided by `platform.posix` and works on iosX64 / iosSimulatorArm64 / iosArm64
 * simulators alike (the only path actually executed in CI macos-15 is iosX64Test).
 *
 * Why [NSString.stringWithContentsOfFile] (and not Kotlin's File API):
 * `iosMain`/`iosTest` source sets do not have `java.io.File`. NSFileManager +
 * NSString cover the I/O surface required for HAR fixture replay.
 *
 * Plan 09 — Pitfall closures:
 *  - Plan 06 left this file as `error("...")` stub (Phase 2 SUMMARY records the
 *    deferral); Plan 09 replaces it with a real implementation.
 *  - The companion JVM actual at
 *    `androidUnitTest/.../HarReplayMockEngine.android.kt` reads the same path via
 *    `System.getProperty("fixtures.dir")` — both actuals operate on identical
 *    sanitized HAR files; only the I/O facade differs.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun loadHarFile(relativePath: String): String {
    val fixturesDir = getenv("FIXTURES_DIR")?.toKString()
        ?: error(
            "FIXTURES_DIR env-var not set on iOS Native test executable. " +
                "Check core/api-avers-v4/build.gradle.kts — Native test tasks must " +
                "export FIXTURES_DIR via " +
                "binaries.withType<TestExecutable>().configureEach { runTask?.environment(...) }.",
        )

    val absolutePath = "$fixturesDir/$relativePath"

    val fileExists = NSFileManager.defaultManager.fileExistsAtPath(absolutePath)
    if (!fileExists) {
        error(
            "HAR fixture not found at $absolutePath " +
                "(FIXTURES_DIR=$fixturesDir, relativePath=$relativePath)",
        )
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    val contents = NSString.stringWithContentsOfFile(
        path = absolutePath,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

    return contents
        ?: error(
            "Failed to read HAR fixture at $absolutePath as UTF-8 " +
                "(file exists but stringWithContentsOfFile returned null)",
        )
}
