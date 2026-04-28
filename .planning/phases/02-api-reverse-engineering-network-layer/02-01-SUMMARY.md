---
phase: 02-api-reverse-engineering-network-layer
plan: 01
subsystem: infra
tags: [gradle, version-catalog, ktor, room, ksp, kmp, har, sanitization, ci, python, bash]

# Dependency graph
requires:
  - phase: 01-foundation-compliance-infrastructure
    provides: build-logic convention plugins (lintech-kmp, lintech-test); :core:platform module; gradle/libs.versions.toml baseline; .github/workflows/ci.yml; expect/actual pattern; Linux Mint dev-host workflow
provides:
  - "gradle/libs.versions.toml extended with Ktor 3.4.3 + Room 2.8.4 + sqlite 2.6.2 + bumped kotlinx-serialization 1.9.0 + kotlinx-datetime 0.8.0-rc01"
  - "androidxRoom plugin alias registered (id=androidx.room, version.ref=room)"
  - ":core:database module skeleton (lintech-kmp + lintech-test + ksp + androidxRoom; per-target KSP for Android/iosX64/iosArm64/iosSimulatorArm64; schemaDirectory configured; allowBackup=false manifest fragment)"
  - ":core:api-avers-v4 module skeleton (lintech-kmp + lintech-test only; depends on :core:network; ktor commonMain + ktor-mock commonTest)"
  - "tools/sanitize-har.py — Python 3 PII sanitizer (Russian fullnames + initials → deterministic fakes via sha256 → NAME_POOL; cookie value redaction via configurable YAML rules; photo URL → placeholder)"
  - "tools/sanitize-rules.yaml — initial cookie list (extended in Plan 02 capture)"
  - "fixtures/{raw,sanitized}/ structure (raw/ gitignored; sanitized/ committed via .gitkeep)"
  - "tests/sanitize-har-canary.sh — CI lint preventing real PII leak in fixtures/sanitized/"
  - "tests/log-redactor-canary.sh — CI lint asserting kanareyka_PASSWORD_DO_NOT_LEAK_42 not in :core:network test stdout (no-op until Plan 04 wires HttpRequestRedactor)"
  - "ROADMAP.md §Phase 2 success #1 corrected — Chrome DevTools (D-02) replacing mitmproxy"
  - ".env.local + fixtures/raw/ added to .gitignore (D-04 storage layout)"
affects:
  - "02-02 (HAR capture) — uses tools/sanitize-har.py + fixtures/raw/ + fixtures/sanitized/ + tools/README.md workflow"
  - "02-03 (Room schema fill) — fills :core:database with @Database/CookieEntity/CookieDao + DatabaseFactory expect/actual + JournalDatabaseConstructor"
  - "02-04 (Ktor wiring + redactor + canary) — fills :core:network using ktor-* aliases; wires HttpRequestRedactor; integrates tests/log-redactor-canary.sh"
  - "02-05/06/07/08 (kill-switch, AVERS API contract, anti-bot, manual smoke) — depend on registered :core:api-avers-v4 module + ktor-client-mock + sanitized fixtures"
  - "Phase 3 (Auth) — KVault + AccountDataPurger + CredentialProvider built on this foundation"

# Tech tracking
tech-stack:
  added:
    - "Ktor 3.4.3 (client-core, content-negotiation, kotlinx-json, logging, darwin, okhttp, mock)"
    - "AndroidX Room 2.8.4 (room-runtime, room-compiler) + sqlite-bundled 2.6.2"
    - "kotlinx-serialization 1.9.0 (bumped from 1.7.3)"
    - "kotlinx-datetime 0.8.0-rc01 (bumped from 0.6.2)"
    - "androidxRoom Gradle plugin"
    - "PyYAML 6+ (system pip install --user) — for tools/sanitize-har.py"
  patterns:
    - "Per-target KSP wiring for Room KMP (kspAndroid + kspIosArm64 + kspIosX64 + kspIosSimulatorArm64) — required for Room KMP per developer.android.com/kotlin/multiplatform/room"
    - "Android manifest fragment per non-app module (allowBackup=false in :core:database/src/androidMain/AndroidManifest.xml; folded by manifest merger into final composeApp manifest)"
    - "Two-tier API module split — :core:network = AVERS-agnostic Ktor infrastructure; :core:api-avers-v4 = versioned API contract depending on :core:network (D-07)"
    - "Configurable PII sanitizer pattern — Python script + YAML rules file (tools/sanitize-rules.yaml) so cookie names extend without code changes"
    - "Deterministic fake mapping via sha256 → NAME_POOL[] — same real name yields same fake across runs, preserving cross-fixture references in sanitized HAR"
    - "Two-class CI canary scripts — content scan (sanitize-har-canary.sh) + log scan (log-redactor-canary.sh) — both no-op when scoped data not yet present, fail-loud once data arrives"

key-files:
  created:
    - "core/database/build.gradle.kts"
    - "core/database/src/androidMain/AndroidManifest.xml"
    - "core/api-avers-v4/build.gradle.kts"
    - "tools/sanitize-har.py"
    - "tools/sanitize-rules.yaml"
    - "tools/README.md"
    - "fixtures/sanitized/.gitkeep"
    - "fixtures/README.md"
    - "tests/sanitize-har-canary.sh"
    - "tests/log-redactor-canary.sh"
  modified:
    - "gradle/libs.versions.toml — bumped 2 versions, added 3 versions, added 9 library aliases, added 1 plugin alias"
    - "settings.gradle.kts — registered :core:database + :core:api-avers-v4"
    - ".gitignore — fixtures/raw/ + .env.local"
    - ".planning/ROADMAP.md — §Phase 2 success #1 mitmproxy → Chrome DevTools (D-02 correction)"

key-decisions:
  - "Per-target KSP wiring chosen over single ksp() call — Room KMP requires explicit per-target codegen (Pitfall #3); aligned with developer.android.com sample"
  - "Omitted androidx-room-sqlite-wrapper from :core:database/build.gradle.kts — RESEARCH §Standard Stack notes it as 'only needed if Android target uses different SQLite resolution'; standard BundledSQLiteDriver path doesn't need it. Plan 03 will re-add if Robolectric tests fail."
  - "fixtures/sanitized/.gitkeep committed; fixtures/raw/.gitkeep skipped (raw/ is gitignored — .gitkeep would never be committed)"
  - "log-redactor-canary.sh designed as no-op when :core:network has no tests yet (Plan 04 wires the parameterized HttpRequestRedactorTest); allows wiring into CI before content arrives, fail-loud once test class lands"
  - "PyYAML imported with explicit ImportError handler — script prints actionable install command (pip3 install --user pyyaml) instead of cryptic stack trace"

patterns-established:
  - "Catalog-driven multi-module versioning — libs.versions.toml is single source of truth; modules reference via alias(libs.plugins.*) and libs.* lib-aliases (no version literals in build.gradle.kts)"
  - "Per-account architecture invariant prep — :core:database namespace and module isolation; AndroidManifest allowBackup=false at the data-module level (D-21)"
  - "PII redaction pipeline — Python sanitizer + YAML config + canary script + gitignored raw/ + committed sanitized/ — three layers of defense for HAR fixtures"

requirements-completed: [SUCCESS-1, SUCCESS-5, D-02, D-03, D-04, D-07, D-15, D-21]

# Metrics
duration: ~70min
completed: 2026-04-28
---

# Phase 02 Plan 01: Bootstrap Phase 2 Infrastructure Summary

**Version catalog bumped (Ktor 3.4.3 + Room 2.8.4 + kotlinx-serialization 1.9.0 + kotlinx-datetime 0.8.0-rc01); two new modules (`:core:database` + `:core:api-avers-v4`) registered with KSP-aware Room skeleton + AVERS-agnostic Ktor consumer skeleton; HAR sanitization pipeline (Python + YAML rules + Chrome DevTools workflow doc) plus two CI canary scripts staged for Plans 02–04; ROADMAP §Phase 2 success #1 corrected from mitmproxy to Chrome DevTools per D-02.**

## Performance

- **Duration:** ~70 min (start ~21:10 UTC, end 19:22 UTC reflects local timezone — see commit timestamps)
- **Started:** 2026-04-28T19:10:00Z (approx)
- **Completed:** 2026-04-28T19:22:05Z
- **Tasks:** 4 / 4
- **Files modified:** 14 (4 modified, 10 created)
- **Plan-level commits:** 4 (one per task)

## Accomplishments

- **Catalog foundation for Phase 2:** Ktor 3.4.3 client + 4 plugins + 2 platform engines + mock; Room 2.8.4 KMP runtime + compiler + sqlite-bundled 2.6.2; bumped serialization/datetime to versions required by Ktor 3.4 + Instant serialization. All version-pinned via gradle/libs.versions.toml — no version literals leaked into module build.gradle.kts.
- **Two new buildable modules:** `:core:database` (Room KMP infra with per-target KSP, schemaDirectory, allowBackup=false manifest) and `:core:api-avers-v4` (versioned API contract depending on `:core:network`). Both compile cleanly via `./gradlew :core:database:assemble :core:api-avers-v4:assemble` (BUILD SUCCESSFUL).
- **PII sanitization pipeline:** Python 3 sanitizer with regex coverage for Russian fullnames + initials, deterministic sha256-keyed fake mapping (preserves cross-fixture references), configurable cookie redaction via YAML rules. Self-tested on synthetic HAR — full name → `Кузнецов К.К.`, cookie → 32 X's, photo URL → placeholder.
- **Two CI canary scripts staged:** `sanitize-har-canary.sh` (greps committed fixtures/sanitized/ for non-approved Russian names) and `log-redactor-canary.sh` (greps :core:network test stdout for `kanareyka_PASSWORD_DO_NOT_LEAK_42`). Both no-op gracefully when their data is not yet present — wire into CI before Plans 02/04 land content.
- **ROADMAP correction:** §Phase 2 success criterion #1 changed from "захваченные через mitmproxy" to "захваченные через **Chrome DevTools**" with inline D-02 reasoning (Linux Mint dev-host, mobile UA divergence mitigation).

## Task Commits

Each task was committed atomically (worktree mode — `--no-verify`):

1. **Task 1: Bump version catalog + register modules + extend gitignore** — `bee238d` (build)
2. **Task 2: Create :core:database + :core:api-avers-v4 module skeletons** — `0dfa446` (build)
3. **Task 3: Create HAR sanitization tooling + fixtures structure + CI canaries** — `a04baec` (feat)
4. **Task 4: Edit ROADMAP.md (mitmproxy → Chrome DevTools)** — `fd7d910` (docs)

_Plan metadata commit (this SUMMARY.md) follows after self-check._

## Files Created/Modified

### Created (10)

- `core/database/build.gradle.kts` — Room KMP module with per-target KSP, schemaDirectory, dependency on `:core:platform`
- `core/database/src/androidMain/AndroidManifest.xml` — D-21 `allowBackup="false"` + `tools:replace` for manifest merger
- `core/api-avers-v4/build.gradle.kts` — Ktor consumer module depending on `:core:network`, with ktor-client-mock in commonTest
- `tools/sanitize-har.py` — Python 3 PII sanitizer (chmod +x), 87 lines
- `tools/sanitize-rules.yaml` — initial 7 cookie name patterns (extended in Plan 02)
- `tools/README.md` — Chrome DevTools capture workflow + .env.local dotenv format + D-04 storage layout
- `fixtures/sanitized/.gitkeep` — preserves directory in fresh clones (raw/ gitignored)
- `fixtures/README.md` — layout + CI guarantee documentation
- `tests/sanitize-har-canary.sh` (chmod +x) — CI lint for fixtures/sanitized/ (set -euo pipefail; multi-step echo pattern)
- `tests/log-redactor-canary.sh` (chmod +x) — CI lint for log redactor (no-op until Plan 04)

### Modified (4)

- `gradle/libs.versions.toml` — 5 versions bumped/added; 9 new library aliases (ktor-* x4 + ktor engines x2 + ktor-mock + room-runtime + room-compiler + sqlite-bundled); 1 new plugin alias (androidxRoom)
- `settings.gradle.kts` — added `include(":core:database")` and `include(":core:api-avers-v4")`
- `.gitignore` — added `fixtures/raw/` and `.env.local`
- `.planning/ROADMAP.md` — Phase 2 success criterion #1 line edited with D-02 correction

## Decisions Made

1. **Per-target KSP** (kspAndroid + kspIosArm64 + kspIosX64 + kspIosSimulatorArm64) chosen for `:core:database` over single `ksp(...)` — required by Room KMP per official Android docs, prevents Pitfall #3.
2. **Omitted `androidx-room-sqlite-wrapper`** from :core:database deps — RESEARCH §Standard Stack notes it as conditionally needed; the standard BundledSQLiteDriver path doesn't need it. Plan 03 will revisit if Robolectric tests fail.
3. **`fixtures/raw/.gitkeep` skipped** — `raw/` is gitignored; a placeholder there would never be committed. Documented in `fixtures/README.md` instead.
4. **`log-redactor-canary.sh` defensive no-op** — when `:core:network` has no tests yet (Plans 02/04 not run), the script detects "No tests found" / "is not a test project" and exits 0 with a note. Allows wiring the script into CI now without breaking the pipeline; fail-loud once Plan 04 wires the canary test.
5. **Plain `python3` (system) over venv** for `tools/sanitize-har.py` — researcher discretion (D-19); A-D-19 picks system Python since dev workflow is Linux Mint local; pip3 install --user pyyaml is a one-time setup.

## Deviations from Plan

None — plan executed exactly as written.

All 4 tasks ran end-to-end without auto-fix interventions. The plan's `<read_first>` blocks gave precise content snippets, and the `<verify>` blocks were used as written. The only minor textual nuance: the plan's Step 3.4 noted "since fixtures/raw is in .gitignore, the .gitkeep won't be committed. Skip the .gitkeep for raw/ and instead document in README" — this guidance was followed (no `fixtures/raw/.gitkeep` was created; documentation went into `fixtures/README.md`).

## Issues Encountered

1. **Initial worktree base mismatch** — at agent startup, `git merge-base HEAD <expected>` returned a different commit (`4ca50fb` vs expected `fafbf04`); HEAD was several commits behind the expected base. Resolved per `<worktree_branch_check>` instruction by `git reset --hard fafbf04f81e85afd6621dbd748beeb33531f5e49` and re-verifying. Root cause: parallel branch checkout artifact, not a plan issue.
2. **Verify chain `! grep` exit code quirk** — first attempt at chained negative grep verification gave a non-zero exit due to set-style command sequencing. Re-ran step-by-step and all individual checks passed. No code change required.

Both incidents resolved without any code/file change.

## Wave 1 Status

This is the foundation half of Phase 2 Wave 1. Plan 02 (HAR captures, autonomous=false — manual developer workflow) is the other Wave 1 plan; it depends on this plan's `tools/sanitize-har.py` + `fixtures/raw/` + `fixtures/sanitized/` + `tools/README.md`.

## Next Phase Readiness

**Ready for downstream Phase 2 plans:**

- **Plan 02 (HAR capture):** `tools/sanitize-har.py` + `tools/sanitize-rules.yaml` + `tools/README.md` workflow ready; `fixtures/raw/` + `fixtures/sanitized/` directories present; `.env.local` gitignored. Developer can begin Chrome DevTools capture.
- **Plan 03 (Room schema fill):** `:core:database/build.gradle.kts` + per-target KSP wired; ready for `JournalDatabase` + `CookieEntity` + `CookieDao` + `DatabaseFactory` expect/actual + `JournalDatabaseConstructor` to land.
- **Plan 04 (Ktor wiring + redactor + canary):** `:core:network/build.gradle.kts` does not yet consume the new ktor-* aliases — Plan 04 will edit it. `tests/log-redactor-canary.sh` is staged and ready to be invoked from CI once `HttpRequestRedactor` + parameterized canary test land.
- **Plans 05/06/07/08:** `:core:api-avers-v4` module exists with ktor-client-mock in commonTest; ready for `AversApi`, `AversApiError`, `KillSwitchClient`, `AntiBotDetector`, `HarReplayMockEngine`.

**No blockers.** ROADMAP correction propagates downstream — verifier-phase will use the corrected criterion to validate Phase 2 closure.

## Threat Flags

None — no new security-relevant surface introduced beyond what the plan's `<threat_model>` already enumerated. The Python sanitizer + YAML rules + canary scripts are all defensive (T-02-01 through T-02-04 mitigations); registering empty Gradle modules introduces no runtime surface.

## Self-Check: PASSED

All 11 created files exist on disk; all 4 modified files exist; all 4 task commits (bee238d, 0dfa446, a04baec, fd7d910) are present in `git log`. Module skeletons compile (`./gradlew :core:database:assemble :core:api-avers-v4:assemble` returned `BUILD SUCCESSFUL` during Task 2 verification). Python sanitizer self-tested on synthetic HAR (full name → fake, cookie → XXX, photo → placeholder). Both canary scripts pass `bash -n` syntax check; sanitize-har-canary.sh exits 0 on empty fixtures.

---
*Phase: 02-api-reverse-engineering-network-layer*
*Completed: 2026-04-28*
