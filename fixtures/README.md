# fixtures/

Test fixtures for `commonTest` HAR replay (Plan 06).

## Layout (D-04)

- `raw/` — **GITIGNORED**. Real captures with real ФИО + cookies. Local dev only.
- `sanitized/` — **COMMITTED**. PII-stripped via `tools/sanitize-har.py`.
  - `account-A/{login,grades,schedule,homework,attendance,messages}.har`
  - `account-B/{login,grades,schedule,homework,attendance,messages}.har`

## Regenerate

See `tools/README.md` capture workflow.

## CI guarantee

`tests/sanitize-har-canary.sh` runs on every PR — fails if any real surname leaks into committed sanitized files.
