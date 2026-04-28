package io.github.chudoxl.linteh.journal

import android.app.Application
import io.github.chudoxl.linteh.journal.core.platform.androidApplicationContext

/**
 * Bootstrap Application — инициализирует platform-level context для openUrl().
 *
 * Phase 1 pattern: pragmatic top-level lateinit var в :core:platform.
 * Phase 4: заменится на Koin DI инициализацию (modules { single<Context> { ... } }).
 */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        androidApplicationContext = applicationContext
    }
}
