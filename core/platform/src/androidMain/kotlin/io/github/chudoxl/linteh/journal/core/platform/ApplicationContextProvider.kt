package io.github.chudoxl.linteh.journal.core.platform

import android.content.Context

/**
 * Public accessor for the application Context registered via [initApplicationContext]
 * in `UrlOpener.android.kt` (BL-02 holder pattern, Phase 1).
 *
 * Phase 2: consumed by `:core:database/DatabaseFactory.android.kt` for `Room.databaseBuilder`.
 * Phase 3+: KVault (`:core:platform/...` actual), background workers, and other
 * platform-bound components MAY consume this — keeps single source of truth for the holder,
 * no extra `lateinit var` globals.
 *
 * Throws `IllegalStateException` if accessed before `Application.onCreate()` calls
 * `initApplicationContext(...)`. The holder is `internal var` in `UrlOpener.android.kt`
 * so this accessor reads it across files within the same `:core:platform/androidMain`
 * source set without exposing it to downstream modules.
 *
 * This is a programmer-error (early use) signal, NOT a runtime fallback — fix by
 * ensuring `MainApplication.onCreate()` always calls `initApplicationContext(this)`
 * (already true in Phase 1; see `composeApp/src/androidMain/kotlin/.../MainApplication.kt`).
 */
fun getApplicationContext(): Context = applicationContextHolder
    ?: error("Call initApplicationContext() in Application.onCreate() first")
