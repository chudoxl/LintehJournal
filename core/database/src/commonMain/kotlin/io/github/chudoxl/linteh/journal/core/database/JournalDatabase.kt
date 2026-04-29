package io.github.chudoxl.linteh.journal.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.chudoxl.linteh.journal.core.database.dao.CookieDao
import io.github.chudoxl.linteh.journal.core.database.entity.CookieEntity

/**
 * Root Room database for one account.
 *
 * Phase 2: schema version 1, single `cookies` table (D-17). One DB file per account
 * (`journal_${accountId}.db`, D-16) — Phase 2 uses accountId="default", Phase 3 (login)
 * renames to real account ID via AccountDataPurger orchestration.
 *
 * Phase 3 will introduce schema version 2 (accounts table) via Room migration; Phase 4+
 * adds schema versions 3..N (grades, lessons, homework, attendance, messages).
 *
 * Note on the constructor pattern: `expect class JournalDatabase` is rejected as anti-pattern
 * (RESEARCH.md, "fights Koin/DI"). Instead use plain class + `expect object
 * JournalDatabaseConstructor : RoomDatabaseConstructor<JournalDatabase>` (Room 2.8 KMP standard).
 */
@Database(
    entities = [CookieEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(JournalDatabaseConstructor::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun cookieDao(): CookieDao
}
