package io.github.chudoxl.linteh.journal.core.platform

import co.touchlab.kermit.Logger
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * **WR-08 mitigation:** silent-failure ⇒ logged-warning. Прежде iOS-actual возвращал
 * `?: return` без диагностики — пользователь нажимает «Открыть», ничего не происходит,
 * Phase 2 dev'у не за что зацепиться при reverse-engineering AVERS deep-link рендеринга.
 * Теперь Kermit Logger.w(...) идёт в OSLog, видно через `xcrun simctl spawn ... log stream`.
 */
private const val TAG = "UrlOpener"

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: run {
        Logger.w(TAG) { "Invalid URL, skipping openUrl: $url" }
        return
    }
    UIApplication.sharedApplication.openURL(
        url = nsUrl,
        options = emptyMap<Any?, Any?>(),
        completionHandler = null,
    )
}
