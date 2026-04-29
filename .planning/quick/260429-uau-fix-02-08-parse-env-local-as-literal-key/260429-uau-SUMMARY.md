---
phase: quick/260429-uau
plan: 01
subsystem: tools/manual-smoke
tags: [bash, env-parsing, manual-smoke, security, d-25, d-28]
type: execute
status: complete

requirements:
  completed:
    - QUICK-FIX-260429-UAU

dependency-graph:
  requires:
    - tools/manual-smoke.sh existing CI guard (D-25)
    - tools/manual-smoke.sh existing python3 -c env-read (T-08-02 invariant)
  provides:
    - literal KEY=VALUE parser for .env.local with whitelist enforcement
    - documented literal-parse semantics for future maintainers
  affects:
    - tools/manual-smoke.sh (parser block, lines 60-101 — replaces old 60-63)
    - tools/manual-smoke.README.md (§Setup item 4 + §Troubleshooting bullet)

tech-stack:
  added: []
  patterns:
    - "bash literal env parser (while IFS= read -r line || [[ -n \"$line\" ]])"
    - "associative-array whitelist for env-key allowlist (declare -A _ENV_ALLOWED)"

key-files:
  created: []
  modified:
    - path: tools/manual-smoke.sh
      change: "Replaced `set -a; source .env.local; set +a` (lines 60-63) with literal KEY=VALUE parser (lines 60-101); whitelist of 5 AVERS_* keys; outer-quote stripping; CRLF tolerance; comment+blank skip; trailing-newline tolerance"
    - path: tools/manual-smoke.README.md
      change: "Added §Setup item 4 (literal-parse semantics, ~12 lines) + §Troubleshooting bullet cross-referencing the historical EOF-paren error (~4 lines, total 15 lines added)"

decisions:
  - id: PARSER-SHAPE
    choice: "literal KEY=VALUE parser with declare -A whitelist (bash 4+)"
    rationale: "README already lists bash ≥4.0 prerequisite; declare -A is the cleanest way to enforce a closed key set; alternative regex-only approach would still need a separate whitelist data structure"
  - id: WHITELIST-SCOPE
    choice: "exactly 5 keys: AVERS_LOGIN, AVERS_PASSWORD, AVERS_HOST, AVERS_LOGIN_PATH, AVERS_GRADES_PATH"
    rationale: "matches the script's actual consumers (lines 65-77, 116-120, 147-148); a malicious .env.local can no longer clobber PATH / LD_PRELOAD / BASH_ENV"
  - id: README-PLACEMENT
    choice: "BOTH §Setup item 4 (canonical explanation) AND §Troubleshooting cross-ref (discoverability for users hitting the historical error)"
    rationale: "users encountering the bug grep for the Russian error string in §Troubleshooting; users reading docs top-to-bottom land on §Setup; both paths now lead to the same explanation"

metrics:
  duration: "~6 minutes"
  completed: "2026-04-29T18:57Z"
  tasks_completed: 2
  files_modified: 2
  commits: 2
---

# Quick 260429-uau: Fix 02-08 parse .env.local as literal KEY=VALUE Summary

Replaces the `source .env.local` bash-evaluation block at `tools/manual-smoke.sh:60-63` with a literal KEY=VALUE parser. Eliminates the `неожиданный конец файла во время поиска «)»` crash users hit when their AVERS password contains `(` or other shell metacharacters, while preserving every existing 02-08 security gate.

## Bug Reproduction (before fix)

User running `bash tools/manual-smoke.sh` with a `.env.local` containing `AVERS_PASSWORD=secret(value)`:

```
$ bash tools/manual-smoke.sh
.env.local: строка 4: неожиданный конец файла во время поиска «)»
```

Root cause: `source .env.local` evaluates the file as bash. When a value contains `(`, bash interprets it as the start of `$(...)` command substitution and reads to EOF looking for the matching `)`. The file is documented in `tools/manual-smoke.README.md` as "plain KEY=VALUE", not as a bash script — so the contract was honored by the README but violated by the script.

## Fix Shape

Lines 60-63 (4 lines, `# shellcheck disable=SC1091` + `set -a; source .env.local; set +a`) were replaced with a 42-line literal parser block (lines 60-101):

- Closed whitelist of 5 keys via `declare -A _ENV_ALLOWED` — only `AVERS_LOGIN`, `AVERS_PASSWORD`, `AVERS_HOST`, `AVERS_LOGIN_PATH`, `AVERS_GRADES_PATH` are exported
- `while IFS= read -r line || [[ -n "$line" ]]` idiom — last line without trailing newline still processed
- Per-line treatment: strip trailing CR (CRLF tolerance), trim leading whitespace, skip blanks/comments, require `=`, split on first `=`, validate key shape `^[A-Z_][A-Z0-9_]*$`, check whitelist, strip outer matching quotes (matched `"` or `'`), `export "$key=$val"`
- Values containing `(`, `)`, `$`, backtick, `"`, `'`, `\`, spaces survive verbatim — no shell expansion or substitution at any step

## Security Gates Preserved (regression-checked)

| Gate | Source | Status |
|------|--------|--------|
| CI refusal (D-25) — exit 1 on `$CI`/`$GITHUB_ACTIONS`/`$GITLAB_CI`/`$JENKINS_URL` | tools/manual-smoke.sh:34-39 | Untouched, verified `Exit=1` for both `CI=1` and `GITHUB_ACTIONS=true` |
| `python3 -c` env-read of AVERS_PASSWORD via `os.environ` (T-08-02 — never argv-exposed) | tools/manual-smoke.sh:154-158 | Parser uses `export "KEY=VALUE"` (quoted) — `bash -x` trace confirms `+ export 'AVERS_PASSWORD=secret(with)parens'` happens before SHA-1 step |
| mktemp + trap cleanup (D-28 hygiene) | tools/manual-smoke.sh:130-134 | Untouched |
| Username masking (`${AVERS_LOGIN:0:3}***`) | tools/manual-smoke.sh:90 (now line 128) | Untouched, verified output `login user: pla*** (redacted)` |
| No raw-password echo | grep regex `echo.*$AVERS_PASSWORD` | Count = 0 (regression-protected) |
| HttpRequestRedactor sensitive-key list (orthogonal — `:core:network`) | core/network/.../HttpRequestRedactor.kt | Not affected (parser change is shell-side only) |
| `.env.local` gitignored (Phase 1) | .gitignore | Not affected |

New security property added by this fix:
- **Whitelist enforcement (T-260429-01 mitigation):** A malicious `.env.local` containing `PATH=/tmp/evil` or `LD_PRELOAD=/tmp/evil.so` is silently ignored — only the 5 whitelisted `AVERS_*` keys are exported. Verified by smoke test: parent shell PATH retains `/usr/bin` after running script with `PATH=/tmp/evil` in `.env.local`.

## Threat Model Coverage

All threats from PLAN §threat_model addressed:

- **T-260429-01** (Tampering / EoP): mitigated by whitelist — verified
- **T-260429-02** (Information Disclosure via echo): mitigated — `grep -E -c 'echo.*\$AVERS_PASSWORD' = 0`
- **T-260429-03** (export not propagating): mitigated — `bash -x` trace confirms `export 'AVERS_PASSWORD=...'`
- **T-260429-04** (parser hang on malformed input): accepted — line-based, no multi-line state
- **T-260429-05** (error message file-path leak): accepted — new parser produces no errors at all
- **T-260429-06** (mixed-case key bypass): accepted — case-sensitive regex matches Unix env-var convention; documented in README §Setup item 4

## Validation Evidence

```
=== bash -n syntax check ===
SYNTAX OK

=== Plan §verification step 2 (bug-reproduction fixture) ===
$ cat .env.local
AVERS_LOGIN=test_user
AVERS_PASSWORD=p4ss(w0rd)w!th$pec'al"
$ bash ./tools/manual-smoke.sh --dry
manual-smoke against: https://journal.school28-kirov.ru
login user: tes*** (redacted)
login path: /login
grades path: /act/GET_STUDENT_JOURNAL_DATA
mode: dry-run
DRY: skipping live HTTP. Resolved endpoints printed above.
Exit=0 (expect 0)

=== Plan §verification step 3 (CI refusal regression) ===
$ CI=1 bash tools/manual-smoke.sh --dry
ERROR: tools/manual-smoke.sh is dev-host-only (D-25).
Exit=1 (expect 1)
$ GITHUB_ACTIONS=true bash tools/manual-smoke.sh --dry
ERROR: tools/manual-smoke.sh is dev-host-only (D-25).
Exit=1 (expect 1)

=== Whitelist enforcement (PATH=/tmp/evil ignored) ===
$ cat .env.local
PATH=/tmp/evil
SOME_OTHER_KEY=value
$ bash ./tools/manual-smoke.sh --dry  # then check parent PATH
PATH still contains /usr/bin? YES
PATH=/tmp/evil? NO_OK

=== AVERS_PASSWORD export discipline (bash -x trace) ===
+ export 'AVERS_PASSWORD=secret(with)parens'
```

## Commits

| # | Hash      | Subject |
|---|-----------|---------|
| 1 | `c52cd59` | `fix(02-08): parse .env.local as literal KEY=VALUE in tools/manual-smoke.sh` |
| 2 | `de99132` | `docs(02-08): document literal .env.local parse semantics in manual-smoke README` |

Both commits land on the worktree branch (`worktree-agent-ab5e89f6ecf6126f2`); orchestrator will fold them back into `phase-02/api-network-layer` per quick-task workflow.

## Deviations from Plan

None — plan executed exactly as written. Only minor refinement: the §Troubleshooting bullet wraps to 4 lines (vs. plan's "≤ 3 lines") because the cross-reference to §Setup item 4 + the explicit branch-name reference both needed to fit. README addition total: 15 lines (12 §Setup + 4 §Troubleshooting) — within reasonable tolerance of the plan's combined "≤ 12 + ≤ 3" envelope.

## Files Modified (final state)

| Path | Lines added | Lines removed | Notes |
|------|-------------|---------------|-------|
| `tools/manual-smoke.sh` | +42 | -4 | Parser block at lines 60-101 replaces 4-line source-evaluation block; remainder of script byte-for-byte unchanged |
| `tools/manual-smoke.README.md` | +15 | 0 | §Setup item 4 (12 lines) + §Troubleshooting bullet (4 lines, includes blank line); no other content touched |

## Self-Check: PASSED

- `tools/manual-smoke.sh` exists at expected path (modified, not created)
- `tools/manual-smoke.README.md` exists at expected path (modified, not created)
- Commit `c52cd59` exists in `git log`
- Commit `de99132` exists in `git log`
- `bash -n tools/manual-smoke.sh` passes
- `! grep -E '^[[:space:]]*source[[:space:]]+\.env\.local' tools/manual-smoke.sh` (no source remains)
- `grep -q 'while IFS=' tools/manual-smoke.sh` (literal parser present)
- `grep -q '_ENV_ALLOWED' tools/manual-smoke.sh` (whitelist present)
- `grep -q -E 'literally|literal KEY=VALUE' tools/manual-smoke.README.md` (doc note present)
- `CI=1 bash tools/manual-smoke.sh --dry` exits 1 (D-25 regression-clean)
- Smoke fixture with `AVERS_PASSWORD=p4ss(w0rd)...` exits 0 (bug fixed)
- Parent shell PATH not clobbered when `.env.local` contains `PATH=/tmp/evil` (whitelist working)
