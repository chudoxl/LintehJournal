package io.github.chudoxl.linteh.journal.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Контекст пробрасывается через top-level mutable property в Phase 1 — pragmatic bootstrap-bridge.
 * Phase 4: заменится на CompositionLocal из LocalContext.current вместе с Koin DI и Navigation 3.
 * См. RESEARCH.md Open Question #4 для обоснования.
 *
 * **Visibility**: Намеренно `public` (без явного модификатора) — нужен доступ из :composeApp
 * (MainApplication.onCreate инициализирует), а Kotlin's `internal` = same Gradle module, что
 * не работает cross-module. Плановая спецификация Task 1 указывала `internal`, но это compile-error
 * (cannot access from :composeApp); скорректировано как Rule 1 deviation.
 *
 * TODO(Phase 4): Refactor to CompositionLocal-based access (LocalContext.current + Koin scope) —
 * тогда top-level mutable исчезнет и visibility-проблема снимается естественно.
 */
lateinit var androidApplicationContext: Context

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    androidApplicationContext.startActivity(intent)
}
