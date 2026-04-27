---
phase: 01-foundation-compliance-infrastructure
plan: 05
type: execute
wave: 4
depends_on:
  - 01-02-hello-linteh-PLAN
  - 01-03-ci-workflows-PLAN
files_modified:
  - composeApp/build.gradle.kts
  - composeApp/PrivacyInfo.xcprivacy
  - .github/workflows/ci.yml
autonomous: true
requirements:
  - COMP-02
must_haves:
  truths:
    - "composeApp/PrivacyInfo.xcprivacy exists и содержит точно: NSPrivacyAccessedAPICategoryUserDefaults reason CA92.1, NSPrivacyAccessedAPICategoryFileTimestamp reason C617.1, NSPrivacyTracking=false, NSPrivacyCollectedDataTypes=[], NSPrivacyTrackingDomains=[]"
    - "composeApp/build.gradle.kts применяет plugin org.jetbrains.kotlin.apple-privacy-manifests:1.0.0 + privacyManifest embed(...) блок"
    - "CI iOS job (ci.yml) после Run iOS X64 tests запускает plutil -lint + plutil -extract grep verifications для CA92.1, C617.1, NSPrivacyTracking=false"
    - "plutil -lint composeApp/PrivacyInfo.xcprivacy на macOS exits 0 (XML/plist синтаксис valid)"
    - "iOS framework сборка через ./gradlew :composeApp:linkDebugFrameworkIosX64 успешна — plugin копирует PrivacyInfo.xcprivacy в framework"
  artifacts:
    - path: "composeApp/PrivacyInfo.xcprivacy"
      provides: "iOS Privacy Manifest declaration с required-reason API"
      contains: "CA92.1"
    - path: "composeApp/build.gradle.kts"
      provides: "apple-privacy-manifests plugin + embed(...) configuration"
      contains: "apple-privacy-manifests"
    - path: ".github/workflows/ci.yml"
      provides: "Updated CI workflow с PrivacyInfo lint step в iOS job"
      contains: "PrivacyInfo.xcprivacy"
  key_links:
    - from: "composeApp/build.gradle.kts"
      to: "composeApp/PrivacyInfo.xcprivacy"
      via: "privacyManifest embed(layout.projectDirectory.file(...))"
      pattern: "privacyManifest\\s*\\{[\\s\\S]*?embed\\s*\\("
    - from: ".github/workflows/ci.yml"
      to: "composeApp/PrivacyInfo.xcprivacy"
      via: "plutil -lint + plutil -extract grep"
      pattern: "plutil\\s+-lint\\s+composeApp/PrivacyInfo\\.xcprivacy"
---

<objective>
Создать iOS Privacy Manifest файл `composeApp/PrivacyInfo.xcprivacy` с required-reason API declarations, подключить `org.jetbrains.kotlin.apple-privacy-manifests:1.0.0` plugin в `composeApp/build.gradle.kts` для автоматической упаковки manifest в iOS framework, добавить CI lint step (plutil + grep) в iOS job `.github/workflows/ci.yml`.

Purpose: Закрывает COMP-02 success criterion ROADMAP — iOS-сборка содержит PrivacyInfo.xcprivacy с required-reason API (CA92.1, C617.1), NSPrivacyTracking=false, NSPrivacyCollectedDataTypes=[]. Закрывает pitfall #9 (Privacy Manifest заглушка). CI lint step обнаруживает регрессии (если decl случайно убран) сразу.

Output: composeApp/PrivacyInfo.xcprivacy валидный plist, упакован в iOS framework через official JetBrains plugin, CI lint шаг (plutil + grep CA92.1/C617.1) проходит на macos-15.
</objective>

<execution_context>
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/workflows/execute-plan.md
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md
@.planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md
@.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
@.planning/phases/01-foundation-compliance-infrastructure/01-02-SUMMARY.md
@.planning/phases/01-foundation-compliance-infrastructure/01-03-SUMMARY.md
@.planning/research/PITFALLS.md
@CLAUDE.md
</context>

## Tasks

<tasks>

<task type="auto">
  <name>Task 1: Create composeApp/PrivacyInfo.xcprivacy plist with exact required-reason API declarations</name>
  <files>
    composeApp/PrivacyInfo.xcprivacy
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section PrivacyInfo.xcprivacy → Точное содержимое — verbatim XML; section Reason-codes — что они формально декларируют; section JetBrains-recommended additional reason codes — fstat/stat/mach_absolute_time deferred)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-25 — точный list reason codes; D-26 CI lint commands)
    - .planning/research/PITFALLS.md (#9 Privacy Manifest stub — что закрывается)
  </read_first>
  <action>
    Создать `composeApp/PrivacyInfo.xcprivacy` — strict plist XML с D-25 reason codes. Содержимое файла (verbatim, по RESEARCH.md секция "PrivacyInfo.xcprivacy → Точное содержимое") должно быть следующим plist documentом:

    Корневой `<plist version="1.0">` содержит `<dict>` с пятью ключами:
    1. `NSPrivacyTracking` → `<false/>`
    2. `NSPrivacyTrackingDomains` → пустой `<array/>`
    3. `NSPrivacyCollectedDataTypes` → пустой `<array/>`
    4. `NSPrivacyAccessedAPITypes` → массив из двух dict-элементов:
       - первый dict: `NSPrivacyAccessedAPIType` = `NSPrivacyAccessedAPICategoryUserDefaults`, `NSPrivacyAccessedAPITypeReasons` = массив с одним string `CA92.1`
       - второй dict: `NSPrivacyAccessedAPIType` = `NSPrivacyAccessedAPICategoryFileTimestamp`, `NSPrivacyAccessedAPITypeReasons` = массив с одним string `C617.1`

    DOCTYPE и encoding — стандартные для plist (см. RESEARCH excerpt):
    ```
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    ```

    Indentation — четырёхпробельная (consistency с RESEARCH excerpt; plutil не чувствителен к whitespace, но human-readable).

    **Не добавлять в Phase 1** дополнительные reason codes (`fstat`/`stat`/`mach_absolute_time` — RESEARCH.md JetBrains-recommended additional reason codes → отложено в Phase 6 при первом TestFlight upload reactively, если App Store Connect reject ITMS-91053).

    **Locked semantics:**
    - `NSPrivacyTracking=false` — приложение не делает tracking (нет analytics/ads/трекеров — PROJECT.md Out of Scope; D-23 Privacy Policy)
    - `NSPrivacyCollectedDataTypes` empty — мы не collect никакие data types (on-device storage только)
    - `NSPrivacyTrackingDomains` empty — нет tracking domains (network communication только с journal.school28-kirov.ru — declared в Privacy Policy, НЕ tracking)
    - `CA92.1` (NSPrivacyAccessedAPICategoryUserDefaults) — для будущего использования multiplatform-settings/NSUserDefaults в Phase 3+; некоторые internal Compose Multiplatform API могут трогать UserDefaults, потому декларация обязательна с самого первого билда
    - `C617.1` (NSPrivacyAccessedAPICategoryFileTimestamp) — для будущего Room/SQLite в Phase 3+ (cache age stat)

    Verify locally на Linux Mint (plutil недоступен, full lint runs в CI Plan 05 Task 3):
    - `xmllint --noout composeApp/PrivacyInfo.xcprivacy` (если xmllint установлен; install: `apt-get install libxml2-utils`)
    - Альтернативно: `python3 -c "from xml.etree import ElementTree as ET; ET.parse('composeApp/PrivacyInfo.xcprivacy')"` — XML well-formed verification
    - Grep для каждого ключа — см. acceptance_criteria
  </action>
  <verify>
    <automated>test -f composeApp/PrivacyInfo.xcprivacy && python3 -c "from xml.etree import ElementTree as ET; ET.parse('composeApp/PrivacyInfo.xcprivacy')" && grep -c 'NSPrivacyTracking' composeApp/PrivacyInfo.xcprivacy | awk '{ exit ($1 < 2) ? 1 : 0 }' && grep -q 'NSPrivacyAccessedAPICategoryUserDefaults' composeApp/PrivacyInfo.xcprivacy && grep -q 'NSPrivacyAccessedAPICategoryFileTimestamp' composeApp/PrivacyInfo.xcprivacy && grep -q 'CA92.1' composeApp/PrivacyInfo.xcprivacy && grep -q 'C617.1' composeApp/PrivacyInfo.xcprivacy</automated>
  </verify>
  <acceptance_criteria>
    - File `composeApp/PrivacyInfo.xcprivacy` exists at exact path (composeApp directory root)
    - File parses as valid XML (`python3 -c "from xml.etree import ElementTree as ET; ET.parse(...)"` exits 0)
    - File starts with `<?xml version="1.0" encoding="UTF-8"?>` and contains DOCTYPE plist line referencing `http://www.apple.com/DTDs/PropertyList-1.0.dtd`
    - File contains root `<plist version="1.0">` element
    - File contains key `NSPrivacyTracking` with value `<false/>` (NOT `<true/>` — D-25 explicit; ITMS-91053 false-claim threat T-01-03 mitigation)
    - File contains key `NSPrivacyTrackingDomains` with empty array (`<array/>` self-closing)
    - File contains key `NSPrivacyCollectedDataTypes` with empty array (D-25 — мы ничего не собираем)
    - File contains key `NSPrivacyAccessedAPITypes` with array of exactly 2 dict elements
    - First dict element under `NSPrivacyAccessedAPITypes`: API type = `NSPrivacyAccessedAPICategoryUserDefaults`, reason = `CA92.1` (D-25 — multiplatform-settings/NSUserDefaults)
    - Second dict element under `NSPrivacyAccessedAPITypes`: API type = `NSPrivacyAccessedAPICategoryFileTimestamp`, reason = `C617.1` (D-25 — Room/SQLite filesystem-stat)
    - File does NOT contain `fstat`, `0A2A.1`, or `35F9.1` (these are JetBrains-recommended additional codes — explicitly deferred per RESEARCH "JetBrains-recommended additional reason codes" + D-25)
    - File does NOT contain Termly/iubenda boilerplate or third-party-tracker reason codes (custom-only per D-23 Privacy Policy semantic)
  </acceptance_criteria>
  <done>
    PrivacyInfo.xcprivacy готов как valid plist XML с точным содержимым D-25; готов к упаковке через apple-privacy-manifests plugin (Task 2) и CI lint step (Task 3).
  </done>
</task>

<task type="auto">
  <name>Task 2: Apply org.jetbrains.kotlin.apple-privacy-manifests plugin in composeApp/build.gradle.kts via TARGETED Edit (do NOT rewrite full file)</name>
  <files>
    composeApp/build.gradle.kts
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "PrivacyInfo.xcprivacy → Где упаковывается в .ipa" — точный Kotlin DSL; section "Common Pitfalls #2" — plugin не подключён → manifest не упаковывается; section "Module Skeleton → Per-module build.gradle.kts patterns")
    - composeApp/build.gradle.kts (создан в Plan 01, обновлён в Plan 02 — содержит BuildKonfig + commonTest.dependencies { compose.uiTest } блок; ОБА элемента — load-bearing, нельзя терять)
    - gradle/libs.versions.toml (создан в Plan 01 — содержит applePrivacyManifests = "1.0.0" + plugin alias)
    - composeApp/PrivacyInfo.xcprivacy (создан в Task 1)
  </read_first>
  <action>
    **CRITICAL — BLOCKER 2 (iter 2) mitigation: TARGETED EDIT, NOT FULL REWRITE.**

    **DO NOT** rewrite full `composeApp/build.gradle.kts`. Plan 02 Task 2 уже создал в этом файле `commonTest.dependencies { compose.uiTest }` блок (BLOCKER 1 fix iter 1) — full rewrite уничтожит этот fix и сломает Plan 02. Используй **Edit tool**, не Write.

    Apply EXACTLY two targeted edits:

    **Edit 1 — Add plugin alias to `plugins { ... }` block.**

    Найти существующий блок:
    ```kotlin
    plugins {
        alias(libs.plugins.kotlinMultiplatform)
        alias(libs.plugins.androidApplication)
        alias(libs.plugins.kotlinComposeCompiler)
        alias(libs.plugins.composeMultiplatform)
        alias(libs.plugins.buildkonfig)
        id("lintech-test")
    }
    ```

    Заменить на (добавлена ОДНА новая строка `alias(libs.plugins.applePrivacyManifests)` после `buildkonfig`):
    ```kotlin
    plugins {
        alias(libs.plugins.kotlinMultiplatform)
        alias(libs.plugins.androidApplication)
        alias(libs.plugins.kotlinComposeCompiler)
        alias(libs.plugins.composeMultiplatform)
        alias(libs.plugins.buildkonfig)
        alias(libs.plugins.applePrivacyManifests)
        id("lintech-test")
    }
    ```

    **Edit 2 — Add `privacyManifest { ... }` block ПОСЛЕ корневого `kotlin { ... }` блока (или сразу после iOS targets), ДО блоков `android { ... }` или `buildkonfig { ... }`.**

    Insertion point: на top-level (НЕ внутри `kotlin` или `android`), между closing `}` блока `kotlin { ... }` и opening `android {`. Точный текст для вставки:

    ```kotlin
    privacyManifest {
        embed(
            privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile,
        )
    }
    ```

    **Validate after edit (regression check — обязательно ДО verify command):**
    Confirm все три load-bearing fragmenta остаются в файле:
    1. `commonTest.dependencies` block (with `@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)` + `implementation(compose.uiTest)`) — BLOCKER 1 iter 1 fix, MUST remain
    2. `buildkonfig { ... }` block (Plan 02 Task 2 content) — MUST remain
    3. Entire `kotlin { ... }` block с iosX64()/iosArm64()/iosSimulatorArm64() targets, sourceSets, commonMain/androidMain dependencies — MUST remain unchanged

    Если хоть один из этих fragmenta пропал из файла после edit → REVERT и выполни targeted edit заново.

    Notes:
    - Plugin id `org.jetbrains.kotlin.apple-privacy-manifests`, version `1.0.0` (locked в gradle/libs.versions.toml — Plan 01).
    - `privacyManifest { embed(...) }` блок располагается на top-level scope (НЕ внутри kotlin/android). Ordering между android и buildkonfig не строгий, но human-readable.
    - `layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile` — относительный path от composeApp module dir. Файл создан в Task 1 в `composeApp/PrivacyInfo.xcprivacy`.
    - Plugin **автоматически** копирует PrivacyInfo.xcprivacy в `Frameworks/ComposeApp.framework/PrivacyInfo.xcprivacy` при сборке Apple framework. Manual copy НЕ нужен (Pitfall #2 mitigation: единственный officially-supported путь).

    Verify after edit:
    1. `./gradlew :composeApp:tasks --all | grep -i privacy` — должен показать plugin-generated tasks (например, `processIosSimulatorArm64ResourcesForRelease` могут содержать privacyManifest dependency).
    2. `./gradlew :composeApp:compileKotlinIosX64` — должен пройти без plugin configuration errors. Если plugin errors — проверить, что libs.versions.toml содержит `applePrivacyManifests = "1.0.0"` (Plan 01) и `applePrivacyManifests = { id = "org.jetbrains.kotlin.apple-privacy-manifests", version.ref = "applePrivacyManifests" }` под `[plugins]`.
    3. `./gradlew :composeApp:linkDebugFrameworkIosX64` — full link iOS framework (опционально на Linux Mint, может не запуститься без xcrun; **обязательно verifies в CI macos-15 в Task 3**).
  </action>
  <verify>
    <automated>./gradlew :composeApp:compileKotlinIosX64 2>&1 | grep -qE "BUILD SUCCESSFUL" && grep -q "alias(libs.plugins.applePrivacyManifests)" composeApp/build.gradle.kts && grep -q "privacyManifest" composeApp/build.gradle.kts && grep -q "compose.uiTest" composeApp/build.gradle.kts && grep -q "PrivacyInfo.xcprivacy" composeApp/build.gradle.kts</automated>
  </verify>
  <acceptance_criteria>
    - File `composeApp/build.gradle.kts` plugins block contains `alias(libs.plugins.applePrivacyManifests)` (resolved через libs.versions.toml — Plan 01 — to `org.jetbrains.kotlin.apple-privacy-manifests:1.0.0`)
    - File `composeApp/build.gradle.kts` contains `privacyManifest {` block with `embed(privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile)` (RESEARCH "Где упаковывается в .ipa" verbatim DSL)
    - **BLOCKER 2 iter 2 regression-guard:** File `composeApp/build.gradle.kts` retains `compose.uiTest` reference inside a `commonTest.dependencies { ... }` block (Plan 02 Task 2 BLOCKER 1 iter 1 fix MUST NOT be lost). Если этой строки нет → Plan 05 Task 2 был выполнен через full rewrite вместо targeted edit — REVERT и redo as targeted Edit.
    - File `composeApp/build.gradle.kts` retains `buildkonfig { ... }` block с тремя buildConfigField declarations (VERSION_NAME, VERSION_CODE, IS_DEBUG) — Plan 02 content
    - File `composeApp/build.gradle.kts` retains all previous Plan 01+02 content (kotlin/android blocks, sourceSets, ios targets, BuildKonfig) — без regression
    - `./gradlew :composeApp:compileKotlinIosX64` exits 0 (plugin configures cleanly без errors)
    - Optional (если xcrun доступен): `./gradlew :composeApp:linkDebugFrameworkIosX64` exits 0 — full framework link с PrivacyInfo embedded. На Linux Mint dev-host этот task может skip или fail (требует xcrun); в CI macos-15 обязателен (Task 3).
    - VALIDATION.md Per-Task Verification Map row `01-05-01` updated to ✅ green после CI verification (CI macos-15 запустит linkDebugFrameworkIosX64)
  </acceptance_criteria>
  <done>
    apple-privacy-manifests plugin подключён через targeted edit (НЕ full rewrite), privacyManifest block указывает на composeApp/PrivacyInfo.xcprivacy. Plan 02 Task 2 fixes (compose.uiTest в commonTest, BuildKonfig блок) сохранены. Plugin готов автоматически копировать manifest в iOS framework при сборке. Готово к CI lint step в Task 3.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add PrivacyInfo lint steps to .github/workflows/ci.yml iOS job via TARGETED Edit (do NOT rewrite full file)</name>
  <files>
    .github/workflows/ci.yml
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "GitHub Actions Workflows → ci.yml" — Lint PrivacyInfo.xcprivacy step verbatim; section "Common Pitfalls #3" — plutil только на macOS; section "Code Examples → Example 3" — final structure)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-26 CI lint specification: plutil -lint + grep CA92.1 + C617.1 + NSPrivacyTracking=false)
    - .github/workflows/ci.yml (создан в Plan 03 — обновляется здесь; iOS job уже существует с правильной структурой: НЕТ Test step в Android job, НЕТ continue-on-error на Select Xcode 16, ЕСТЬ Verify Xcode version step)
    - composeApp/PrivacyInfo.xcprivacy (создан в Task 1 — путь PLIST в lint step)
  </read_first>
  <action>
    **CRITICAL — BLOCKER 1 (iter 2) mitigation: TARGETED EDIT, NOT FULL REWRITE.**

    **DO NOT** show or write the full ci.yml content. Plan 03 Task 1 уже установил правильную структуру:
    - Android job (jobs.android.steps) — содержит ТОЛЬКО `assembleDebug` + `lint` (БЕЗ Test step — Plan 03 BLOCKER 2 fix iter 1)
    - iOS job (jobs.ios.steps) — `Select Xcode 16` БЕЗ `continue-on-error: true` + последующий `Verify Xcode version` step (Plan 03 WARNING 2 fix iter 1)

    Full rewrite в Plan 05 Task 3 уничтожит оба этих fix-а. Используй **Edit tool**, не Write.

    Insertion-point: **ПОСЛЕ существующего step `Run iOS X64 tests` в `jobs.ios-build.steps`** (его run line — `./gradlew :composeApp:iosX64Test`). Найти эту строку и добавить ПОСЛЕ неё (с тем же отступом — 6 пробелов перед `- name:`) два новых step-блока.

    Insert exactly следующее (без изменений выше или ниже этого места):

    ```yaml
          - name: Link iOS X64 debug framework (verify privacyManifest plugin)
            # BLOCKER 1 iter 2 mitigation: targeted insertion ПОСЛЕ существующего "Run iOS X64 tests" step.
            # Plan 03 структура (Android job без Test step; Select Xcode 16 без continue-on-error;
            # Verify Xcode version present) — НЕ trogать.
            run: ./gradlew :composeApp:linkDebugFrameworkIosX64

          - name: Lint PrivacyInfo.xcprivacy
            run: |
              set -euo pipefail
              PLIST=composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy

              echo "Step 1: plutil -lint (XML/plist syntax)"
              plutil -lint "$PLIST"

              echo "Step 2: verify reason code CA92.1 (NSPrivacyAccessedAPICategoryUserDefaults)"
              plutil -extract NSPrivacyAccessedAPITypes xml1 "$PLIST" -o - | grep -q 'CA92.1' \
                || (echo "ERROR: CA92.1 (NSPrivacyAccessedAPICategoryUserDefaults reason) missing" && exit 1)

              echo "Step 3: verify reason code C617.1 (NSPrivacyAccessedAPICategoryFileTimestamp)"
              plutil -extract NSPrivacyAccessedAPITypes xml1 "$PLIST" -o - | grep -q 'C617.1' \
                || (echo "ERROR: C617.1 (NSPrivacyAccessedAPICategoryFileTimestamp reason) missing" && exit 1)

              echo "Step 4: verify NSPrivacyTracking=false"
              plutil -extract NSPrivacyTracking raw "$PLIST" | grep -q '^false$' \
                || (echo "ERROR: NSPrivacyTracking must be false (T-01-03 mitigation)" && exit 1)

              echo "Step 5: verify NSPrivacyCollectedDataTypes is array (empty)"
              COLLECTED=$(plutil -extract NSPrivacyCollectedDataTypes xml1 "$PLIST" -o -)
              echo "$COLLECTED" | grep -qE '<array(/| )' \
                || (echo "ERROR: NSPrivacyCollectedDataTypes must be an array (empty)" && exit 1)

              echo "PrivacyInfo.xcprivacy passes all required-reason checks."
    ```

    Note: PLIST path использует framework-embedded copy (`composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy`) — это lint integration check, что apple-privacy-manifests plugin реально упаковал manifest (Pitfall #2 closer). Source manifest (`composeApp/PrivacyInfo.xcprivacy`) тоже валиден, но проверка framework-copy дополнительно ловит regression в plugin packaging.

    **Validate after edit (regression check — обязательно ДО verify command):**
    Confirm все Plan 03 load-bearing структурные элементы остаются:
    1. Android job (`jobs.android.steps`) НЕ содержит `- name: Test` или `- name: Run tests` step (BLOCKER 2 iter 1 fix)
    2. iOS job (`jobs.ios.steps`) `Select Xcode 16` step НЕ содержит `continue-on-error: true` (WARNING 2 iter 1 fix)
    3. iOS job содержит explicit `Verify Xcode version` step после `Select Xcode 16` (WARNING 2 iter 1 fix)
    4. iOS job retains pre-existing steps в правильном порядке: Checkout → Set up JDK 17 → Select Xcode 16 → Verify Xcode version → Setup Gradle → Run iOS X64 tests → (NEW) Link iOS X64 debug framework → (NEW) Lint PrivacyInfo.xcprivacy

    Если хоть один из этих regression-guards нарушен → REVERT и выполни targeted edit заново.

    Verify YAML syntax: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`.

    Pitfall #3 mitigation: lint step **только** в `jobs.ios.steps` (не в Android job — `plutil` macOS-only). Уже корректно расположен.
  </action>
  <verify>
    <automated>python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && ! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$" .github/workflows/ci.yml && ! grep -q "continue-on-error: true" .github/workflows/ci.yml && grep -q "Verify Xcode version" .github/workflows/ci.yml && grep -q "Link iOS X64 debug framework" .github/workflows/ci.yml && grep -q "Lint PrivacyInfo.xcprivacy" .github/workflows/ci.yml && grep -q "plutil -lint" .github/workflows/ci.yml && grep -q "plutil -extract NSPrivacyAccessedAPITypes" .github/workflows/ci.yml && grep -q "CA92.1" .github/workflows/ci.yml && grep -q "C617.1" .github/workflows/ci.yml && grep -q "NSPrivacyTracking" .github/workflows/ci.yml && grep -q "linkDebugFrameworkIosX64" .github/workflows/ci.yml && grep -q "set -euo pipefail" .github/workflows/ci.yml</automated>
  </verify>
  <acceptance_criteria>
    - File `.github/workflows/ci.yml` is well-formed YAML (parseable by yaml.safe_load)
    - File `.github/workflows/ci.yml` iOS job (jobs.ios.steps) contains step named `Link iOS X64 debug framework (verify privacyManifest plugin)` running `./gradlew :composeApp:linkDebugFrameworkIosX64` — Pitfall #2 integration check
    - File `.github/workflows/ci.yml` iOS job contains step named `Lint PrivacyInfo.xcprivacy` (bash multi-step with `set -euo pipefail`)
    - Lint step uses framework-embedded plist path `composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy` (verifies plugin packaging — Pitfall #2 integration)
    - Lint step contains `plutil -lint` referencing PLIST var (syntax validation — D-26)
    - Lint step contains `plutil -extract NSPrivacyAccessedAPITypes xml1 ... | grep -q 'CA92.1'` (D-26 — defensive reason code check)
    - Lint step contains `plutil -extract NSPrivacyAccessedAPITypes xml1 ... | grep -q 'C617.1'` (D-26)
    - Lint step contains `plutil -extract NSPrivacyTracking raw ... | grep -q '^false$'` (T-01-03 mitigation: false-claim guard)
    - Lint step contains `plutil -extract NSPrivacyCollectedDataTypes` array verification
    - Lint step is located **inside `jobs.ios.steps`** (NOT в `jobs.android.steps`) — Pitfall #3 mitigation (plutil macOS-only)
    - **BLOCKER 1 iter 2 regression-guard #1:** Android job (`jobs.android.steps`) **unchanged from Plan 03** — содержит только `assembleDebug` + `lint` (NO test step), per Plan 03 BLOCKER 2 fix iter 1. `! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$" .github/workflows/ci.yml` MUST exit 0.
    - **BLOCKER 1 iter 2 regression-guard #2:** iOS job retains `Verify Xcode version` step from Plan 03 (NO `continue-on-error: true` на `Select Xcode 16`, per Plan 03 WARNING 2 fix iter 1). `! grep -q "continue-on-error: true" .github/workflows/ci.yml` MUST exit 0; `grep -q "Verify Xcode version"` MUST exit 0.
    - iOS job retains pre-existing steps from Plan 03: Checkout, Set up JDK 17, Select Xcode 16, Verify Xcode version, Setup Gradle, Run iOS X64 tests
    - VALIDATION.md Per-Task Verification Map row `01-05-02` updated to ✅ green after CI run on macos-15 (verified later by checker)
  </acceptance_criteria>
  <done>
    CI workflow обновлён через targeted edit: добавлены ровно два новых step (`Link iOS X64 debug framework` + `Lint PrivacyInfo.xcprivacy`) после `Run iOS X64 tests` в iOS job. Plan 03 fixes (Android без Test step, Select Xcode без continue-on-error, Verify Xcode version present) полностью сохранены. После merge: на push в main / PR — iOS job на macos-15 запускает iosX64Test → linkDebugFrameworkIosX64 → plutil-lint chain. Любой regression в PrivacyInfo.xcprivacy (drop CA92.1/C617.1, flip Tracking, populate CollectedDataTypes) → CI fail. COMP-02 success criterion закрывается через automated regression detection.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| iOS Bundle .ipa → App Store Connect | TestFlight upload (Phase 6) — Apple validator проверяет PrivacyInfo.xcprivacy presence + correctness |
| Repo PrivacyInfo.xcprivacy → CI lint → push to main | CI gate prevents merge with broken/missing manifest |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-03 | (compliance, не STRIDE) — false claim в Privacy Manifest | composeApp/PrivacyInfo.xcprivacy | mitigate | NSPrivacyTracking=false declared (point of fact: приложение не tracking — PROJECT.md Out of Scope confirmed; никаких analytics/ads/Yandex.Metrica). CI lint step grep `^false$` обнаруживает случайный flip к true. ITMS-91053 reject — самый высокий compliance risk; mitigation = exact verbatim D-25 list + automated regression detection. |
| T-01-pm-01 | T (Tampering) — manifest dropped | PR removing PrivacyInfo.xcprivacy or weakening reason codes | mitigate | CI lint step (Task 3) запускается на каждом PR; main branch protected (Plan 03 Task 3 manual UI step) — merge невозможен с red CI. |
| T-01-pm-02 | T (Tampering) — supply chain Plugin | org.jetbrains.kotlin.apple-privacy-manifests:1.0.0 | accept | Officially-supported JetBrains plugin (RESEARCH "Don't Hand-Roll": единственный officially-supported путь). Pinned 1.0.0. Regular monitoring при future minor versions. |
| T-01-pm-03 | I (Information disclosure) — false negative reason codes | Missing fstat/0A2A.1, mach_absolute_time/35F9.1 | accept (Phase 1) | RESEARCH JetBrains-recommended additional reason codes — могут потребоваться при Phase 6 TestFlight upload (issue #4738 в JetBrains/compose-multiplatform tracker). Phase 1 фиксирует минимум D-25; reactive expansion в Phase 6. |
</threat_model>

<verification>
- File `composeApp/PrivacyInfo.xcprivacy` exists with exact D-25 reason codes (CA92.1 + C617.1 + Tracking=false + empty CollectedDataTypes/TrackingDomains)
- File parses as valid XML/plist
- File `composeApp/build.gradle.kts` applies `alias(libs.plugins.applePrivacyManifests)` plugin and contains `privacyManifest { embed(...) }` block
- File `composeApp/build.gradle.kts` retains `compose.uiTest` reference in commonTest.dependencies (Plan 02 BLOCKER 1 iter 1 fix preserved)
- `./gradlew :composeApp:compileKotlinIosX64` succeeds locally на Linux Mint
- `.github/workflows/ci.yml` Android job unchanged from Plan 03 (no Test step — BLOCKER 2 iter 1 fix preserved)
- `.github/workflows/ci.yml` iOS job retains `Verify Xcode version` step (WARNING 2 iter 1 fix preserved)
- `.github/workflows/ci.yml` contains plutil-lint step in jobs.ios.steps (НЕ в android)
- After merge: CI iOS job (macos-15) runs `linkDebugFrameworkIosX64` (verifies plugin pack), then `Lint PrivacyInfo.xcprivacy` (defensive lint chain — все 5 plutil sub-steps green)
- VALIDATION.md Per-Task Verification Map rows `01-05-01`, `01-05-02` updated to ✅ green после CI run
- COMP-02 success criterion #4 (PrivacyInfo + lint green) — закрыт после Plan 05
</verification>

<success_criteria>
1. **PrivacyInfo.xcprivacy valid plist с exact D-25 content** — XML well-formed; CA92.1 + C617.1 reason codes; NSPrivacyTracking=false; NSPrivacyCollectedDataTypes/TrackingDomains empty.
2. **apple-privacy-manifests plugin успешно копирует manifest в iOS framework** — `./gradlew :composeApp:linkDebugFrameworkIosX64` succeeds in CI macos-15 (Pitfall #2 closer).
3. **CI lint step регрессионная защита** — любой будущий PR который удаляет/изменяет CA92.1, C617.1, или flip Tracking → CI fail.
4. **macOS-only constraint enforced** — `plutil` lint step в jobs.ios.steps только; Pitfall #3 mitigation explicit.
5. **Phase 6 TestFlight readiness** — при первом TestFlight upload не будет ITMS-91053 reject из-за missing required-reason API (минимум D-25 declared; дополнительные codes — reactive в Phase 6 если App Store Connect reject).
6. **COMP-02 закрыт** — Privacy Manifest присутствует, lint validates на каждом коммите.
7. **Plan 03 fixes preserved** — Android job без Test step, iOS Select Xcode без continue-on-error, Verify Xcode version step present (BLOCKER 1 iter 2 regression-guards honored).
8. **Plan 02 fixes preserved** — composeApp/build.gradle.kts retains commonTest.dependencies { compose.uiTest } block (BLOCKER 2 iter 2 regression-guard honored).
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-compliance-infrastructure/01-05-SUMMARY.md` with:
- What was built (PrivacyInfo.xcprivacy + apple-privacy-manifests plugin via targeted edit + CI lint steps via targeted edit)
- Files created/modified (composeApp/PrivacyInfo.xcprivacy created, composeApp/build.gradle.kts edited (2 targeted insertions), .github/workflows/ci.yml edited (2 targeted insertions after Run iOS X64 tests))
- Confirmation что Plan 02 + Plan 03 fixes preserved (compose.uiTest в commonTest, Android без Test step, Select Xcode без continue-on-error, Verify Xcode version present)
- CI run URL первого зелёного run с PrivacyInfo lint passing
- Key decisions taken from `Claude's Discretion` (точная структура lint bash chain, ordering steps в iOS job, defensive `set -euo pipefail` strategy, framework-embedded plist path для integration check)
- Что отложено: дополнительные reason codes (fstat 0A2A.1, mach_absolute_time 35F9.1) — Phase 6 reactive
- COMP-02 status: ✅ closed (PrivacyInfo present, plugin embeds, CI lint regression-protect)
- Anything Plan 06 should know (CLAUDE.md update должен включить privacy-manifest discipline note: any iOS-side change should re-verify reason codes; добавление multiplatform-settings/Room в Phase 3 — реальное использование уже декларированных API, no manifest change needed)
</output>
</content>
</invoke>