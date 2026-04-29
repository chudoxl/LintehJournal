package io.github.chudoxl.linteh.journal.core.database

/**
 * Factory for [JournalDatabase] — D-19 expect/actual pattern (mirrors `:core:platform/UrlOpener`).
 *
 * Phase 2: invoked with `accountId="default"` (D-16 placeholder).
 * Phase 3: when a real login succeeds, AccountDataPurger orchestrates a rename from
 *    `journal_default.db` to `journal_${realAccountId}.db` (or in-place migration).
 * Phase 5: multi-account scope — each accountId gets its own DB file via this same factory.
 *
 * Security:
 * - iOS: parent directory marked `NSFileProtectionComplete` (D-20) before open.
 *   Phase 6 background polling will require `completeUntilFirstUserAuthentication` —
 *   tracked in CONTEXT Deferred Ideas → Phase 6 prerequisites.
 * - Android: `allowBackup="false"` declared in
 *   `:core:database/src/androidMain/AndroidManifest.xml` (D-21, Plan 01). Manifest merger
 *   folds the fragment into the composeApp final manifest.
 *
 * Per-account isolation invariant: each accountId resolves to a distinct file path; cross-account
 * leak (Pitfall #6) is structurally impossible — no shared file, no shared connection.
 */
expect object DatabaseFactory {
    fun create(accountId: String): JournalDatabase
}
