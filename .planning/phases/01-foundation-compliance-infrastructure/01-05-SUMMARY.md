---
phase: 01-foundation-compliance-infrastructure
plan: 05
subsystem: compliance
tags: [ios, privacy-manifest, xcprivacy, apple-privacy-manifests, ci-lint, plutil, comp-02]

requires:
  - phase: 01-02-hello-linteh
    provides: "composeApp/build.gradle.kts с BuildKonfig + commonTest.dependencies { compose.uiTest } + androidUnitTest Robolectric deps — load-bearing блоки, требующие preservation при targeted edit"
  - phase: 01-03-ci-workflows
    provides: ".github/workflows/ci.yml с Android job (assembleDebug + lint + testDebugUnitTest, БЕЗ bare Test step) + iOS job (Select Xcode 16 без continue-on-error + Verify Xcode version)"

provides:
  - "composeApp/PrivacyInfo.xcprivacy — iOS Privacy Manifest plist с D-25 required-reason API (CA92.1 + C617.1, NSPrivacyTracking=false, empty CollectedDataTypes/TrackingDomains)"
  - "apple-privacy-manifests plugin v1.0.0 (org.jetbrains.kotlin.apple-privacy-manifests) применён в composeApp — auto-копирует PrivacyInfo.xcprivacy в Frameworks/ComposeApp.framework/ при сборке iOS framework"
  - "CI iOS job lint chain — linkDebugFrameworkIosX64 (verify plugin packaging) + plutil-lint (5-step validation: syntax + CA92.1 + C617.1 + NSPrivacyTracking=false + NSPrivacyCollectedDataTypes empty)"
  - "Regression-protect для COMP-02 — любой PR который удаляет/изменяет CA92.1, C617.1, или flip Tracking → CI fail на macos-15"

affects: [01-06-docs, 02-api-research, 03-auth, 06-background-polling]

tech-stack:
  added:
    - "org.jetbrains.kotlin.apple-privacy-manifests 1.0.0 — официальный JetBrains plugin для упаковки PrivacyInfo.xcprivacy в iOS framework (RESEARCH "Don't Hand-Roll" — единственный officially-supported путь)"
  patterns:
    - "PrivacyInfo.xcprivacy располагается в module root composeApp/PrivacyInfo.xcprivacy (а не в src/iosMain/resources) — plugin embed() ссылается через layout.projectDirectory.file(...)"
    - "privacyManifest { embed(...) } блок — внутри kotlin { } scope (НЕ top-level), так как plugin регистрирует extension на KotlinMultiplatformExtension (выяснено эмпирически — Rule 3 deviation)"
    - "CI lint step проверяет framework-embedded copy (composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy), а не source manifest — integration check, что plugin реально packed"
    - "5-step plutil chain: -lint (syntax), -extract NSPrivacyAccessedAPITypes xml1 (grep CA92.1 + C617.1), -extract NSPrivacyTracking raw (grep ^false$), -extract NSPrivacyCollectedDataTypes (verify <array)"
    - "set -euo pipefail на всём bash multi-step CI lint — fail-fast на любой missing reason code или mis-configured manifest"

key-files:
  created:
    - "composeApp/PrivacyInfo.xcprivacy"
  modified:
    - "composeApp/build.gradle.kts (targeted Edit: добавлен alias(libs.plugins.applePrivacyManifests) в plugins{} + privacyManifest{} блок внутри kotlin{} scope)"
    - ".github/workflows/ci.yml (targeted Edit: добавлены 2 step ПОСЛЕ Run iOS X64 tests — Link iOS X64 debug framework + Lint PrivacyInfo.xcprivacy)"

key-decisions:
  - "[Rule 3 deviation] privacyManifest { ... } блок размещён ВНУТРИ kotlin { } scope, не на top-level. План указывал между kotlin{} и android{} (top-level), но apple-privacy-manifests v1.0.0 регистрирует extension на KotlinMultiplatformExtension. Top-level placement → 'Unresolved reference: privacyManifest' compile error. Fix: переместил блок внутрь kotlin {}. Inline-комментарий задокументировал причину."
  - "PrivacyInfo.xcprivacy расположен в composeApp/PrivacyInfo.xcprivacy (project module root), а не composeApp/src/iosMain/resources/PrivacyInfo.xcprivacy. Plugin embed() явно ожидает project-root path через layout.projectDirectory.file('PrivacyInfo.xcprivacy') — RESEARCH.md verbatim DSL."
  - "CI lint step использует framework-embedded copy path (composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy) для проверки, а не source manifest. Это integration test: убеждается, что plugin реально упаковал manifest в framework. Source-only check был бы недостаточен — pitfall #2 (plugin может отсутствовать, файл лежит в repo, но не попадает в .ipa)."
  - "Reason codes ограничены минимумом D-25 (CA92.1 + C617.1). JetBrains-recommended additional codes (fstat 0A2A.1, mach_absolute_time 35F9.1) — отложены в Phase 6 reactively, если App Store Connect upload reject ITMS-91053. Phase 1 фиксирует минимум, требуемый ROADMAP success criterion #4."
  - "Defensive set -euo pipefail на CI lint shell step — fail-fast strategy. Без неё bash продолжил бы выполнение после grep -q exit 1 (pipefail отключен по умолчанию в GitHub Actions runner). 5-step chain структурирован с явными echo Step N + per-step error message с reason code reference."

metrics:
  duration: "3m 41s (targeted edits + 2 Gradle builds: compileKotlinIosX64 для validation + linkDebugFrameworkIosX64 SKIPPED on Linux)"
  tasks_completed: 3
  files_created: 1
  files_modified: 2
  commits: 3
  completed_date: "2026-04-28"
---

# Phase 01 Plan 05: Privacy Manifest Summary

**One-liner:** iOS PrivacyInfo.xcprivacy + apple-privacy-manifests plugin embedding + CI plutil-lint regression-protect — закрывает COMP-02 success criterion.

## What Was Built

Закрыто всё, что требовал COMP-02 для Phase 1:

1. **`composeApp/PrivacyInfo.xcprivacy`** — strict plist XML с D-25 verbatim content:
   - `NSPrivacyTracking` = `false` (нет analytics/трекеров — PROJECT.md Out of Scope; T-01-03 mitigation)
   - `NSPrivacyTrackingDomains` = empty array
   - `NSPrivacyCollectedDataTypes` = empty array (on-device storage, ничего не collect)
   - `NSPrivacyAccessedAPITypes` array из 2 dict:
     - `NSPrivacyAccessedAPICategoryUserDefaults` reason `CA92.1` (для Phase 3+ multiplatform-settings/NSUserDefaults; некоторые internal Compose Multiplatform API могут трогать UserDefaults — декларация обязательна с самого первого билда)
     - `NSPrivacyAccessedAPICategoryFileTimestamp` reason `C617.1` (для Phase 3+ Room/SQLite cache-age stat)
   - DOCTYPE plist 1.0, UTF-8 encoding, 4-space indentation

2. **`composeApp/build.gradle.kts`** — `apple-privacy-manifests:1.0.0` plugin подключён через targeted Edit (НЕ rewrite — Plan 02 load-bearing блоки сохранены):
   - `alias(libs.plugins.applePrivacyManifests)` добавлен в `plugins {}` после `buildkonfig`
   - `privacyManifest { embed(...) }` блок добавлен **внутри** `kotlin { }` scope (не top-level — Rule 3 deviation, см. Key Decisions)
   - `embed(privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile)` — указывает plugin на manifest в module root

3. **`.github/workflows/ci.yml`** — 2 новых step добавлены через targeted Edit ПОСЛЕ существующего `Run iOS X64 tests` в `jobs.ios.steps`:
   - `Link iOS X64 debug framework (verify privacyManifest plugin)` — `./gradlew :composeApp:linkDebugFrameworkIosX64` (Pitfall #2 closer: integration check, что plugin реально упаковал manifest)
   - `Lint PrivacyInfo.xcprivacy` — bash multi-step с `set -euo pipefail`:
     - Step 1: `plutil -lint $PLIST` (XML/plist syntax)
     - Step 2: `plutil -extract NSPrivacyAccessedAPITypes xml1 ... | grep -q 'CA92.1'`
     - Step 3: `plutil -extract NSPrivacyAccessedAPITypes xml1 ... | grep -q 'C617.1'`
     - Step 4: `plutil -extract NSPrivacyTracking raw ... | grep -q '^false$'` (T-01-03 false-claim guard)
     - Step 5: `plutil -extract NSPrivacyCollectedDataTypes xml1 ...` + grep `<array(/| )` (verify empty array)
   - PLIST path = `composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy` (framework-embedded copy)
   - Step размещён ТОЛЬКО в `jobs.ios.steps` (Pitfall #3 — plutil macOS-only)

## Files Created/Modified

**Created:**
- `composeApp/PrivacyInfo.xcprivacy` — iOS Privacy Manifest plist (31 lines)

**Modified:**
- `composeApp/build.gradle.kts` (+18 lines, 0 deletions) — targeted Edit, 2 insertions
- `.github/workflows/ci.yml` (+41 lines, 0 deletions) — targeted Edit, 2 step insertions ПОСЛЕ `Run iOS X64 tests`

## Plan 02 + Plan 03 Fixes Preserved

Подтверждено grep-проверками после каждого edit:

**Plan 02 load-bearing (composeApp/build.gradle.kts):**
- ✅ `commonTest.dependencies { @OptIn(...) implementation(compose.uiTest) }` блок (BLOCKER 1 iter 1 fix) сохранён
- ✅ `buildkonfig { ... }` блок с тремя buildConfigField declarations (VERSION_NAME, VERSION_CODE, IS_DEBUG) сохранён
- ✅ `androidUnitTest by getting { dependencies { libs.robolectric, libs.androidx.test.ext.junit } }` сохранён
- ✅ `testOptions { unitTests.isIncludeAndroidResources = true }` сохранён
- ✅ `kotlin { iosX64()/iosArm64()/iosSimulatorArm64() + sourceSets + dependencies }` без regression

**Plan 03 load-bearing (.github/workflows/ci.yml):**
- ✅ Android job (`jobs.android.steps`) — НЕТ `- name: Test` или `- name: Run tests` step (BLOCKER 2 iter 1 fix preserved)
- ✅ iOS job — `Select Xcode 16` step БЕЗ `continue-on-error: true` (WARNING 2 iter 1 fix preserved)
- ✅ iOS job — `Verify Xcode version` step (WARNING 2 iter 1 fix preserved)
- ✅ Step ordering: Checkout → Set up JDK 17 → Select Xcode 16 → Verify Xcode version → Setup Gradle → Run iOS X64 tests → (NEW) Link iOS X64 debug framework → (NEW) Lint PrivacyInfo.xcprivacy

## Verification

Локально на Linux Mint:

| Check | Command | Result |
|-------|---------|--------|
| PrivacyInfo file exists | `test -f composeApp/PrivacyInfo.xcprivacy` | ✅ |
| XML well-formed | `python3 -c "from xml.etree import ElementTree as ET; ET.parse(...)"` | ✅ exits 0 |
| CA92.1 + C617.1 grep | `grep -q 'CA92.1'/'C617.1' composeApp/PrivacyInfo.xcprivacy` | ✅ both present |
| No deferred reason codes | `! grep -q '0A2A.1\|35F9.1\|fstat'` | ✅ confirmed absent |
| iOS Kotlin compile | `./gradlew :composeApp:compileKotlinIosX64` | ✅ BUILD SUCCESSFUL |
| iOS framework link | `./gradlew :composeApp:linkDebugFrameworkIosX64` | ⚠️ SKIPPED on Linux (требует macOS xcrun); validates в CI macos-15 |
| YAML well-formed | `python3 -c "import yaml; yaml.safe_load(open(...))"` | ✅ exits 0 |
| Plugin alias present | `grep -q 'applePrivacyManifests'` | ✅ |
| privacyManifest block present | `grep -q 'privacyManifest {'` | ✅ |
| CI lint step present | `grep -q 'Lint PrivacyInfo.xcprivacy'` | ✅ |

В CI macos-15 (после merge):
- iOS job pipeline: `iosX64Test` → `linkDebugFrameworkIosX64` (plugin packs manifest) → 5-step plutil-lint chain → all green
- Любой regression в PrivacyInfo.xcprivacy (drop reason code, flip Tracking, populate CollectedDataTypes) → step fails → PR blocked

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] privacyManifest extension scope mismatch**
- **Found during:** Task 2 — `./gradlew :composeApp:compileKotlinIosX64` validation
- **Issue:** План указывал размещение `privacyManifest { embed(...) }` блока на top-level scope (между closing `}` блока `kotlin { }` и opening `android {`). При попытке компиляции получили: `Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: public fun KotlinMultiplatformExtension.privacyManifest(configure: Action<PrivacyManifest>): Unit`. Plugin v1.0.0 регистрирует extension method на `KotlinMultiplatformExtension`, не на top-level `Project`.
- **Fix:** Перенёс `privacyManifest { ... }` блок ВНУТРЬ `kotlin { ... }` scope (после `sourceSets { ... }`). Добавил inline-комментарий с описанием причины. После fix `./gradlew :composeApp:compileKotlinIosX64` exits 0.
- **Files modified:** `composeApp/build.gradle.kts` (та же commit, что и Task 2)
- **Commit:** `1993059`
- **Why Rule 3 (blocking):** без этого fix Gradle конфигурация падает с compile error — невозможно завершить Task 2.
- **Plan accuracy:** план предложил placement на основе RESEARCH.md prose ("на top-level scope (НЕ внутри kotlin/android)"), но фактически JetBrains документация (kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html) показывает блок внутри `kotlin {}`. Рекомендую обновить RESEARCH.md в Plan 06 docs update для устранения двусмысленности.

### Out-of-Scope Findings

Никаких. Все changes — strictly в рамках 3 модифицируемых файлов плана (`composeApp/PrivacyInfo.xcprivacy`, `composeApp/build.gradle.kts`, `.github/workflows/ci.yml`).

## What Was Deferred

- **Дополнительные reason codes** (`fstat` 0A2A.1, `mach_absolute_time` 35F9.1) — JetBrains-recommended additional codes для Compose Multiplatform iOS (issue #4738 в JetBrains/compose-multiplatform tracker). Отложены в **Phase 6** reactively, если App Store Connect upload reject ITMS-91053. Phase 1 фиксирует минимум D-25, требуемый ROADMAP COMP-02 success criterion #4.
- **`plutil -lint` локально на Linux Mint** — невозможен (macOS-only). Отложен на CI macos-15. Локальная валидация через Python `xml.etree.ElementTree.parse()` достаточна для XML well-formedness; plutil semantic validation (plist DTD compliance) проверится при первом CI run.

## Threat Coverage

| Threat ID | Status | Mitigation |
|-----------|--------|-----------|
| T-01-03 (false claim в Privacy Manifest) | ✅ mitigated | NSPrivacyTracking=false declared verbatim; CI lint step `plutil -extract NSPrivacyTracking raw "$PLIST" \| grep -q '^false$'` обнаруживает любой случайный flip к true. |
| T-01-pm-01 (PR removing PrivacyInfo.xcprivacy / weakening reason codes) | ✅ mitigated | CI lint step запускается на каждом PR (push to main + pull_request to main). main branch protection rule (Plan 03 Task 3 manual UI step) блокирует merge с red CI. |
| T-01-pm-02 (supply chain — apple-privacy-manifests plugin) | ⏳ accepted | Officially-supported JetBrains plugin (RESEARCH "Don't Hand-Roll": единственный officially-supported путь). Pinned 1.0.0 в gradle/libs.versions.toml. Regular monitoring при future minor versions. |
| T-01-pm-03 (false negative reason codes — missing fstat/mach_absolute_time) | ⏳ accepted в Phase 1 | Phase 6 reactive expansion: при первом TestFlight upload в Phase 6 если App Store Connect reject ITMS-91053 на 0A2A.1/35F9.1 — добавить reason codes на тот момент. |

Никаких новых threat-flagged surface не открылось — изменения strictly compliance/CI infrastructure, не trust boundary.

## COMP-02 Status

✅ **Closed** — Privacy Manifest присутствует, plugin embeds manifest в iOS framework, CI lint regression-protect на каждом коммите. Compliance success criterion #4 (PrivacyInfo + lint green) — закрыт после первого CI run на main с iOS job green.

## Notes for Plan 06 (docs)

CLAUDE.md update должен включить privacy-manifest discipline note:
- Любой iOS-side change (новый Compose Multiplatform feature, новая KMP-library) — re-verify reason codes на покрытие (через `plutil -lint` локально на macOS dev-host или CI run)
- Добавление multiplatform-settings/Room в Phase 3 — реальное использование уже декларированных API (CA92.1 для UserDefaults, C617.1 для FileTimestamp), no manifest change needed
- При добавлении новых categories (Phase 3+ Network → возможно потребуется NSPrivacyAccessedAPICategoryDiskSpace; Phase 6 BGTaskScheduler → возможно UserDefaults для checkpointing) — обновить PrivacyInfo.xcprivacy + CI lint step grep checks

## TDD Gate Compliance

N/A — этот plan не TDD-типа (`type: execute`). Verification — в форме CI lint chain + локальная XML/Gradle validation.

## Self-Check: PASSED

**Files exist:**
- ✅ `composeApp/PrivacyInfo.xcprivacy` (FOUND)
- ✅ `composeApp/build.gradle.kts` modified (FOUND with applePrivacyManifests + privacyManifest)
- ✅ `.github/workflows/ci.yml` modified (FOUND with Lint PrivacyInfo.xcprivacy step)

**Commits exist:**
- ✅ `71fc784` — feat(01-05): add PrivacyInfo.xcprivacy with required-reason API (FOUND)
- ✅ `1993059` — feat(01-05): apply apple-privacy-manifests plugin to composeApp (FOUND)
- ✅ `6c22684` — feat(01-05): add PrivacyInfo lint to CI iOS job (FOUND)

**Verification commands all green:**
- ✅ `./gradlew :composeApp:compileKotlinIosX64` BUILD SUCCESSFUL
- ✅ `python3 ET.parse('composeApp/PrivacyInfo.xcprivacy')` exits 0
- ✅ `python3 yaml.safe_load(open('.github/workflows/ci.yml'))` exits 0
- ✅ All Plan 02 + Plan 03 regression guards pass

**Plan/Summary count alignment:**
- Plan tasks: 3
- SUMMARY tasks completed: 3
- Files created (plan): 1 (`composeApp/PrivacyInfo.xcprivacy`)
- Files created (SUMMARY): 1 ✅
- Files modified (plan): 2 (`composeApp/build.gradle.kts`, `.github/workflows/ci.yml`)
- Files modified (SUMMARY): 2 ✅
