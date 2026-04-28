package io.github.chudoxl.linteh.journal

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag

/**
 * **WR-05 mitigation:** shared helper для AppTest на iosTest и androidUnitTest.
 *
 * Проблема: оба теста ([io.github.chudoxl.linteh.journal.AppTest] iOS-side и
 * [io.github.chudoxl.linteh.journal.AppTestAndroid] android-side) дублировали 4
 * `onNodeWithTag(...)` проверки. Добавление нового testTag в [App] требовало
 * synchronously обновлять оба файла; легко было drift-нуть (например, фикс
 * только в iosTest).
 *
 * Архитектурное split (AppTest на iosTest + AppTestAndroid на androidUnitTest)
 * сохраняется — оно требуется потому, что Robolectric vs runComposeUiTest на
 * Android JVM unit test без RobolectricRunner валится с NPE в
 * `RobolectricIdlingStrategy.getHasRobolectricFingerprint`. Helper устраняет
 * только code duplication, оставляя platform-specific runners на месте.
 *
 * compose.uiTest dependency живёт в commonTest (composeApp/build.gradle.kts:37-46),
 * поэтому этот файл в commonMain test source set может импортировать
 * `androidx.compose.ui.test.*`.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertHelloLintechRendered() {
    setContent { App() }

    onNodeWithTag("app_title").assertIsDisplayed()
    onNodeWithTag("app_version").assertIsDisplayed()
    onNodeWithTag("privacy_url").assertIsDisplayed()
    onNodeWithTag("open_privacy_button").assertIsDisplayed()
}
