package io.github.chudoxl.linteh.journal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppTestAndroid {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun helloLintech_displaysAllElements() = runComposeUiTest {
        setContent { App() }

        onNodeWithTag("app_title").assertIsDisplayed()
        onNodeWithTag("app_version").assertIsDisplayed()
        onNodeWithTag("privacy_url").assertIsDisplayed()
        onNodeWithTag("open_privacy_button").assertIsDisplayed()
    }
}
