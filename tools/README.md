# tools/

Phase 2 dev-only tooling for AVERS API reverse-engineering.

## Files

- `sanitize-har.py` — strip PII from captured HAR files before commit.
- `sanitize-rules.yaml` — configurable cookie-name list (extend without touching code).

## Capture workflow

1. **Setup test account credentials** (one-time):
   ```bash
   cp .env.local.example .env.local  # if example exists; otherwise create:
   # Format:
   # CHILD1_LOGIN=login_for_account_A
   # CHILD1_PASSWORD=password_A
   # CHILD2_LOGIN=login_for_account_B
   # CHILD2_PASSWORD=password_B
   ```
   `.env.local` is gitignored (D-04). Never commit it.

2. **Capture HAR via Chrome DevTools (D-02)**:
   - Open Chrome in incognito + clean profile + disable all extensions (Pitfall #6 — adblocker pollution).
   - Navigate to https://journal.school28-kirov.ru
   - Open DevTools (F12) → Network tab → ensure recording active (red circle).
   - Log in with one test account.
   - Click each section: оценки, расписание, ДЗ, посещаемость, сообщения. Wait for each network call to complete.
   - Right-click any request in Network tab → "Save all as HAR with content".
   - Save to `fixtures/raw/account-A/<endpoint>.har`. Repeat for account B.

3. **Sanitize**:
   ```bash
   pip3 install --user pyyaml  # one-time
   python3 tools/sanitize-har.py fixtures/raw/account-A/login.har fixtures/sanitized/account-A/login.har
   # Repeat for each endpoint and each account
   ```

4. **Verify**:
   ```bash
   bash tests/sanitize-har-canary.sh
   ```
   Must exit 0. If it greps any real surname, sanitization rules need extension in `sanitize-rules.yaml`.

5. **Commit only `fixtures/sanitized/`**. Never commit `fixtures/raw/`.

## Why Chrome DevTools (not mitmproxy)

D-02: dev-host is Linux Mint. Chrome DevTools HAR-export is simpler (no proxy setup, no SSL cert install). Trade-off: captures use desktop User-Agent. Mobile UA divergence risk is mitigated by parameterized UA in `HttpClientFactory` and the first Android run in Phase 4.

## D-04 storage layout

- `fixtures/raw/` — gitignored. Real ФИО, real cookies. Local dev only.
- `fixtures/sanitized/` — committed. Used by `commonTest` HarReplayMockEngine (Plan 06) and CI canary script.
- `.env.local` — gitignored. Test account credentials for `tools/manual-smoke.sh` (Plan 08).
