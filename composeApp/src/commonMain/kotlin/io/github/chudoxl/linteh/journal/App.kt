package io.github.chudoxl.linteh.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.chudoxl.linteh.journal.core.platform.openUrl
import lintehjournal.composeapp.generated.resources.Res
import lintehjournal.composeapp.generated.resources.app_name
import lintehjournal.composeapp.generated.resources.open_button
import lintehjournal.composeapp.generated.resources.privacy_policy_url
import org.jetbrains.compose.resources.stringResource

/**
 * Hello LinTech screen — Phase 1 «working code»: показывает имя приложения, версию из BuildKonfig,
 * Privacy Policy URL и кнопку «Открыть» (диспатчит URL в системный браузер через
 * [io.github.chudoxl.linteh.journal.core.platform.openUrl]).
 *
 * NB про package Compose Resources: фактический generated package — `lintehjournal.composeapp.generated.resources`
 * (без подчёркивания), потому что Compose Resources нормализует имя проекта `LintehJournal` в lowercase
 * без spec-символов. Plan 02 ожидал `linteh_journal.*` — установлено в SUMMARY как verified discovery.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.testTag("app_title"),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "v${BuildKonfig.VERSION_NAME} (${BuildKonfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("app_version"),
                )
                Spacer(Modifier.height(24.dp))
                val privacyUrl = stringResource(Res.string.privacy_policy_url)
                Text(
                    text = privacyUrl,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("privacy_url"),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { openUrl(privacyUrl) },
                    modifier = Modifier.testTag("open_privacy_button"),
                ) {
                    Text(stringResource(Res.string.open_button))
                }
            }
        }
    }
}
