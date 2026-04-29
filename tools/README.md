# tools/

Phase 2 dev-only tooling for AVERS API reverse-engineering.

## Files

- `capture-avers-fixtures.py` — programmatic HAR fixture capture (stdlib only). Replicates the SPA's auth flow + bootstrap chain + 6 target endpoints.
- `sanitize-har.py` — strip PII from captured HAR files before commit.
- `sanitize-rules.yaml` — configurable cookie names, login param names, explicit redaction strings.

## Capture workflow (programmatic — primary path)

1. **Setup test account credentials** (one-time):
   ```
   # .env.local (gitignored)
   CHILD1_LOGIN=<account_A_login>      # Cyrillic surname OK
   CHILD1_PASSWORD=<password_A>
   CHILD2_LOGIN=<account_B_login>
   CHILD2_PASSWORD=<password_B>
   ```

2. **Capture both accounts**:
   ```bash
   python3 tools/capture-avers-fixtures.py            # both A and B
   python3 tools/capture-avers-fixtures.py --account A   # one only
   python3 tools/capture-avers-fixtures.py --probe       # login probe only (no chain)
   ```
   Writes raw fixtures to `fixtures/raw/account-{A,B}/<endpoint>.har`. The script:
   - Reads `.env.local` (no value ever logged)
   - POSTs `/login` with `l=<login>&p=<sha1_hex(password)>`
   - Manually injects 3 client-side auth cookies (`ys-user`/`ys-password`/`ys-userId`) into the cookie jar — AVERS auth scheme reverse-engineered from `site/client/Journal.js` + ExtJS `Ext.state.CookieProvider`. Server never issues `Set-Cookie`.
   - Cookie values use JS `escape()` polyfill (`%uXXXX` for codepoints ≥256) — Cyrillic logins require this; standard URL-encoders produce UTF-8 byte sequences which the server rejects.
   - Walks bootstrap chain: `get_user_data → get_uch_year → GET_STUDENT_CLASS → GET_STUDENT_PARALLEL`
   - Captures 6 target endpoints into per-endpoint HAR files (with bootstrap context entries for grades/homework where applicable)
   - Tolerates `IncompleteRead` (AVERS nginx announces wrong `Content-Length` on `/`)

3. **Sanitize**:
   ```bash
   pip3 install --user pyyaml  # one-time
   for ACC in A B; do
     for EP in login grades schedule homework attendance messages; do
       python3 tools/sanitize-har.py "fixtures/raw/account-$ACC/$EP.har" "fixtures/sanitized/account-$ACC/$EP.har"
     done
   done
   ```

4. **Verify**:
   ```bash
   bash tests/sanitize-har-canary.sh
   ```
   Must exit 0. If it greps any real surname, extend `sanitize-rules.yaml`:
   - Add to `cookie_names:` if a new auth/session cookie is observed
   - Add to `replace_strings:` if a single-word surname surfaces in an API response (where the 2-word ФИО regex misses it)
   - Add to `login_param_names:` if the auth POST body uses different field names

5. **Commit only `fixtures/sanitized/`**. Never commit `fixtures/raw/`.

## Capture workflow (Chrome DevTools — fallback)

Use this only if the programmatic path breaks (server changes auth, anti-bot triggers, etc.).

1. Open Chrome in incognito + clean profile + disable all extensions (Pitfall #6 — adblocker pollution).
2. Navigate to https://journal.school28-kirov.ru
3. Open DevTools (F12) → Network tab → ensure recording active (red circle), enable "Preserve log".
4. Log in with one test account.
5. Click each section: оценки, расписание, ДЗ, посещаемость, сообщения. Wait for each network call to complete.
6. Right-click any request in Network tab → "Save all as HAR with content".
7. Save to `fixtures/raw/account-A/<endpoint>.har`. Repeat for account B.
8. Same sanitize/verify steps as above.

## Why ys-* cookie polyfilling matters

The AVERS server uses an unusual auth scheme where session state lives entirely in three client-set cookies (`ys-user`, `ys-password`, `ys-userId`). Values are encoded by `Ext.state.Provider.encodeValue` — `<type>:<value>` then JS `escape()`. For Cyrillic logins (the default at `journal.school28-kirov.ru`) this means codepoints ≥256 are emitted as `%uXXXX`, NOT UTF-8 byte sequences. Standard URL-encoders (`urllib.parse.quote`, Java/Kotlin `URLEncoder.encode`) produce `%XX%XX` UTF-8 — server rejects and returns empty `[]` for all `/act/` calls.

Plan 06 must implement an equivalent polyfill in the Kotlin client (`:core:platform` module suggested).

## D-04 storage layout

- `fixtures/raw/` — gitignored. Real ФИО, real cookies. Local dev only.
- `fixtures/sanitized/` — committed. Used by `commonTest` HarReplayMockEngine (Plan 06) and CI canary script.
- `.env.local` — gitignored. Test account credentials for `capture-avers-fixtures.py` and future `tools/manual-smoke.sh` (Plan 08).
