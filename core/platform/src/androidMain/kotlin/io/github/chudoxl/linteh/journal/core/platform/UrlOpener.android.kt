package io.github.chudoxl.linteh.journal.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Контекст пробрасывается через top-level mutable property в Phase 1 — pragmatic bootstrap-bridge.
 * Phase 4: заменится на CompositionLocal из LocalContext.current вместе с Koin DI и Navigation 3.
 * См. RESEARCH.md Open Question #4 для обоснования.
 *
 * TODO(Phase 4): Refactor to CompositionLocal-based access (LocalContext.current + Koin scope).
 */
internal lateinit var androidApplicationContext: Context

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    androidApplicationContext.startActivity(intent)
}
