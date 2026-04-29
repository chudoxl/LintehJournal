package io.github.chudoxl.linteh.journal.core.network.credentials

/**
 * Provides AVERS credentials for auto-relogin (D-26).
 *
 * Phase 2: noop/test-only implementation — supplied via Mokkery in commonTest.
 *    A `NoopCredentialProvider` (returns null always) lives below for production wiring
 *    until Phase 3 ships KVault.
 * Phase 3: KVault-backed implementation injected via DI. The interface contract MUST NOT
 *    change between Phase 2 and Phase 3.
 *
 * Security note: returns `null` when credentials are not available; never throws.
 * Caller (`AversAuthInterceptor`) maps `null` -> original 401 response -> `:core:api-avers-v4`
 * boundary maps to `AversApiError.Unauthorized`, surfacing the login screen to the user.
 *
 * Per-account scope: implementations MUST honor `accountId` — Phase 5 multi-account scenarios
 * depend on this. Returning credentials for accountId B when asked for A is a critical
 * cross-account leak (Pitfall #6 mitigation).
 */
interface CredentialProvider {
    /**
     * @return (login, password) pair for the given accountId, or null if no credentials are
     *   stored. Never throws — failure modes (KVault locked, decryption error in Phase 3) MUST
     *   return null and log internally.
     */
    suspend fun get(accountId: String): Pair<String, String>?
}

/** Phase 2 default — always returns null. Phase 3 replaces with KVaultCredentialProvider. */
object NoopCredentialProvider : CredentialProvider {
    override suspend fun get(accountId: String): Pair<String, String>? = null
}
