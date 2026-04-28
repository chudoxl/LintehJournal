package io.github.chudoxl.linteh.journal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android-side AppTest — Robolectric + runComposeUiTest. Body вынесен в
 * [assertHelloLintechRendered] (commonTest helper) — см. WR-05 mitigation.
 *
 * @RunWith(RobolectricTestRunner) обязателен: без него Compose UI test на
 * Android JVM падает с NPE в RobolectricIdlingStrategy.getHasRobolectricFingerprint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppTestAndroid {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun helloLintech_displaysAllElements() = runComposeUiTest {
        assertHelloLintechRendered()
    }
}
