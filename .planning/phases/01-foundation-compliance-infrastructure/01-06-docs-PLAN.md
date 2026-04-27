---
phase: 01-foundation-compliance-infrastructure
plan: 06
type: execute
wave: 5
depends_on:
  - 01-01-skeleton-PLAN
  - 01-02-hello-linteh-PLAN
  - 01-03-ci-workflows-PLAN
  - 01-04-privacy-policy-PLAN
  - 01-05-privacy-manifest-PLAN
files_modified:
  - .planning/ROADMAP.md
  - CLAUDE.md
  - README.md
  - .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
autonomous: true
requirements:
  - COMP-01
  - COMP-02
must_haves:
  truths:
    - "ROADMAP.md Phase 1 success criterion #2 обновлён: Hello LinTech на iOS валидируется через iosX64Test screenshot test (НЕ локальный Xcode на симуляторе) — Linux Mint dev-host constraint reflected"
    - "CLAUDE.md §Conventions заполнен: convention plugin pattern (lintech-kmp/-compose/-test), package root, testTag-based tests, iosX64Test для iOS validation"
    - "CLAUDE.md §Architecture заполнен: 5-module skeleton, package root io.github.chudoxl.linteh.journal.*, expect/actual pattern, dependency rules слои, SwiftPM (НЕ CocoaPods)"
    - "README.md обновлён с финальными ссылками (Privacy Policy URL working — Plan 04 deployed; CI status badge — Plan 03 active; PrivacyInfo lint mention — Plan 05 active)"
    - "Все 5 phase 1 ROADMAP success criteria — verifiable как ✅ closed (CI зелёный, Hello LinTech запускается на Android, Privacy URL HTTP 200, PrivacyInfo plugin works + lint passes, convention plugins ≤5 строк добавляют модуль)"
    - "01-VALIDATION.md frontmatter `nyquist_compliant: true` устанавливается в Plan 06 close-out (BLOCKER 5 fix) после того как все task-rows в Per-Task Verification Map = ✅ green по факту execution + first green CI run"
  artifacts:
    - path: ".planning/ROADMAP.md"
      provides: "Updated success criterion #2 to reflect Linux Mint dev-host"
      contains: "iosX64Test"
    - path: "CLAUDE.md"
      provides: "Filled §Conventions, §Architecture sections"
      contains: "lintech-kmp"
    - path: "README.md"
      provides: "Updated with Plan 04 + Plan 05 outcomes"
      contains: "PrivacyInfo.xcprivacy"
  key_links:
    - from: "CLAUDE.md §Architecture"
      to: ":composeApp / :core:platform / :core:ui / :core:network module structure"
      via: "documentation describes module dependency layers"
      pattern: "core:platform"
    - from: ".planning/ROADMAP.md Phase 1 success criterion #2"
      to: "iosX64Test in CI"
      via: "criterion text mentions screenshot test instead of local simulator"
      pattern: "iosX64Test"
---

<objective>
Финализировать документацию Phase 1: edit ROADMAP.md success criterion #2 reflecting Linux Mint dev-host constraint, fill CLAUDE.md sections §Conventions и §Architecture (currently TBD), update README.md с финальными outcomes (Privacy Policy live, CI active, PrivacyInfo lint enforced).

Purpose: Все артефакты Phase 1 готовы (Plans 01-05). Plan 06 — phase closeout: документирует canonical patterns установленные в этой фазе, чтобы Phases 2-6 могли референсить existing decisions без relitigate. ROADMAP edit фиксирует Linux Mint dev-host как explicit constraint (CONTEXT.md `<deferred>` requirement).

Output: ROADMAP.md, CLAUDE.md, README.md финализированы; Phase 1 closed-in-documentation.
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
@.planning/phases/01-foundation-compliance-infrastructure/01-01-SUMMARY.md
@.planning/phases/01-foundation-compliance-infrastructure/01-02-SUMMARY.md
@.planning/phases/01-foundation-compliance-infrastructure/01-03-SUMMARY.md
@.planning/phases/01-foundation-compliance-infrastructure/01-04-SUMMARY.md
@.planning/phases/01-foundation-compliance-infrastructure/01-05-SUMMARY.md
@CLAUDE.md
</context>

## Tasks

<tasks>

<task type="auto">
  <name>Task 1: Edit ROADMAP.md Phase 1 success criterion #2 (Linux Mint dev-host reflected) + add CI lint mention to #4</name>
  <files>
    .planning/ROADMAP.md
  </files>
  <read_first>
    - .planning/ROADMAP.md (current file — нужно знать exact existing wording success criterion #2 и #4)
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "ROADMAP edit задача" — exact before/after diff)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (`<deferred>` block — Корректировки ROADMAP.md, требуемые planner-у Phase 1)
  </read_first>
  <action>
    Edit `.planning/ROADMAP.md` — два targeted изменения в Phase 1 раздел:

    **Change 1 — Success criterion #2 (Linux Mint dev-host constraint):**
    Найти text:
    ```
      2. Разработчик может открыть проект в Android Studio + Xcode и запустить заглушку «Hello LinTech» на симуляторе iPhone и Android-устройстве
    ```
    Заменить на:
    ```
      2. Разработчик может открыть проект в Android Studio и запустить заглушку «Hello LinTech» на Android-устройстве/эмуляторе. iOS «Hello LinTech» валидируется через автоматический `iosX64Test` screenshot-test в CI на macos-15 runner-е (dev-host разработчика — Linux Mint, локальный Xcode недоступен)
    ```

    **Change 2 — Success criterion #4 (CI lint reference):**
    Найти text:
    ```
      4. iOS-сборка содержит `PrivacyInfo.xcprivacy` с required-reason API (`NSPrivacyAccessedAPICategoryUserDefaults` CA92.1, `NSPrivacyAccessedAPICategoryFileTimestamp` C617.1), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` — Xcode «Validate App» не выдаёт ITMS-91053
    ```
    Заменить на:
    ```
      4. iOS-сборка содержит `PrivacyInfo.xcprivacy` с required-reason API (`NSPrivacyAccessedAPICategoryUserDefaults` CA92.1, `NSPrivacyAccessedAPICategoryFileTimestamp` C617.1), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` — Xcode «Validate App» не выдаёт ITMS-91053; CI lint (`plutil -lint` + grep CA92.1, C617.1, NSPrivacyTracking=false) на macos-job не выдаёт ошибок на каждом коммите
    ```

    **Не трогать другие success criteria (#1, #3, #5)** — они корректны.

    **Не трогать другие phases (2-6)** — out of scope для Phase 1 plan.

    **Update Progress table** (опционально — если Phase 1 завершён в момент Plan 06): После всех Plans 01-06 mergeable, обновить таблицу:
    ```
    | 1. Foundation & Compliance Infrastructure | 6/6 | Complete | YYYY-MM-DD |
    ```
    Note: Plan 06 запускается после Plans 01-05 — но **сам Plan 06 ещё не закрывает фазу**; close formally — через `/gsd-verify-work` или `/gsd-close-phase` workflow. Plan 06 ставит status в `In Progress` или оставляет `Not started`. **Лучше оставить Progress table нетронутой** — closure handles by ✓gsd command.
  </action>
  <verify>
    <automated>grep -q "iOS «Hello LinTech» валидируется через автоматический \`iosX64Test\` screenshot-test" .planning/ROADMAP.md && grep -q "Linux Mint" .planning/ROADMAP.md && grep -q "CI lint" .planning/ROADMAP.md && grep -q "plutil -lint" .planning/ROADMAP.md && grep -q "macos-job" .planning/ROADMAP.md && ! grep -q "запустить заглушку «Hello LinTech» на симуляторе iPhone и Android-устройстве" .planning/ROADMAP.md</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/ROADMAP.md` Phase 1 Success Criteria #2 contains exact phrase `iosX64Test` (replaces previous "симуляторе iPhone и Android-устройстве" wording)
    - File `.planning/ROADMAP.md` Phase 1 Success Criteria #2 contains phrase `Linux Mint` (explicit dev-host constraint per CONTEXT `<deferred>` requirement)
    - File `.planning/ROADMAP.md` Phase 1 Success Criteria #4 contains phrase `CI lint` and `plutil -lint` (RESEARCH "ROADMAP edit задача → Закрепление D-25 как finalized")
    - File `.planning/ROADMAP.md` previous wording `«запустить заглушку «Hello LinTech» на симуляторе iPhone и Android-устройстве»` is REMOVED (single match → 0 matches)
    - File `.planning/ROADMAP.md` does NOT change wording для Success Criteria #1, #3, #5 (no regression — other criteria unchanged)
    - File `.planning/ROADMAP.md` does NOT change other phases sections (Phase 2-6 unchanged)
  </acceptance_criteria>
  <done>
    ROADMAP.md Phase 1 success criterion #2 reflects Linux Mint dev-host (CONTEXT `<deferred>` requirement satisfied); criterion #4 reflects CI lint as automated regression-protection.
  </done>
</task>

<task type="auto">
  <name>Task 2: Fill CLAUDE.md §Conventions and §Architecture sections (currently empty/TBD)</name>
  <files>
    CLAUDE.md
  </files>
  <read_first>
    - CLAUDE.md (current file — найти §Conventions и §Architecture sections; они currently say "Conventions not yet established" / "Architecture not yet mapped")
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "Documentation / CLAUDE.md Updates → CLAUDE.md additions" — exact text-additions для §Conventions и §Architecture)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-01..D-12 module skeleton, D-19 testTag, D-31 expect/actual)
    - .planning/research/ARCHITECTURE.md (multi-module layering pattern)
  </read_first>
  <action>
    Edit `CLAUDE.md` — заменить два раздела (`## Conventions` и `## Architecture`) с прежнего "TBD"-состояния на реальный content per RESEARCH.md "Documentation / CLAUDE.md Updates → CLAUDE.md additions".

    **Section §Conventions** — replace existing:
    ```
    ## Conventions

    Conventions not yet established. Will populate as patterns emerge during development.
    ```
    На:
    ```
    ## Conventions

    > Established in Phase 1 (Foundation). All future modules adhere to these patterns.

    ### Module structure

    Convention plugins в `build-logic/` — single source for KMP/Compose/Test boilerplate:

    - `lintech-kmp` — applies `org.jetbrains.kotlin.multiplatform` + `com.android.library`; configures KMP targets (android + iosX64 + iosArm64 + iosSimulatorArm64), JDK 17 toolchain, freeCompilerArgs `-Xexpect-actual-classes`, kotlin.test in commonTest.
    - `lintech-compose` — applies `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0+ Compose compiler); adds compose.runtime/foundation/material3/components.resources to commonMain.
    - `lintech-test` — applies `dev.mokkery`; adds kotlin.test + kotest.assertions + turbine to commonTest (compose.uiTest добавляется отдельно в `:composeApp/build.gradle.kts` commonTest — BLOCKER 1 fix iter 1, чтобы non-UI модули не тянули compose deps).

    Adding a new KMP module — minimum (≤5 строк plugins-блока):
    ```kotlin
    plugins {
        id("lintech-kmp")
        id("lintech-test")  // если нужны тесты
        id("lintech-compose")  // если UI-модуль
    }
    ```

    ### Package root

    `io.github.chudoxl.linteh.journal.*`. Транслитерация **`linteh`** (НЕ `lintech`) — закреплено в bundle id, package, GitHub Pages URL slug.

    Sub-packages module-aligned:
    - `io.github.chudoxl.linteh.journal.core.platform.*` — `:core:platform` module (expect/actual abstractions)
    - `io.github.chudoxl.linteh.journal.core.ui.*` — `:core:ui` module (design tokens, Phase 4)
    - `io.github.chudoxl.linteh.journal.core.network.*` — `:core:network` module (Ktor, Phase 2)

    ### Versioning

    Single source of truth — `gradle/libs.versions.toml` (Gradle 8+ Version Catalog). All plugin/library versions pinned; never use `latest.release` или unbound ranges. Bump versions через explicit PR.

    ### expect/actual pattern

    Platform abstractions live in `:core:platform`. `commonMain` declares `expect`, `androidMain` + `iosMain` provide `actual`. Phase 1 first example: `openUrl(url: String)` in `core/platform/src/commonMain/kotlin/.../UrlOpener.kt`.

    Future expect/actual targets: KVault wrappers (Phase 3), WorkManager/BGTaskScheduler (Phase 6).

    ### Tests

    `runComposeUiTest { ... }` в commonTest для UI-tests (cross-platform — runs on Android JVM-test и iosX64Test). Selectors через `Modifier.testTag("...")` + `onNodeWithTag("...")` — НЕ текстовые. iOS-сборки валидируются через `iosX64Test` в CI macos-15 runner (локальный Xcode недоступен на dev-host Linux Mint).

    ### Build & verification

    - Локальный Android: `./gradlew :composeApp:assembleDebug :composeApp:installDebug`
    - Локальный iOS-side compile: `./gradlew :composeApp:compileKotlinIosX64`
    - Full CI suite (mirrors GitHub Actions): `./gradlew assembleDebug lint test` (Android) + `./gradlew :composeApp:iosX64Test` (требует macOS)
    - Privacy Manifest lint: `plutil -lint composeApp/PrivacyInfo.xcprivacy` (только macOS — на Linux Mint xmllint as fallback)
    ```

    **Section §Architecture** — replace existing:
    ```
    ## Architecture

    Architecture not yet mapped. Follow existing patterns found in the codebase.
    ```
    На:
    ```
    ## Architecture

    > Mapped in Phase 1 (Foundation). Module skeleton scales for Phases 2-6.

    ### Multi-module layout

    ```
    LintehJournal/
    ├── composeApp/                      # Single application module (KMP)
    │   ├── commonMain                   # @Composable App() — Hello LinTech (Phase 1); future screens (Phase 4-5)
    │   ├── androidMain                  # MainActivity + MainApplication
    │   ├── iosMain                      # MainViewController
    │   └── PrivacyInfo.xcprivacy        # iOS Privacy Manifest (CA92.1 + C617.1)
    ├── core/
    │   ├── platform/                    # expect/actual abstractions (openUrl Phase 1; KVault Phase 3; BGTaskScheduler Phase 6)
    │   ├── ui/                          # Design tokens, OfflineBanner, staleness indicators (Phase 4)
    │   └── network/                     # Ktor HttpClientFactory, AVERS API contracts (Phase 2)
    └── build-logic/                     # Convention plugins (includedBuild — НЕ buildSrc)
        └── convention/
    ```

    Future modules (Phase 3+):
    - `:core:domain` — domain models, business logic
    - `:core:data` — repositories, data sources
    - `:core:database` — Room KMP, per-account DBs (`journal_${accountId}.db`)
    - `:feature:auth`, `:feature:grades`, etc. — feature-by-layer

    ### Dependency rules (D-07)

    Слои вниз: `:composeApp` → `:feature:*` → `:core:ui` → `:core:network` → `:core:platform`. Enforce через PR-review до Phase 5; build-time enforcement (gradle-modules-graph plugin) — после Phase 5 когда модулей >10.

    ### iOS integration

    Через **SwiftPM XCFramework** (НЕ CocoaPods — pitfall #15 closed in Phase 1). `:composeApp` exports `ComposeApp.framework` через `binaries.framework { isStatic = true }`. iOS app в `iosApp/` импортирует через standard Xcode build phase `embedAndSignAppleFrameworkForXcode` — задача активируется в Phase 6 при подготовке к TestFlight.

    Phase 1 валидирует iOS-side через `iosX64Test` в CI macos-15. Полный xcodebuild — Phase 6.

    ### Privacy & compliance

    - Privacy Policy: `https://chudoxl.github.io/LintehJournal/privacy/` (deployed via GitHub Pages from `docs/`)
    - iOS Privacy Manifest: `composeApp/PrivacyInfo.xcprivacy` упаковывается через `org.jetbrains.kotlin.apple-privacy-manifests:1.0.0` plugin; CI lint `plutil` + grep on macos-15 регрессионная защита
    - **PrivacyInfo deferred reason codes (Phase 6 reactive):** Phase 1 декларирует CA92.1 + C617.1. При первом TestFlight upload в Phase 6 могут потребоваться дополнительные reason codes (fstat 0A2A.1, mach_absolute_time 35F9.1) — JetBrains issue #4738. App Store Connect reject ITMS-91053 = sign to extend; добавление этих codes — single-source edit `composeApp/PrivacyInfo.xcprivacy` + регенерация CI lint expected-list.
    - Per-account scope (Phase 5+): отдельный файл БД на каждый аккаунт; cross-account leak prevention как архитектурный инвариант
    ```

    **Не трогать другие sections** (Project, Technology Stack, Project Skills, GSD Workflow Enforcement, Developer Profile, userEmail, currentDate). CLAUDE.md is the project instructions file — minimal targeted changes.

    Note (WARNING 2 iter 2 fix): описание `lintech-test` в §Conventions выше явно говорит "compose.uiTest добавляется отдельно в `:composeApp/build.gradle.kts` commonTest" — это reflect-ит actual состояние convention plugin после BLOCKER 1 iter 1 fix (LintechTestConventionPlugin не содержит compose.uiTest). Stale wording про "compose.uiTest в commonTest" внутри `lintech-test` description удалён.
  </action>
  <verify>
    <automated>grep -q "lintech-kmp" CLAUDE.md && grep -q "lintech-compose" CLAUDE.md && grep -q "lintech-test" CLAUDE.md && grep -q "io.github.chudoxl.linteh.journal" CLAUDE.md && grep -q "expect/actual" CLAUDE.md && grep -q "iosX64Test" CLAUDE.md && grep -q "testTag" CLAUDE.md && grep -q "build-logic/" CLAUDE.md && grep -q "SwiftPM" CLAUDE.md && grep -q "PrivacyInfo.xcprivacy" CLAUDE.md && ! grep -q "Conventions not yet established" CLAUDE.md && ! grep -q "Architecture not yet mapped" CLAUDE.md</automated>
  </verify>
  <acceptance_criteria>
    - File `CLAUDE.md` §Conventions section contains all three convention plugins names (`lintech-kmp`, `lintech-compose`, `lintech-test`) with descriptions
    - File `CLAUDE.md` §Conventions describes "≤5 строк plugins-блока" pattern (success criterion #5 verbatim)
    - File `CLAUDE.md` §Conventions documents package root `io.github.chudoxl.linteh.journal.*` and explicit `linteh` (NOT `lintech`) транслитерация
    - File `CLAUDE.md` §Conventions documents `runComposeUiTest` + `Modifier.testTag(...)` selector pattern (D-19)
    - **WARNING 2 iter 2 fix:** File `CLAUDE.md` §Conventions `lintech-test` description явно говорит "compose.uiTest добавляется отдельно в `:composeApp/build.gradle.kts` commonTest" (НЕ "compose.uiTest в commonTest" как часть convention plugin) — reflect actual состояние после BLOCKER 1 iter 1 fix.
    - File `CLAUDE.md` §Architecture documents 5-module layout (composeApp + core:platform + core:ui + core:network + build-logic)
    - File `CLAUDE.md` §Architecture documents dependency rules слои `:composeApp → :feature:* → :core:ui → :core:network → :core:platform` (D-07)
    - File `CLAUDE.md` §Architecture mentions SwiftPM (НЕ CocoaPods) — pitfall #15 closed
    - File `CLAUDE.md` §Architecture mentions PrivacyInfo.xcprivacy + apple-privacy-manifests plugin
    - File `CLAUDE.md` does NOT contain previous TBD wording: "Conventions not yet established" or "Architecture not yet mapped"
    - File `CLAUDE.md` does NOT modify §Project, §Technology Stack, §GSD Workflow Enforcement, §Developer Profile, # userEmail, # currentDate sections (no regression — only Conventions + Architecture replaced)
  </acceptance_criteria>
  <done>
    CLAUDE.md §Conventions + §Architecture filled with Phase 1-established patterns. Future agents читающие CLAUDE.md имеют canonical references для convention plugins, package root, expect/actual pattern, dependency layers — без relitigate Phase 1 decisions. WARNING 2 iter 2 fix: stale "compose.uiTest в commonTest" в lintech-test description обновлён на корректное reflection iter 1 BLOCKER 1 fix (compose.uiTest в composeApp вручную).
  </done>
</task>

<task type="auto">
  <name>Task 3: Update README.md with Plan 04 + Plan 05 outcomes (Privacy Policy live, PrivacyInfo lint enforced)</name>
  <files>
    README.md
  </files>
  <read_first>
    - README.md (created in Plan 03 — financial update here с финальными outcomes Plans 04-05)
    - .planning/phases/01-foundation-compliance-infrastructure/01-04-SUMMARY.md (Plan 04 outcomes — Privacy Policy live URL confirmed)
    - .planning/phases/01-foundation-compliance-infrastructure/01-05-SUMMARY.md (Plan 05 outcomes — PrivacyInfo lint active)
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "Documentation / CLAUDE.md Updates → README.md (root)" — base structure)
  </read_first>
  <action>
    Update `README.md` — small targeted дополнения reflecting Plans 04-05 outcomes. Не переписывать целиком, только enhancement.

    **Change 1 — Privacy Policy section update:**
    Найти существующий блок:
    ```
    ## Privacy Policy

    Политика конфиденциальности опубликована на: <https://chudoxl.github.io/LintehJournal/privacy/>

    Приложение не отправляет данные третьим лицам, не использует аналитику, не имеет рекламы.
    Все учётные данные хранятся локально в платформенном защищённом хранилище (iOS Keychain, Android Keystore).
    ```
    После него добавить (если ещё нет):
    ```
    iOS-сборка содержит `PrivacyInfo.xcprivacy` с required-reason API declarations (NSPrivacyAccessedAPICategoryUserDefaults CA92.1, NSPrivacyAccessedAPICategoryFileTimestamp C617.1), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` — упаковывается автоматически через `org.jetbrains.kotlin.apple-privacy-manifests:1.0.0` plugin. CI lint (plutil + grep на macos-15) предотвращает регрессии.
    ```

    **Change 2 — Phase 1 status block (новый раздел перед "Status"):**
    Добавить новый раздел или extend существующий "Status":
    ```
    ## Status

    **Phase 1 (Foundation & Compliance Infrastructure): COMPLETE**

    - ✅ Multi-module Gradle skeleton (`:composeApp` + `:core:platform`/`:core:ui`/`:core:network` + `build-logic/`)
    - ✅ Convention plugins (`lintech-kmp`, `lintech-compose`, `lintech-test`) — adding new module = ≤5 строк plugins-блока
    - ✅ GitHub Actions CI: Android (ubuntu-latest) + iOS (macos-15) на каждый push/PR
    - ✅ Privacy Policy опубликована: <https://chudoxl.github.io/LintehJournal/privacy/>
    - ✅ iOS PrivacyInfo.xcprivacy с required-reason API + CI lint regression-protect
    - ✅ Hello LinTech composable как первый working экран (Android-side runs локально; iOS-side validates через CI iosX64Test)

    Distribution v1 — TestFlight (iOS) + Google Play Internal track (Android) для семейного/классного использования. Public submission и formal legal compliance — отложены в v2.

    **Next: Phase 2 — API Reverse-Engineering & Network Layer.**
    ```

    **Не трогать другие разделы** (Tech Stack, Project Structure, Build & Test, CI, One-time Manual Setup, Developer Profile, License). Они актуальны.
  </action>
  <verify>
    <automated>grep -q "Phase 1 (Foundation & Compliance Infrastructure): COMPLETE" README.md && grep -q "PrivacyInfo.xcprivacy" README.md && grep -q "apple-privacy-manifests" README.md && grep -q "Next: Phase 2" README.md && grep -q "lintech-kmp" README.md && grep -q "≤5 строк" README.md && grep -q "https://chudoxl.github.io/LintehJournal/privacy/" README.md</automated>
  </verify>
  <acceptance_criteria>
    - File `README.md` Status section shows "Phase 1 (Foundation & Compliance Infrastructure): COMPLETE"
    - File `README.md` Status section lists all 6 closure-checkpoints (multi-module + convention plugins + CI + Privacy Policy + PrivacyInfo + Hello LinTech)
    - File `README.md` Privacy Policy section mentions PrivacyInfo.xcprivacy + apple-privacy-manifests plugin + CI lint
    - File `README.md` mentions "Next: Phase 2" pointer
    - File `README.md` retains build-status badge from Plan 03
    - File `README.md` retains build/test instructions from Plan 03
    - File `README.md` retains "One-time Manual Setup" section (Pages enable + Branch protection rule) from Plan 03
    - File `README.md` does NOT remove any pre-existing content from Plans 03 (no regression)
  </acceptance_criteria>
  <done>
    README.md финализирован с Plan 04 + Plan 05 outcomes; Phase 1 explicitly marked COMPLETE. Контрибьюторам и stakeholders видна готовность Phase 1 + roadmap указатель к Phase 2.
  </done>
</task>

<task type="auto">
  <name>Task 4: Finalize VALIDATION.md — set nyquist_compliant: true after first green CI confirms all task-rows ✅</name>
  <files>
    .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md (current state — wave_0_complete уже true с Plan 01 Task 3; nyquist_compliant: false до этого Task)
    - .planning/phases/01-foundation-compliance-infrastructure/01-01-SUMMARY.md
    - .planning/phases/01-foundation-compliance-infrastructure/01-02-SUMMARY.md
    - .planning/phases/01-foundation-compliance-infrastructure/01-03-SUMMARY.md (CI зелёный URL подтверждён)
    - .planning/phases/01-foundation-compliance-infrastructure/01-04-SUMMARY.md (Pages deploy + curl confirmed)
    - .planning/phases/01-foundation-compliance-infrastructure/01-05-SUMMARY.md (PrivacyInfo lint passing на macos-15)
  </read_first>
  <action>
    **BLOCKER 5 fix — finalize Nyquist contract closure.** Plan 01 Task 3 преждевременно ставил `nyquist_compliant: true` в VALIDATION.md frontmatter. По BLOCKER 5 fix этот write был перенесён сюда — финальный close-out, когда все task-rows в Per-Task Verification Map имеют реальные ✅ green статусы (по факту execution + first green CI run).

    1. Прочесть `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md`. Проверить:
       - Frontmatter `wave_0_complete: true` (установлен в Plan 01 Task 3)
       - Frontmatter `nyquist_compliant: false` (НЕ был установлен в `true` ни в одном предыдущем Plan — BLOCKER 5 contract honored)
       - Per-Task Verification Map содержит rows для всех 13 task entries (01-01-01..03, 01-02-01..02, 01-03-01..02, 01-04-01..02, 01-05-01..02, 01-06-01)

    2. По каждой row в Per-Task Verification Map — проверить factual статус из соответствующего SUMMARY:
       - 01-01-01..03 → 01-01-SUMMARY.md verification block
       - 01-02-01..02 → 01-02-SUMMARY.md (compileKotlinIosX64 success + iosX64Test deferred to CI per BLOCKER 3)
       - 01-03-01..02 → 01-03-SUMMARY.md (first green CI run URL)
       - 01-04-01..02 → 01-04-SUMMARY.md (Pages deploy + curl 200 + Last-Modified date stamped)
       - 01-05-01..02 → 01-05-SUMMARY.md (PrivacyInfo lint green on macos-15)
       - 01-06-01 → этот Plan текущий (manual review of ROADMAP+CLAUDE+README)

    3. Update each row Status column: ✅ green (если SUMMARY confirms execution + verify command exited 0).

    4. **Если хотя бы одна row ❌ red или ⚠️ flaky — НЕ устанавливать `nyquist_compliant: true`.** Вместо этого: записать в SUMMARY список red/flaky rows + recommend gap-closure planning (`/gsd-plan-phase 01 --gaps`). VALIDATION остаётся `nyquist_compliant: false`.

    5. **Если все rows ✅ green** — установить frontmatter `nyquist_compliant: true`. Обновить `Validation Sign-Off → Approval: pending` → `Approval: approved` (или дата approval).

    Acceptance: nyquist_compliant=true только когда factual data все task-rows confirmed green. Никаких optimistic write-ahead-of-evidence (что было BLOCKER 5).
  </action>
  <verify>
    <automated>grep -q 'nyquist_compliant: true' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md && grep -q 'wave_0_complete: true' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md && ! grep -qE '\| ⬜ \|?\s*$' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md && [ "$(grep -cE '\| ✅ \|?\s*$' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md)" -ge 13 ]</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` frontmatter `nyquist_compliant: true` (BLOCKER 5 fix: финализирован ТОЛЬКО в этом Task, ПОСЛЕ confirmation факта что все task-rows green)
    - **WARNING 1 iter 2 fix:** Per-Task Verification Map: ZERO rows со status ⬜ (pending) — verification regex `! grep -qE '\| ⬜ \|?\s*$' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` matches actual table format (rows кончаются на `| ⬜ |` или `| ⬜ |\n`).
    - **WARNING 1 iter 2 fix (positive):** Per-Task Verification Map содержит **минимум 13 rows** со status ✅ green: `[ "$(grep -cE '\| ✅ \|?\s*$' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md)" -ge 13 ]`. (Rows для всех 13 task entries: 01-01-01..03 + 01-02-01..02 + 01-03-01..02 + 01-04-01..02 + 01-05-01..02 + 01-06-01.)
    - Per-Task Verification Map: ZERO rows со status ❌ red (если есть — сначала gap-closure plan, потом возврат сюда)
    - File `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` `Validation Sign-Off → Approval` updated from `pending` к `approved` (с датой)
  </acceptance_criteria>
  <done>
    VALIDATION.md финализирован: `nyquist_compliant: true` отражает факт что все task-rows green по реальным SUMMARY-данным. WARNING 1 iter 2 fix: verification regex совпадает с actual table row format (cells end на `| ⬜ |` или `| ✅ |`); positive ✅ count ≥13 проверяет complete coverage. WARNING 4 iter 2 fix: cross-reference column НЕ требуется в acceptance criteria (drop) — VALIDATION.md template не содержит такой колонки; cross-reference desirable но не блокирует. BLOCKER 5 contract honored — нет optimistic write-ahead-of-evidence. Phase 1 формально готова к `/gsd-verify-work` или `/gsd-transition` to Phase 2.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Documentation files (ROADMAP/CLAUDE/README) | Public repository — содержит только публично-disclosable patterns; no secrets |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-doc-01 | I (Information disclosure) | CLAUDE.md, README.md, ROADMAP.md | accept | Документация references public patterns (CMP, Kotlin, GitHub Actions) — нет PII, нет credentials, нет internal infrastructure. Acceptable для open-source-style personal project. |
| T-01-doc-02 | T (Tampering) | Manual edits to ROADMAP/CLAUDE | mitigate | Branch protection (Plan 03 Task 3) blocks direct push to main. PRs требуют green CI. RESEARCH "ROADMAP edit задача" diff is locked в CONTEXT.md `<deferred>` — single source. |
</threat_model>

<verification>
- File `.planning/ROADMAP.md` Phase 1 success criterion #2 mentions `iosX64Test` and `Linux Mint`
- File `.planning/ROADMAP.md` Phase 1 success criterion #4 mentions `CI lint` and `plutil -lint`
- File `CLAUDE.md` §Conventions filled (≥1 reference to lintech-kmp/-compose/-test, package root, testTag, expect/actual)
- File `CLAUDE.md` §Conventions `lintech-test` description reflects iter 1 BLOCKER 1 fix (compose.uiTest добавляется в composeApp вручную, НЕ в convention plugin) — WARNING 2 iter 2 fix
- File `CLAUDE.md` §Architecture filled (≥1 reference to 5-module structure, dependency rules, SwiftPM, PrivacyInfo)
- File `README.md` Status block reflects Phase 1 COMPLETE
- VALIDATION.md Per-Task Verification Map row `01-06-01` updated to ✅ green
- VALIDATION.md frontmatter `nyquist_compliant: true` set (Task 4 — BLOCKER 5 close-out, after all task-rows green)
- VALIDATION.md verification: WARNING 1 iter 2 fix — regex matches actual table row format (`| ⬜ |` pending pattern)
- All 5 ROADMAP Phase 1 success criteria verifiable across Plans 01-05 outputs:
  - #1 (CI обе платформы зелёные) — Plan 03 + Plan 05
  - #2 (Hello LinTech runs) — Plan 02 (Android local) + Plan 03 CI iosX64Test
  - #3 (Privacy Policy опубликована, линк виден) — Plan 04 + Plan 02 strings.xml + Plan 03 README
  - #4 (PrivacyInfo + Validate App green) — Plan 05 (manifest + plugin + lint)
  - #5 (≤5 строк plugins-блока) — Plan 01 (convention plugins) verified в Plan 02 (composeApp/core/* build.gradle.kts)
</verification>

<success_criteria>
1. **ROADMAP.md Phase 1 success criterion #2 reflects Linux Mint constraint** — CONTEXT `<deferred>` requirement explicitly satisfied; future planners читают актуальный criterion.
2. **CLAUDE.md §Conventions + §Architecture filled** — convention plugins / package root / dependency rules / SwiftPM / PrivacyInfo пасы документированы; future agents (Phase 2-6 planners) могут reference без relitigate Phase 1.
3. **README.md final** — Phase 1 COMPLETE status + Plan 04/05 outcomes integrated; контрибьютор-onboarding clean.
4. **Phase 1 documentation closure** — все 5 ROADMAP success criteria verifiable через Plans 01-05 outputs.
5. **Готовность к Phase 2 transition** — `/gsd-transition` command can pick up clean state; next milestone = "API Reverse-Engineering & Network Layer".
6. **Nyquist contract honored (BLOCKER 5)** — `nyquist_compliant: true` устанавливается **только после** factual evidence что все task-rows green (Task 4); НЕ optimistic write-ahead в Plan 01.
7. **WARNING 1 iter 2 fix** — Verify regex для Per-Task Verification Map совпадает с actual cell format (`| ⬜ |` pending, `| ✅ |` green) — defensive completion check работает.
8. **WARNING 2 iter 2 fix** — `lintech-test` description в CLAUDE.md и Plan 02 `<interfaces>` отражает iter 1 BLOCKER 1 (compose.uiTest в composeApp вручную, НЕ в convention plugin).
9. **WARNING 4 iter 2 fix** — Cross-reference column для VALIDATION.md rows НЕ блокирует acceptance — desirable но optional.
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-compliance-infrastructure/01-06-SUMMARY.md` with:
- What was edited (ROADMAP.md success criterion #2 + #4; CLAUDE.md §Conventions + §Architecture; README.md Status + Privacy Policy section; VALIDATION.md frontmatter nyquist_compliant + sign-off)
- Files modified (.planning/ROADMAP.md, CLAUDE.md, README.md, .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md)
- BLOCKER 5 closure: confirmation что nyquist_compliant=true reflects реальные green task-rows (НЕ optimistic write)
- WARNING 1 iter 2 fix: VALIDATION.md verification regex обновлён на `| ⬜ |` (matches actual cell format), positive count check `[ "$(grep -cE '\| ✅ \|?\s*$' ...)" -ge 13 ]`
- WARNING 2 iter 2 fix: §Conventions `lintech-test` description обновлён — compose.uiTest deferred to composeApp, НЕ часть convention plugin
- WARNING 4 iter 2 fix: cross-reference acceptance criterion drop-нут (desirable, не блокирующий)
- Phase 1 closure check: все 5 ROADMAP success criteria mapped to Plans 01-05 outputs (verifiable)
- Key decisions taken from `Claude's Discretion` (точное wording §Conventions/§Architecture, README Status block structure)
- Phase 1 closeout summary:
  - Total plans: 6 (01-skeleton, 02-hello-linteh, 03-ci-workflows, 04-privacy-policy, 05-privacy-manifest, 06-docs)
  - Total waves: 6 (0 → 5)
  - Requirements closed: COMP-01 + COMP-02 (both verifiable)
  - Pitfalls closed: #15 (SwiftPM с дня 1), #16 (CI обе платформы), #9 (Privacy Manifest), частично #3 (Privacy Policy)
- Suggested next action: run `/gsd-verify-work 01` для verification, затем `/gsd-transition` to Phase 2
</output>
</content>
</invoke>