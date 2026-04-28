package io.github.chudoxl.linteh.journal.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Контекст пробрасывается через инкапсулированный private holder в Phase 1 — pragmatic
 * bootstrap-bridge. Phase 4: заменится на CompositionLocal из LocalContext.current вместе
 * с Koin DI и Navigation 3. См. RESEARCH.md Open Question #4 для обоснования.
 *
 * **BL-02 fix:** Заменили `lateinit var androidApplicationContext: Context` (top-level
 * mutable global, public) на private holder + одноразовый initializer:
 * - `initApplicationContext(ctx)` — единственный путь записи; повторный вызов кидает IllegalStateException;
 * - getter `androidApplicationContext` приватный, доступен только внутри этого файла;
 * - misuse (вызов openUrl до Application.onCreate) даёт ясный error вместо silent
 *   `UninitializedPropertyAccessException` (опасно при ContentProvider, инициализирующемся
 *   до Application.onCreate).
 *
 * **Public API** ограничен `initApplicationContext(...)` — downstream-код больше не может
 * переприсвоить контекст и не может прочитать его напрямую. iOS-actual параллельный по
 * encapsulation: NSURL.URLWithString defensive guard уже на месте (см. UrlOpener.ios.kt).
 *
 * TODO(Phase 4): Refactor to CompositionLocal-based access (LocalContext.current + Koin scope) —
 * тогда private holder исчезнет естественно.
 */
private var applicationContextHolder: Context? = null

fun initApplicationContext(context: Context) {
    check(applicationContextHolder == null) {
        "androidApplicationContext already initialized"
    }
    applicationContextHolder = context.applicationContext
}

private val androidApplicationContext: Context
    get() = applicationContextHolder
        ?: error("Call initApplicationContext() in Application.onCreate() first")

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    androidApplicationContext.startActivity(intent)
}
