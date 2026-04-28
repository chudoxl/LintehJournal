package io.github.chudoxl.linteh.journal

import android.app.Application
import io.github.chudoxl.linteh.journal.core.platform.initApplicationContext

/**
 * Bootstrap Application — инициализирует platform-level context для openUrl().
 *
 * Phase 1 pattern: одноразовый initializer в :core:platform (BL-02 fix — заменили
 * mutable lateinit var на private holder + initApplicationContext(...)).
 * Phase 4: заменится на Koin DI инициализацию (modules { single<Context> { ... } }).
 */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initApplicationContext(this)
    }
}
