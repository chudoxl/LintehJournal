package io.github.chudoxl.linteh.journal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * iOS-side AppTest — Kotlin/Native runComposeUiTest. Body вынесен в
 * [assertHelloLintechRendered] (commonTest helper) — см. WR-05 mitigation.
 */
class AppTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun helloLintech_displaysAllElements() = runComposeUiTest {
        assertHelloLintechRendered()
    }
}
