package io.github.chudoxl.linteh.journal.core.database

import androidx.room.RoomDatabaseConstructor

/**
 * Room KMP standard constructor object — required when @Database class is in commonMain.
 *
 * Per the official Room KMP setup (developer.android.com/kotlin/multiplatform/room), the
 * Room compiler/KSP generates per-target `actual object JournalDatabaseConstructor` bodies
 * automatically; we declare only the `expect` here.
 *
 * The `@Suppress("NO_ACTUAL_FOR_EXPECT")` annotation is the documented workaround until
 * the Kotlin compiler natively understands compiler-plugin-generated actuals.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object JournalDatabaseConstructor : RoomDatabaseConstructor<JournalDatabase> {
    override fun initialize(): JournalDatabase
}
