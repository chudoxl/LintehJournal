---
phase: 01-foundation-compliance-infrastructure
plan: 03
subsystem: infra
tags: [github-actions, ci, ci-workflow, macos-runner, xcode, gradle-cache, branch-protection, github-pages, readme]

requires:
  - phase: 01-01-skeleton
    provides: "Multi-module Gradle skeleton с :composeApp + :core:platform/ui/network — нужны для assembleDebug/lint/test commands из CI workflow"
  - phase: 01-02-hello-linteh
    provides: "AppTest infrastructure: AppTestAndroid.kt (Robolectric, androidUnitTest) + AppTest.kt (iosTest) — Android JVM tests теперь pass, iOS X64 tests существуют"

provides:
  - "GitHub Actions CI workflow `.github/workflows/ci.yml` — два параллельных job (android on ubuntu-latest, ios on macos-15)"
  - "Android job: ./gradlew assembleDebug + lint + test (full local-equivalent verification на CI)"
  - "iOS job: ./gradlew :composeApp:iosX64Test на macos-15 (закрытие dev-host gap — Linux Mint без Xcode)"
  - "Concurrency control: `cancel-in-progress: true` per branch — экономия CI minutes"
  - "Security minimal: `permissions: contents: read` + `cache-read-only: true` для PR (T-01-ci-01 mitigation)"
  - "Defensive Xcode-select pattern: explicit fallback chain `Xcode_16.app || Xcode_16.0.app || default` + verify step (Pitfall #4)"
  - "README.md в корне с CI build-status badge и canonical Privacy Policy URL — point of contact для contributors"
  - "Manual setup documentation: Settings → Pages + Settings → Branches → Branch Protection Rule (T-01-04 mitigate)"

affects: [01-04-privacy-policy, 01-05-privacy-manifest, 01-06-docs, all future plans (CI baseline)]

tech-stack:
  added:
    - "GitHub Actions ubuntu-latest runner (Android builds)"
    - "GitHub Actions macos-15 runner (iOS validation — закрывает dev-host gap)"
    - "actions/checkout@v4, actions/setup-java@v4 (java-version: 17, distribution: temurin)"
    - "gradle/actions/setup-gradle@v4 (official Gradle build cache action)"
  patterns:
    - "Two-job parallel CI (android + ios) — НЕ matrix, так чтобы failures были изолированы и логи легко читались"
    - "Xcode-select defensive pattern: `xcode-select -s ... || ... || echo \"Using default Xcode\"` + explicit `Verify Xcode version` step"
    - "Concurrency group naming: `ci-${{ github.ref }}` — уникальный per branch/PR"
    - "Cache strategy: `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` — main writes cache, PRs read-only (security: PR fork не может poison cache)"

key-files:
  created:
    - ".github/workflows/ci.yml (76 строк, 2 jobs)"
    - "README.md (118 строк — title, badge, privacy URL, tech stack, structure, build/test, CI, manual setup, dev profile)"
  modified: []

key-decisions:
  - "Concurrency group naming: `ci-${{ github.ref }}` — unique per ref (branch / PR head). Альтернативы (`ci-${{ github.workflow }}-${{ github.ref }}`) были бы overkill для single-workflow repo. Phase 1 имеет только ci.yml; Plan 04 добавит pages.yml но он будет иметь свой concurrency group (deploy-pages)."
  - "Xcode-select strategy: triple-fallback `Xcode_16.app → Xcode_16.0.app → default` через `||`. НЕ использован `continue-on-error: true` — он маскирует реальные ошибки (если оба Xcode-пути не существуют И default Xcode не подходит, мы хотим явный fail на следующих steps, а не silent skip). `Verify Xcode version` step печатает выбранный Xcode в логах — fail clearly если что-то не так."
  - "README structure: H1 → CI badge → tagline → Status → Privacy Policy → Tech Stack → Project Structure → Build & Test → CI → Manual Setup → Developer Profile → License. Manual Setup помещён ПЕРЕД Developer Profile (иначе слишком близко к низу) и ПОСЛЕ build/test инструкций (контекст: contributor сначала строит локально, потом думает про CI и manual setup)."
  - "License: MIT (TBD) — placeholder. Финализация перед v1 public release (отложено в v2 phase per CLAUDE.md Out of Scope statement)."

metrics:
  duration: "~2 minutes (execution-only; planning + override-handling separately)"
  tasks_completed: 2
  tasks_total: 3
  files_created: 2
  files_modified: 0
  completed_date: "2026-04-28"
---

# Phase 01 Plan 03: CI Workflows Summary

GitHub Actions CI workflow (Android + iOS параллельно) и README с manual setup документацией. Закрывает первый success criterion Phase 1 («CI собирает iOS и Android из коробки на каждом коммите») и Pitfall #16 (Android-only deps в commonMain ловятся CI iOS job сразу). macos-15 runner — критичен потому что dev-host разработчика на Linux Mint без локального Xcode (D-13).

## What Was Built

### Task 1 — `.github/workflows/ci.yml` (commit `b17040e`)

GitHub Actions workflow с двумя параллельными jobs:

**Android job (ubuntu-latest):**
- `actions/checkout@v4` → `actions/setup-java@v4` (JDK 17 Temurin) → `gradle/actions/setup-gradle@v4`
- `./gradlew assembleDebug` (D-14)
- `./gradlew lint`
- `./gradlew test` — **Rule 1 deviation** vs plan (см. Deviations section)

**iOS job (macos-15):**
- `actions/checkout@v4` → `actions/setup-java@v4` (JDK 17 Temurin)
- `Select Xcode 16` step (defensive fallback chain) — Pitfall #4 mitigation
- `Verify Xcode version` step — explicit visibility какой Xcode выбран
- `gradle/actions/setup-gradle@v4`
- `./gradlew :composeApp:iosX64Test` (D-15)

**Workflow-wide:**
- Triggers: `push: branches: [main]` + `pull_request: branches: [main]` (D-17)
- `permissions: contents: read` (security minimal — T-01-ci-01)
- `concurrency: group: ci-${{ github.ref }} cancel-in-progress: true` (CI minutes optimization)
- Cache strategy: `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` (main writes; PRs read-only)

YAML well-formed (validated `python3 -c "import yaml; yaml.safe_load(...)"`).

### Task 2 — `README.md` (commit `38b2383`)

Project root README с:
- H1 title + CI build-status badge (`https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml/badge.svg`)
- Status: pre-alpha, Phase 1 distribution v1 explanation
- **Privacy Policy URL** `https://chudoxl.github.io/LintehJournal/privacy/` (D-21, D-24)
- Tech Stack: CMP 1.10.3 + Kotlin 2.2.20 + JDK 17 + Android 26/35 + iOS 14, multi-module Gradle с convention plugins
- Project Structure (visual tree)
- Build & Test instructions (Android + iOS + full suite)
- CI section (referencing ci.yml)
- **One-time Manual Setup section** (закрытие Pitfall #8 + T-01-04):
  - GitHub Pages enable (Settings → Pages → Source: GitHub Actions)
  - Branch Protection Rule на main (Settings → Branches → require Android+iOS CI checks, up-to-date, conversation resolution)
- Developer Profile: Linux Mint без локального Xcode → CI macos-15 единственный путь iOS validation
- License: MIT (TBD — финализация перед v1 public release)

## Files Created

- `.github/workflows/ci.yml` (76 lines)
- `README.md` (118 lines)

## Files Modified

None — plan не требует модификации существующих файлов.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug-in-plan] Add `./gradlew test` to Android job**

- **Found during:** Task 1 (плановая sequence; orchestrator pre-flight override применён)
- **Issue:** Plan был написан когда AppTest падал на Android JVM (BLOCKER 2 — отсутствие Robolectric). Plan ставил защитный grep `! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$"` чтобы убедиться что Test step **отсутствует**, и явно говорил "НЕ запускаем `./gradlew test` на Android JVM в Phase 1".
- **State change:** В Wave 1 (commit `c16ef7b` — `fix(01-02): make AppTest pass on Android JVM via Robolectric`) добавлен `composeApp/src/androidUnitTest/kotlin/.../AppTestAndroid.kt` с `@RunWith(RobolectricTestRunner::class)` + `testOptions { unitTests.isIncludeAndroidResources = true }` в `composeApp/build.gradle.kts`. AppTest перенесён в `iosTest/`. `:composeApp:testDebugUnitTest` теперь PASSES.
- **Fix:** Add `./gradlew test` step to Android job. Это запускает Robolectric-based `AppTestAndroid` + Kotlin/JVM unit tests всех модулей (`:core:platform`, `:core:ui`, `:core:network`).
- **Files modified:** `.github/workflows/ci.yml`
- **Commit:** `b17040e`
- **Why this is critical:** Без Android JVM tests CI пропускает целый класс ошибок (Compose composition behavior на Android Robolectric), что прямо контрадиктует success criterion #5 ("Pitfall #16 closed — Android-only deps в commonMain ловятся CI iOS job сразу"). Симметрично, JVM-side validation должен ловить Android-specific composition issues. Acceptance criterion в plan-е (`! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$"`) основывался на устаревшем state и должен быть проигнорирован per orchestrator pre-flight directive.

**Note:** Плановый защитный комментарий "BLOCKER 2 mitigation: НЕ запускаем `./gradlew test`" заменён в YAML на короткую заметку "AppTest валидируется на Android JVM через Robolectric ... и на iOS через :composeApp:iosX64Test" — отражает actual реальность.

### Authentication Gates

None — Task 1+2 не требовали авторизации; Task 3 — manual GitHub UI step (это и есть suspended human-action checkpoint, не auth gate).

## Manual Checkpoint Outcome

**Task 3 status:** PENDING — awaiting human action.

Манифест manual steps (для пользователя):
1. **Enable GitHub Pages:** Settings → Pages → Source: GitHub Actions
2. **Branch Protection Rule на main:** Settings → Branches → require `Android` (CI) + `iOS` (CI) status checks, up-to-date branches, conversation resolution
3. **Verify CI is running:** github.com/chudoxl/LintehJournal/actions — должен появиться зелёный CI run после первого push (потенциально первый run этого PR)

См. `README.md` "One-time Manual Setup" section и `.planning/phases/01-foundation-compliance-infrastructure/01-03-ci-workflows-PLAN.md` Task 3 для подробной step-by-step инструкции.

CI run URL первого зелёного run будет добавлен в этот SUMMARY после `approved` или `approved-pending-ci` resume signal от пользователя.

## Notes for Future Plans

### Plan 04 (Privacy Policy)
- GitHub Pages **должен быть enabled через manual UI** (Task 3) ДО первого run pages.yml workflow — иначе deploy упадёт с "Pages site not enabled" (Pitfall #8). Plan 04 не требует additional manual steps если Task 3 done.
- pages.yml workflow должен использовать `fetch-depth: 0` для git log substitution (BLOCKER 3 iter 2 from Plan 04 design).

### Plan 05 (Privacy Manifest)
- PrivacyInfo lint step добавится в `ios:` job ci.yml после `Run iOS X64 tests`. Structure ready.
- **КРИТИЧНО:** Plan 05 должен использовать **targeted Edit** (not full rewrite) на `.github/workflows/ci.yml`. Если Plan 05 переписывает workflow целиком — следующие Plan 03 решения регрессируют:
  - Android job `Run Android JVM tests` step (Rule 1 deviation выше) — должен остаться
  - iOS job `Select Xcode 16` step без `continue-on-error: true` — должен остаться (WARNING 2 iter 1 fix preserved)
  - `Verify Xcode version` step — должен остаться
  - `concurrency:` block — должен остаться
  - `permissions: contents: read` — должен остаться

### Plan 06 (Docs)
- README.md уже создан Plan 03; Plan 06 расширяет docs (например, добавляет CONTRIBUTING.md, docs внутри `.planning/` ссылающиеся на README).

## Threat Flags

None new. Plan 03 закрыл/смитигировал threats per `<threat_model>` (T-01-04, T-01-ci-01); accepted T-01-ci-02 (well-known vendor Actions) и T-01-ci-03 (no secrets in Phase 1) — без regression.

## Self-Check: PASSED

Verified:
- `.github/workflows/ci.yml` exists (commit `b17040e`)
- `README.md` exists (commit `38b2383`)
- Both commits present in `git log --oneline`
- YAML well-formed (validated via `yaml.safe_load`)
- All `<verify><automated>` grep checks pass (with Rule 1 deviation: `! grep ...Test step` defensive check intentionally inverted — `Run Android JVM tests` step IS present)
- README contains all required tokens (CI badge URL, Privacy Policy URL, Compose Multiplatform version, Kotlin version, macos-15 reference, Branch Protection Rule, GitHub Pages, Linux Mint dev-host note)
