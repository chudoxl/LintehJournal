package io.github.chudoxl.linteh.journal.core.api.avers.v4.fixtures

/**
 * iOS Native actual: HAR file resource bundling is OUT OF SCOPE for Phase 2 / Plan 02-06.
 * Plan 02-09 (final integration smoke) is responsible for wiring iOS Native test
 * resources. Until then, iOS tests using this helper MUST be `@Ignore`d.
 *
 * EndpointsContractTest is annotated `@Ignore` on iosTest — Android JVM tests cover the
 * Plan 02-06 contract surface fully; iOS verification arrives in Plan 02-09.
 */
actual fun loadHarFile(relativePath: String): String {
    error(
        "iOS Native fixture loading not implemented in Phase 2 (Plan 02-06). " +
            "Tests using this engine must be @Ignore'd on iosTest. Path requested: $relativePath",
    )
}
