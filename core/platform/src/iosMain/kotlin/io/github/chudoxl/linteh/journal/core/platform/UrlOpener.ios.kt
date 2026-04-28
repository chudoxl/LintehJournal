package io.github.chudoxl.linteh.journal.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return  // defensive: invalid URL → no-op (no crash)
    UIApplication.sharedApplication.openURL(
        url = nsUrl,
        options = emptyMap<Any?, Any?>(),
        completionHandler = null,
    )
}
