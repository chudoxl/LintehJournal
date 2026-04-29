package io.github.chudoxl.linteh.journal.core.network.cookies

import io.github.chudoxl.linteh.journal.core.database.JournalDatabase

/**
 * Platform-specific factory for an in-memory [JournalDatabase] used by
 * [RoomCookiesStorage] tests in `:core:network/commonTest`.
 *
 * Mirrors `:core:database/InMemoryRoom.kt` (Plan 02-03 Spec/Wrapper pattern):
 *  - Android JVM (androidUnitTest): `Room.inMemoryDatabaseBuilder + ApplicationProvider +
 *    AndroidSQLiteDriver` (Robolectric-friendly — `BundledSQLiteDriver` JNI cannot load
 *    under Robolectric).
 *  - iOS Native (iosTest): no-context Room KMP overload + `BundledSQLiteDriver`.
 */
expect fun newInMemoryJournalDatabase(): JournalDatabase
