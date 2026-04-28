package io.github.chudoxl.linteh.journal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

class AppTest {
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
