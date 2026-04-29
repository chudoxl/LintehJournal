package io.github.chudoxl.linteh.journal.core.database

/**
 * Platform-specific factory for an in-memory [JournalDatabase] used by tests.
 *
 * - **Android JVM (androidUnitTest):** uses Robolectric's `ApplicationProvider` for context
 *   and `AndroidSQLiteDriver` (Robolectric-friendly) — `BundledSQLiteDriver` JNI .so cannot
 *   load under Robolectric.
 * - **iOS Native (iosTest):** uses the no-context Room KMP overload + `BundledSQLiteDriver`.
 *
 * `JournalDatabase` is constructed and returned directly so each platform may apply its
 * native driver inside the actual; common tests simply consume the resulting database.
 *
 * Plan 05 (AccountDataPurger / file-system tests) will introduce file-backed test variants
 * that exercise NSFileProtection and Android `getDatabasePath` resolution. Plan 03 only
 * needs in-memory contract verification.
 */
expect fun newInMemoryDatabase(): JournalDatabase
