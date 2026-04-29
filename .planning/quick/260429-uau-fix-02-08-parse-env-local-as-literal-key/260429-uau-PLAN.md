---
phase: quick/260429-uau
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - tools/manual-smoke.sh
  - tools/manual-smoke.README.md
autonomous: true
requirements:
  - QUICK-FIX-260429-UAU
tags: [bash, env-parsing, manual-smoke, security, d-25, d-28]

must_haves:
  truths:
    - "Running `bash tools/manual-smoke.sh` with a `.env.local` whose value contains `(` or `)` no longer produces `неожиданный конец файла во время поиска «)»` (the bash-source EOF-paren error)"
    - "AVERS_LOGIN and AVERS_PASSWORD are read literally from `.env.local` — no shell expansion, no command substitution, no variable interpolation occurs while parsing"
    - "All existing security promises remain intact: CI refusal (D-25), `python3 -c` env-read of AVERS_PASSWORD (never argv-exposed), mktemp + trap cleanup, username masking (first 3 chars + `***`), HttpRequestRedactor sensitive-key list compatibility"
    - "`tools/manual-smoke.README.md` documents the literal-parse semantics so future maintainers know `.env.local` is NOT a bash script and special characters need no quoting"
  artifacts:
    - path: "tools/manual-smoke.sh"
      provides: "Literal KEY=VALUE parser replacing the `set -a; source .env.local; set +a` block at lines 60-63"
      contains: "while IFS= read -r line"
    - path: "tools/manual-smoke.README.md"
      provides: "Note explaining `.env.local` is parsed literally (no quoting needed for special chars)"
      contains: "literally"
  key_links:
    - from: "tools/manual-smoke.sh parser block (~lines 60-63)"
      to: "AVERS_LOGIN / AVERS_PASSWORD / AVERS_HOST / AVERS_LOGIN_PATH / AVERS_GRADES_PATH consumers (lines 65-77, 116-120, 147-148)"
      via: "shell variables exported to current process (so `python3 -c` env-read at line 116-120 still sees AVERS_PASSWORD via os.environ)"
      pattern: "export [A-Z_]+="
    - from: "tools/manual-smoke.README.md §Setup or §Troubleshooting"
      to: "the new parser semantics"
      via: "documentation note about literal parsing"
      pattern: "literally|без\\s+экранирования|no quoting"
---

<objective>
Fix the bash-source-as-script bug at `tools/manual-smoke.sh:60-63`: replace `set -a; source .env.local; set +a` with a literal KEY=VALUE parser that does NOT evaluate values as bash. The current code crashes when the user's password contains `(` because bash interprets it as the start of `$(...)` command substitution and reads to EOF looking for `)`. `.env.local` is documented as "plain KEY=VALUE", not as a bash script — so the script needs a parser that honors that contract.

Purpose: Restore the manual-smoke loop for users whose AVERS credentials contain shell metacharacters. The manual-smoke script is the developer's only live tripwire against `journal.school28-kirov.ru` (D-25 forbids live calls in CI), so any UX regression here blocks Phase 2 contract-drift detection. Quick fix on existing branch `phase-02/api-network-layer` — auto-updates open PR #2.

Output:
- `tools/manual-smoke.sh` — literal env parser replacing source-evaluation block; all security gates preserved
- `tools/manual-smoke.README.md` — short note explaining literal-parse semantics
</objective>

<execution_context>
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/workflows/execute-plan.md
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@tools/manual-smoke.sh
@tools/manual-smoke.README.md
@.planning/phases/02-api-reverse-engineering-network-layer/02-08-SUMMARY.md

<bug-summary>
User reports during HUMAN-UAT:
```
$ bash tools/manual-smoke.sh
.env.local: строка 4: неожиданный конец файла во время поиска «)»
```

Root cause: `tools/manual-smoke.sh` lines 60-63 currently do:
```bash
# shellcheck disable=SC1091
set -a
source .env.local
set +a
```

`source` evaluates the file as bash. If a value contains `(`, bash interprets it as the
start of `$(...)` command substitution and reads to EOF looking for `)`. The user's
AVERS password contains `(`. `.env.local` is documented in `tools/manual-smoke.README.md`
as "plain KEY=VALUE" — this is a real bug in the script, not bad user data.
</bug-summary>

<security-promises-must-preserve>
From `tools/manual-smoke.README.md` §Security & D-28 compliance and 02-08-SUMMARY §Manual-smoke security gates:

1. **CI refusal (D-25)** — first action after `set -euo pipefail`: refuses to run when
   `$CI` / `$GITHUB_ACTIONS` / `$GITLAB_CI` / `$JENKINS_URL` is set. Lines 31-39, NOT
   touched by this fix.
2. **Password env-read via `python3 -c`** — AVERS_PASSWORD MUST be readable from
   `os.environ` inside the python3 SHA-1 step at lines 116-120. The new parser MUST
   `export` the variable so the python3 child process sees it. This is the load-bearing
   security invariant — DO NOT regress it.
3. **mktemp + trap cleanup** — lines 130-134, NOT touched.
4. **Username masking** — `LOGIN_HEAD="${AVERS_LOGIN:0:3}***"` at line 90, NOT touched.
5. **HttpRequestRedactor sensitive-key list** — orthogonal (lives in `:core:network`),
   parser change does not affect it.
6. **Never echo raw password** — already enforced; new parser MUST NOT echo or log
   AVERS_PASSWORD content. Echoing parsed VALUE is forbidden.
7. **`.env.local` gitignored** — orthogonal (Phase 1 .gitignore), not affected.
</security-promises-must-preserve>

<parser-requirements>
The replacement parser at lines 60-63 MUST handle:

- **Shell metacharacters in values:** `(`, `)`, `$`, backtick, `"`, `'`, `\`, spaces — values are taken literally, no expansion or substitution
- **Outer matching quotes stripped:** if value starts with `'` and ends with `'`, strip both. Same for `"`. If quotes don't match (e.g. starts with `'`, ends with `"`), keep both characters as part of the value.
- **CRLF tolerance:** strip trailing `\r` (handles files saved on Windows)
- **Comment lines:** lines starting with `#` (after optional leading whitespace) are skipped
- **Blank lines:** skipped
- **Lines without `=`:** skipped silently (no error, just ignore)
- **Files without trailing newline:** the `while IFS= read -r line || [[ -n "$line" ]]; do ... done` idiom must be used so the last line is processed even if no `\n` follows
- **Leading whitespace on KEY:** trim leading spaces/tabs from the key (e.g. `   AVERS_LOGIN=foo` → key is `AVERS_LOGIN`)
- **First `=` is the separator:** value can contain `=` characters (e.g. `AVERS_PASSWORD=abc=def` → value is `abc=def`)
- **Export to environment:** parsed KEY=VALUE pairs must be `export`ed so child processes (the `python3 -c` SHA-1 step) see them via `os.environ`
- **Whitelist-only key names:** to prevent malicious `.env.local` from clobbering `PATH` or `LD_PRELOAD`, only export keys matching `^[A-Z_][A-Z0-9_]*$` AND in the script's known whitelist (AVERS_LOGIN, AVERS_PASSWORD, AVERS_HOST, AVERS_LOGIN_PATH, AVERS_GRADES_PATH). Unknown keys are skipped silently.
</parser-requirements>

<reference-implementation-shape>
Suggested shape (Claude is free to refine — this is a sketch, not a mandate):

```bash
# ---------------------------------------------------------------------------
# Parse .env.local as literal KEY=VALUE (NOT bash-evaluated).
# Why not `source`: values may contain shell metacharacters like `(`, `$`,
# backtick — bash would interpret them. .env.local is plain KEY=VALUE per
# tools/manual-smoke.README.md §Setup. See HUMAN-UAT bug 260429-uau.
# ---------------------------------------------------------------------------
declare -A _ENV_ALLOWED=(
  [AVERS_LOGIN]=1
  [AVERS_PASSWORD]=1
  [AVERS_HOST]=1
  [AVERS_LOGIN_PATH]=1
  [AVERS_GRADES_PATH]=1
)

while IFS= read -r line || [[ -n "$line" ]]; do
  # Strip trailing CR (CRLF tolerance)
  line="${line%$'\r'}"
  # Trim leading whitespace
  line="${line#"${line%%[![:space:]]*}"}"
  # Skip blanks and comments
  [[ -z "$line" || "$line" == \#* ]] && continue
  # Require an `=` somewhere
  [[ "$line" == *=* ]] || continue
  # Split on FIRST `=`
  key="${line%%=*}"
  val="${line#*=}"
  # Trim trailing whitespace from key (rare but possible: `KEY = value`)
  key="${key%"${key##*[![:space:]]}"}"
  # Validate key: env-var shape AND in whitelist
  [[ "$key" =~ ^[A-Z_][A-Z0-9_]*$ ]] || continue
  [[ -n "${_ENV_ALLOWED[$key]:-}" ]] || continue
  # Strip outer matching quotes (single or double)
  if [[ "${#val}" -ge 2 ]]; then
    first="${val:0:1}"
    last="${val: -1}"
    if [[ ( "$first" == '"' && "$last" == '"' ) || ( "$first" == "'" && "$last" == "'" ) ]]; then
      val="${val:1:${#val}-2}"
    fi
  fi
  export "$key=$val"
done < .env.local
unset _ENV_ALLOWED
```

Note: bash `declare -A` requires bash 4+. The README already lists `bash (≥4.0)` as a
prerequisite — no regression. The `${val: -1}` syntax (note the space) is bash-portable
back to 4.x.
</reference-implementation-shape>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Replace `source .env.local` with literal KEY=VALUE parser in tools/manual-smoke.sh</name>
  <files>tools/manual-smoke.sh</files>
  <action>
Replace lines 60-63 of `tools/manual-smoke.sh` (the current `set -a; source .env.local; set +a` block) with a literal KEY=VALUE parser that does NOT evaluate `.env.local` as bash. Use the `<reference-implementation-shape>` above as a starting point — refine as needed but preserve every requirement from `<parser-requirements>`.

Implementation rules:

1. **Keep `set -euo pipefail`** at line 29 — do not weaken error handling. The parser must work under `nounset` (`set -u`); guard reads of optional vars with `${VAR:-}`.

2. **Preserve the existing comment block** at lines 48-50 (`# Load .env.local`); add a sub-block comment explaining WHY we don't `source` (1-3 lines max — the reason is "values may contain shell metacharacters; .env.local is plain KEY=VALUE per README §Setup; see HUMAN-UAT bug 260429-uau"). DO NOT remove the existing `if [[ ! -f .env.local ]]` guard at lines 51-58 — the new parser still needs that file existence check before reading.

3. **Whitelist enforcement:** only `AVERS_LOGIN`, `AVERS_PASSWORD`, `AVERS_HOST`, `AVERS_LOGIN_PATH`, `AVERS_GRADES_PATH` are recognized. Unknown keys are silently skipped (security: a malicious `.env.local` cannot clobber `PATH`, `LD_PRELOAD`, `BASH_ENV`, etc.). Use `declare -A` associative array (bash 4+ already required per README §Prerequisites).

4. **Export discipline:** parsed values must be `export`ed (so the `python3 -c` SHA-1 step at lines 116-120 sees `AVERS_PASSWORD` via `os.environ.get("AVERS_PASSWORD")`). Use `export "KEY=VALUE"` form (quoted) so values containing spaces or special characters survive.

5. **Outer quote stripping:** if a value starts AND ends with the same quote character (`'` or `"`), strip both. Mixed quotes (`"foo'`) are kept literal. Single-character values like `'` (just one quote) are kept literal (length check: `${#val} -ge 2`).

6. **No echo of values:** under no circumstance echo, log, or print parsed values. Echoing the parsed AVERS_PASSWORD would regress the D-28 hygiene gate. Pre-flight summary at lines 88-95 already prints only masked / boolean derivatives — leave that block untouched.

7. **Use a temporary associative array variable name with leading underscore** (e.g. `_ENV_ALLOWED`) and `unset` it after the loop to avoid polluting the script's later scope.

8. **Idempotence under shellcheck:** remove the now-obsolete `# shellcheck disable=SC1091` directive (it was disabling source-file-not-found warnings; without `source` it's no longer needed). If shellcheck warns on the new code, address the warnings inline rather than disabling them.

9. **Do NOT touch any other lines in the file.** Specifically, lines 1-59 (header comments, CI guard, repo-root resolve, file-existence check) and lines 65 onward (env-var presence check, endpoint defaults, mode detection, pre-flight, tool checks, SHA-1 step, mktemp/trap, login flow, grades flow) MUST remain byte-for-byte unchanged. Use the Edit tool with the exact `set -a\nsource .env.local\nset +a` block as the old_string.

Manual smoke verification (after making changes):

```bash
# (a) Syntax check
bash -n tools/manual-smoke.sh

# (b) Round-trip a value with shell metacharacters through a transient .env.local
TMPDIR_T=$(mktemp -d)
cat > "$TMPDIR_T/.env.local" <<'ENVFILE'
# comment line — should be skipped
AVERS_LOGIN=plain_login
AVERS_PASSWORD=pass(word)$with"meta`and'spaces and \backslash
AVERS_HOST=https://journal.school28-kirov.ru
# blank below — should be skipped

# malicious key — should be ignored
PATH=/tmp/evil
# unknown but valid-shape key — should be ignored
SOME_OTHER_KEY=value
ENVFILE

# Run dry-run from the temp dir (script auto-cd's to repo root, so we need to test the parser in isolation):
# Extract just the parser block + the variable-presence check into a sub-shell
( cd "$TMPDIR_T" && cp "$OLDPWD/tools/manual-smoke.sh" ./smoke.sh && \
  CI= GITHUB_ACTIONS= GITLAB_CI= JENKINS_URL= bash ./smoke.sh --dry 2>&1 | head -20 )

# Expected:
#   manual-smoke against: https://journal.school28-kirov.ru
#   login user: pla*** (redacted)
#   ...
#   mode: dry-run
#   DRY: skipping live HTTP. Resolved endpoints printed above.
# (no "неожиданный конец файла" error)

# Confirm `PATH` was NOT clobbered to `/tmp/evil`:
( cd "$TMPDIR_T" && cp "$OLDPWD/tools/manual-smoke.sh" ./smoke.sh && \
  CI= GITHUB_ACTIONS= GITLAB_CI= JENKINS_URL= bash ./smoke.sh --dry >/dev/null 2>&1 ; \
  echo "PATH unchanged: $([ "$PATH" != "/tmp/evil" ] && echo YES || echo NO)" )

# Cleanup
rm -rf "$TMPDIR_T"

# (c) CI refusal still works (D-25 regression check)
CI=1 bash tools/manual-smoke.sh --dry; echo "Exit=$? (expect 1)"

# (d) Confirm AVERS_PASSWORD is exported (visible to python3 child)
TMPDIR_T=$(mktemp -d)
cat > "$TMPDIR_T/.env.local" <<'ENVFILE'
AVERS_LOGIN=test_login
AVERS_PASSWORD=secret(with)parens
ENVFILE
( cd "$TMPDIR_T" && cp "$OLDPWD/tools/manual-smoke.sh" ./smoke.sh && \
  CI= GITHUB_ACTIONS= GITLAB_CI= JENKINS_URL= bash -c '
    # Run only the parser portion by short-circuiting before the SHA-1 step
    # Simpler: rely on dry-run path (which exits before SHA-1) — confirm via
    # bash -x trace that AVERS_PASSWORD was exported
    bash -x ./smoke.sh --dry 2>&1 | grep -E "export AVERS_PASSWORD|AVERS_PASSWORD=" | grep -v ".env.local" | head -5
  ' )
rm -rf "$TMPDIR_T"

# (e) shellcheck (best-effort — install if not present)
if command -v shellcheck >/dev/null 2>&1; then
  shellcheck tools/manual-smoke.sh && echo "shellcheck PASS"
fi
```

Commit message after the script is patched:
```
fix(02-08): parse .env.local as literal KEY=VALUE in tools/manual-smoke.sh

Replace `set -a; source .env.local; set +a` with a literal parser that
does not evaluate `.env.local` as bash. Values containing shell
metacharacters (e.g. `(` in passwords) no longer trigger
"неожиданный конец файла во время поиска «)»".

Key-name whitelist (AVERS_LOGIN/AVERS_PASSWORD/AVERS_HOST/
AVERS_LOGIN_PATH/AVERS_GRADES_PATH) prevents malicious .env.local
from clobbering PATH, LD_PRELOAD, etc. Outer matching quotes are
stripped; CRLF tolerated; comments and blank lines skipped; last
line without trailing \n still processed.

All existing security gates preserved: CI refusal (D-25),
python3 -c env-read of AVERS_PASSWORD (still exported, never argv-
exposed), mktemp + trap cleanup, username masking, no value echo.

Quick: 260429-uau
```

Stage and commit (per CLAUDE.md commit hygiene — `--no-verify` is the worktree-mode default for this repo):

```bash
git add tools/manual-smoke.sh
git commit --no-verify -m "$(cat <<'EOF'
fix(02-08): parse .env.local as literal KEY=VALUE in tools/manual-smoke.sh

Replace `set -a; source .env.local; set +a` with a literal parser that
does not evaluate `.env.local` as bash. Values containing shell
metacharacters (e.g. `(` in passwords) no longer trigger
"неожиданный конец файла во время поиска «)»".

Key-name whitelist (AVERS_LOGIN/AVERS_PASSWORD/AVERS_HOST/
AVERS_LOGIN_PATH/AVERS_GRADES_PATH) prevents malicious .env.local
from clobbering PATH, LD_PRELOAD, etc. Outer matching quotes are
stripped; CRLF tolerated; comments and blank lines skipped; last
line without trailing \n still processed.

All existing security gates preserved: CI refusal (D-25),
python3 -c env-read of AVERS_PASSWORD (still exported, never argv-
exposed), mktemp + trap cleanup, username masking, no value echo.

Quick: 260429-uau

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```
  </action>
  <verify>
    <automated>
bash -n tools/manual-smoke.sh && \
! grep -E '^[[:space:]]*source[[:space:]]+\.env\.local' tools/manual-smoke.sh && \
! grep -E '^[[:space:]]*set -a[[:space:]]*$' tools/manual-smoke.sh && \
grep -q 'while IFS=' tools/manual-smoke.sh && \
grep -q '_ENV_ALLOWED' tools/manual-smoke.sh && \
grep -q 'AVERS_PASSWORD' tools/manual-smoke.sh && \
CI=1 bash tools/manual-smoke.sh --dry; [ $? -eq 1 ] && echo "VERIFY PASS"
    </automated>
  </verify>
  <done>
- `tools/manual-smoke.sh` no longer contains `source .env.local` or the `set -a / set +a` envelope around it
- New parser uses `while IFS= read -r line || [[ -n "$line" ]]; do ... done` idiom
- Whitelist `_ENV_ALLOWED` associative array gates which keys are exported
- `bash -n tools/manual-smoke.sh` passes (syntax OK)
- Manual smoke check (e) above with a value containing `(` returns "OK" or expected dry-run output (NOT the bash EOF-paren error)
- CI refusal still works: `CI=1 bash tools/manual-smoke.sh --dry` exits 1
- `PATH` is not clobbered when `.env.local` contains `PATH=/tmp/evil`
- `AVERS_PASSWORD` is exported (verified via `bash -x` trace or by the script's own python3 SHA-1 step at lines 116-120 functioning in dry-run-equivalent path)
- Commit landed on `phase-02/api-network-layer` branch
  </done>
</task>

<task type="auto">
  <name>Task 2: Document literal-parse semantics in tools/manual-smoke.README.md</name>
  <files>tools/manual-smoke.README.md</files>
  <action>
Append a short note to `tools/manual-smoke.README.md` explaining that `.env.local` is parsed literally — no need to escape or quote special characters in passwords / values. Place the note in the most natural location:

**Preferred:** at the end of §Setup (just before §Usage, after the existing item 3 about CI-prohibition guard sanity check). Add a new item 4 "Note: `.env.local` parsing semantics" with the explanation.

**Alternative:** add a bullet to §Troubleshooting matching the existing format (one-line problem + one-line resolution).

Do BOTH if the §Setup note benefits from a §Troubleshooting cross-reference. Keep total addition to ≤ 12 lines.

Content of the note (Russian or English — match surrounding language; the README is mixed RU/EN, leaning EN with RU error-message snippets, so EN is fine):

```
4. **Note: `.env.local` parsing semantics**

   `.env.local` is parsed as plain `KEY=VALUE` lines — NOT evaluated as a bash script.
   This means **special characters in values need no escaping or quoting**: passwords
   containing `(`, `)`, `$`, backtick, `"`, `'`, `\`, or spaces are read literally.
   Outer matching quotes are stripped (so both `PASS=abc` and `PASS="abc"` give the
   same result). Lines starting with `#` are comments. Only the whitelisted keys
   (`AVERS_LOGIN`, `AVERS_PASSWORD`, `AVERS_HOST`, `AVERS_LOGIN_PATH`,
   `AVERS_GRADES_PATH`) are exported — unknown keys are silently ignored.
```

If adding to §Troubleshooting as well, format as a bullet matching the existing list at lines 156-167:

```
- **`неожиданный конец файла во время поиска «)»`** — historical bug fixed in
  quick task 260429-uau. `.env.local` is now parsed literally; if you see this on
  an older checkout, pull latest `phase-02/api-network-layer` (or `main` after merge).
```

Commit separately from Task 1 (atomic — README is not load-bearing for the bug fix itself, just a doc-update follow-up):

```bash
git add tools/manual-smoke.README.md
git commit --no-verify -m "$(cat <<'EOF'
docs(02-08): document literal .env.local parse semantics in manual-smoke README

Adds a note to §Setup explaining that .env.local is plain KEY=VALUE
(not a bash script), so passwords with shell metacharacters need no
quoting. Cross-referenced from §Troubleshooting for users hitting the
historical "неожиданный конец файла" error on stale checkouts.

Quick: 260429-uau

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```
  </action>
  <verify>
    <automated>
grep -q -E 'parsed (as plain|literally|literal)|плоск|literal `KEY=VALUE`|literal KEY=VALUE' tools/manual-smoke.README.md && \
grep -q 'AVERS_PASSWORD' tools/manual-smoke.README.md && \
echo "VERIFY PASS"
    </automated>
  </verify>
  <done>
- `tools/manual-smoke.README.md` contains a note (in §Setup or §Troubleshooting or both) explaining that `.env.local` is parsed literally
- Note mentions: special chars need no quoting; outer matching quotes are stripped; whitelist of exported keys
- README addition is ≤ 12 lines (§Setup note) + ≤ 3 lines (optional §Troubleshooting bullet)
- Existing content of the README is otherwise untouched (no formatting churn, no reflow of unrelated paragraphs)
- Commit landed on `phase-02/api-network-layer` branch as a separate commit from Task 1
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| `.env.local` (untrusted file content) → bash process | The parser interprets file contents as data, not as code. A malicious or accidentally-corrupted `.env.local` must not be able to execute commands or clobber security-critical environment variables. |
| bash process → `python3 -c` child (SHA-1 step at lines 116-120) | AVERS_PASSWORD is passed via `os.environ` (env-inheritance), never via argv. The new parser must `export` the value so this inheritance still works. |
| bash process → curl child (lines 140-151, 198-208) | Only `--data-urlencode` from variables — values never appear in argv directly except for the username (already masked) and the SHA-1 hash (acceptable per 02-08-SUMMARY T-08-02 mitigation). |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-260429-01 | Tampering / Elevation of Privilege | new env parser in `tools/manual-smoke.sh` | mitigate | Whitelist of allowed keys (`_ENV_ALLOWED` associative array) — only `AVERS_*` keys are exported. A malicious `.env.local` line `PATH=/tmp/evil` is silently ignored. Without this, an attacker who tricks the user into copying a malicious `.env.local` could clobber `PATH` or `LD_PRELOAD` for the duration of the script. Verified by Task 1 step (b) and (c) of the manual smoke check. |
| T-260429-02 | Information Disclosure | new env parser printing parsed values | mitigate | Parser MUST NOT echo, log, or print parsed values. Existing pre-flight block (lines 88-95) only prints derived/masked forms (`LOGIN_HEAD="${AVERS_LOGIN:0:3}***"`). Task 1 action explicitly forbids adding any echo of parsed values. Regression-check: `grep -E -c 'echo[[:space:]].*\$AVERS_PASSWORD' tools/manual-smoke.sh` must remain 0 (per 02-08-SUMMARY self-check). |
| T-260429-03 | Tampering | export not propagating to `python3 -c` child | mitigate | Parser uses `export "KEY=VALUE"` (quoted) so the value survives spaces and metacharacters into the environment. Verified by Task 1 step (d) — `bash -x` trace confirms `export AVERS_PASSWORD=...` happens before line 116. If this regresses, the SHA-1 step at line 119 returns the SHA-1 of empty string and the script exits 1 at line 122 — fail-safe, but easy to spot in CI / manual run. |
| T-260429-04 | Denial of Service | parser hangs on malformed `.env.local` (unterminated quote, etc.) | accept | The new parser is purely line-based (`while IFS= read -r line`) — no multi-line state, no quote balancing. Cannot hang. Worst case: a "weird" line is silently skipped. User sees `ERROR: AVERS_LOGIN or AVERS_PASSWORD missing in .env.local` at line 65-69 and can investigate. Not a security issue. |
| T-260429-05 | Information Disclosure | error message exposes file path or contents | accept | Standard bash error messages already include the file path (`.env.local: line 4: ...` in the original bug). New parser produces no such errors at all (no source-evaluation). If the file is unreadable, existing line 51 `if [[ ! -f .env.local ]]` guard handles it. Risk: zero new disclosure. |
| T-260429-06 | Spoofing | a key like `aVeRs_PaSsWoRd` (mixed case) bypasses whitelist | accept | The whitelist regex `^[A-Z_][A-Z0-9_]*$` is intentionally case-sensitive — only uppercase keys are recognized. Mixed-case keys are silently skipped. This matches Unix env-var convention. A user who types `avers_password=foo` will hit the existing line 65-69 check (AVERS_PASSWORD missing) and see a clear error. Documented in README §Setup as part of Task 2. |

All threats either mitigated by Task 1 implementation or accepted with rationale documented above. No new external attack surface — `.env.local` was already parsed (just by an unsafe mechanism); the new mechanism is strictly safer.
</threat_model>

<verification>
After both tasks complete, run the following manual checks (Task 1's `<automated>` block covers most; this is an end-to-end smoke check):

```bash
# 1. Both commits landed on phase-02/api-network-layer
git log --oneline | head -5
# Expected: top two commits match the messages from Tasks 1 and 2

# 2. Bug reproduction is fixed (with a real .env.local-like fixture)
TMPDIR_T=$(mktemp -d) && cat > "$TMPDIR_T/.env.local" <<'ENVFILE'
AVERS_LOGIN=test_user
AVERS_PASSWORD=p4ss(w0rd)w!th$pec'al"
ENVFILE
( cd "$TMPDIR_T" && cp "$OLDPWD/tools/manual-smoke.sh" ./smoke.sh && \
  CI= GITHUB_ACTIONS= GITLAB_CI= JENKINS_URL= bash ./smoke.sh --dry )
echo "Exit=$? (expect 0)"
rm -rf "$TMPDIR_T"
# Expected output: "DRY: skipping live HTTP. Resolved endpoints printed above." + exit 0
# NOT expected: "неожиданный конец файла во время поиска «)»"

# 3. CI refusal regression check (D-25)
CI=1 bash tools/manual-smoke.sh --dry; echo "Exit=$? (expect 1)"
GITHUB_ACTIONS=true bash tools/manual-smoke.sh --dry; echo "Exit=$? (expect 1)"

# 4. Existing 02-08-SUMMARY security self-checks still pass
test -x tools/manual-smoke.sh
bash -n tools/manual-smoke.sh
grep -q 'GITHUB_ACTIONS' tools/manual-smoke.sh
grep -q '\.env\.local' tools/manual-smoke.sh
grep -E -c 'echo[[:space:]].*\$AVERS_PASSWORD|echo[[:space:]].*\$\{AVERS_PASSWORD' tools/manual-smoke.sh  # → 0

# 5. README still mentions all original gates
grep -q 'D-25' tools/manual-smoke.README.md
grep -q '\.env\.local' tools/manual-smoke.README.md
grep -q 'literally\|literal `KEY=VALUE`\|literal KEY=VALUE' tools/manual-smoke.README.md  # NEW — added by Task 2

# 6. Open PR auto-updated (visible after push, optional check)
git status -sb
# Expected: "## phase-02/api-network-layer...origin/phase-02/api-network-layer [ahead 2]"
# (User pushes manually if/when ready — quick task does NOT push; respect commit-only-not-push rule)
```
</verification>

<success_criteria>
- [ ] `tools/manual-smoke.sh` no longer contains `source .env.local` (verified by `! grep -E '^[[:space:]]*source[[:space:]]+\.env\.local' tools/manual-smoke.sh`)
- [ ] `tools/manual-smoke.sh` parser handles values with `(`, `)`, `$`, backtick, `"`, `'`, `\`, spaces — verified by manual smoke check #2 in `<verification>` not producing the bash EOF-paren error
- [ ] Whitelist of allowed keys is enforced — verified by step (b) of Task 1 manual check (`PATH=/tmp/evil` ignored)
- [ ] AVERS_PASSWORD is exported and visible to the `python3 -c` SHA-1 child — verified by step (d) of Task 1 manual check
- [ ] CI refusal still works for all 4 env-vars (CI / GITHUB_ACTIONS / GITLAB_CI / JENKINS_URL) — verified by step #3 of `<verification>`
- [ ] mktemp + trap cleanup, username masking, never-echo-password gates all preserved — verified by re-running 02-08-SUMMARY self-check block
- [ ] `tools/manual-smoke.README.md` has a note explaining literal-parse semantics
- [ ] Two atomic commits on `phase-02/api-network-layer` branch (one for script, one for README)
- [ ] PR #2 auto-updates after `git push` (push is user's responsibility — quick task is commit-only per CLAUDE.md commit hygiene rules)
</success_criteria>

<output>
After completion, create `.planning/quick/260429-uau-fix-02-08-parse-env-local-as-literal-key/260429-uau-SUMMARY.md` summarizing:
- Bug reproduction (the EOF-paren error from `source` evaluation)
- Fix shape (literal KEY=VALUE parser with whitelist)
- Security gates preserved (D-25 / D-28 / SHA-1 env-read / mktemp+trap / username masking / no value echo)
- Two commits landed on `phase-02/api-network-layer` (script + README)
- Validation evidence: bash -n PASS, manual smoke with `(` in password PASS, CI=1 refusal still PASS
</output>
