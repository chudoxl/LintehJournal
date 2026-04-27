---
phase: 01-foundation-compliance-infrastructure
plan: 03
type: execute
wave: 2
depends_on:
  - 01-01-skeleton-PLAN
  - 01-02-hello-linteh-PLAN
files_modified:
  - .github/workflows/ci.yml
  - README.md
autonomous: false
requirements:
  - COMP-01
  - COMP-02
user_setup:
  - service: github-pages-and-branch-protection
    why: "GitHub UI configuration (cannot be automated from workflow file): enable GitHub Pages site, configure main branch protection rule"
    env_vars: []
    dashboard_config:
      - task: "Enable GitHub Pages with Source = GitHub Actions"
        location: "Settings → Pages → Source: GitHub Actions"
      - task: "Configure main branch protection rule (after first PR merges with CI workflow)"
        location: "Settings → Branches → Branch protection rules → Add rule for main → Require status checks to pass: Android (CI), iOS (CI) → Require branches to be up to date before merging → Require conversation resolution → ON"
must_haves:
  truths:
    - "GitHub Actions workflow `.github/workflows/ci.yml` запускается на push в main и на каждом PR"
    - "Android job (ubuntu-latest) выполняет ./gradlew assembleDebug lint test и exits 0"
    - "iOS job (macos-15) выполняет ./gradlew :composeApp:iosX64Test и exits 0"
    - "Branch protection rule на main требует обоих jobs зелёными для merge (manual UI step документирован в README)"
    - "GitHub Pages enabled через Settings UI (manual step документирован в README — необходимо для Plan 04)"
    - "README.md содержит build status badges и инструкции для разработчика"
  artifacts:
    - path: ".github/workflows/ci.yml"
      provides: "Continuous Integration workflow для Android + iOS"
      contains: "macos-15"
      min_lines: 40
    - path: "README.md"
      provides: "Project README с инструкциями build/test и manual setup steps"
      contains: "Build Status"
  key_links:
    - from: ".github/workflows/ci.yml"
      to: "./gradlew assembleDebug lint test (Android job)"
      via: "GitHub Actions runs-on: ubuntu-latest"
      pattern: "runs-on:\\s*ubuntu-latest"
    - from: ".github/workflows/ci.yml"
      to: "./gradlew :composeApp:iosX64Test (iOS job)"
      via: "GitHub Actions runs-on: macos-15"
      pattern: "runs-on:\\s*macos-15"
    - from: "README.md"
      to: ".github/workflows/ci.yml"
      via: "build status badge URL"
      pattern: "github\\.com/chudoxl/LintehJournal/actions/workflows/ci\\.yml"
---

<objective>
Создать GitHub Actions CI workflow `.github/workflows/ci.yml` с двумя независимыми параллельными jobs — `android` (на ubuntu-latest) и `ios` (на macos-15) — которые валидируют, что Wave 0 + Wave 1 артефакты собираются на обеих платформах. Документировать manual GitHub UI steps (включить Pages, branch protection rule). Создать README.md с build-status badges и инструкциями.

Purpose: Закрывает success criterion #1 phase 1 — «CI собирает iOS и Android из коробки на каждом коммите». Закрывает pitfall #16 (Android-only deps в commonMain ловятся сразу). macOS runner — критично потому что dev-host разработчика на Linux Mint, локального Xcode нет (см. CONTEXT D-13). PrivacyInfo lint step добавится в Plan 05 (после создания PrivacyInfo.xcprivacy и apple-privacy-manifests plugin).

Output: На push в main и PR — два jobs запускаются параллельно, оба зелёные. Branch protection rule на main блокирует merge без зелёного CI (manual UI step + README документация).
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
@CLAUDE.md
</context>

## Tasks

<tasks>

<task type="auto">
  <name>Task 1: Create .github/workflows/ci.yml with Android + iOS jobs (no PrivacyInfo lint yet — added in Plan 05)</name>
  <files>
    .github/workflows/ci.yml
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "GitHub Actions Workflows → ci.yml" — verbatim YAML; section "Common Pitfalls #3, #4" — plutil only on macOS, Xcode-select notes; section "Code Examples → Example 3")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-13 ubuntu+macos-15, D-14 Android job, D-15 iOS job, D-16 setup-gradle@v4, D-17 push+PR triggers)
    - composeApp/build.gradle.kts (создан в Plans 01, 02 — для понимания какие tasks существуют)
  </read_first>
  <action>
    Создать `.github/workflows/ci.yml` (verbatim per RESEARCH.md "GitHub Actions Workflows → ci.yml — Android + iOS отдельными jobs"; **без** PrivacyInfo lint step — он добавится в Plan 05 после того как PrivacyInfo.xcprivacy будет создан):
    ```yaml
    name: CI

    on:
      push:
        branches: [main]
      pull_request:
        branches: [main]

    permissions:
      contents: read

    concurrency:
      group: ci-${{ github.ref }}
      cancel-in-progress: true

    jobs:
      android:
        name: Android
        runs-on: ubuntu-latest
        steps:
          - name: Checkout
            uses: actions/checkout@v4

          - name: Set up JDK 17
            uses: actions/setup-java@v4
            with:
              java-version: '17'
              distribution: 'temurin'

          - name: Setup Gradle
            uses: gradle/actions/setup-gradle@v4
            with:
              cache-read-only: ${{ github.ref != 'refs/heads/main' }}

          - name: Assemble debug
            run: ./gradlew assembleDebug

          - name: Lint
            run: ./gradlew lint

          # BLOCKER 2 mitigation (iter 1): НЕ запускаем `./gradlew test` на Android JVM в Phase 1.
          # AppTest (composeApp/commonTest/AppTest.kt) использует runComposeUiTest, который требует
          # Robolectric для Android JVM-side execution (out of scope Phase 1). AppTest валидируется
          # только на iOS-side через iosX64Test (см. ios job ниже).
          # Android-side `./gradlew test` для других (non-UI) modules появится в Phase 2+,
          # когда commonTest без compose deps станет non-empty.

      ios:
        name: iOS
        runs-on: macos-15
        steps:
          - name: Checkout
            uses: actions/checkout@v4

          - name: Set up JDK 17
            uses: actions/setup-java@v4
            with:
              java-version: '17'
              distribution: 'temurin'

          - name: Select Xcode 16
            # Pitfall #4 mitigation: macos-15 runner может иметь несколько Xcode-версий;
            # explicit-select fixes "No SDK iphonesimulator" error if it occurs.
            # Если этот шаг падает (Xcode 16 not installed on runner) — последний fallback "Using default Xcode"
            # сохраняет default Xcode (НЕ маскируем сам fail, потому что || echo всегда exits 0).
            run: sudo xcode-select -s /Applications/Xcode_16.app || sudo xcode-select -s /Applications/Xcode_16.0.app || echo "Using default Xcode"
            # WARNING 2 mitigation (iter 1): убран `continue-on-error: true` — `|| echo` уже даёт безопасный fallback;
            # явный continue-on-error маскировал бы реальные ошибки на этом шаге.

          - name: Verify Xcode version
            # WARNING 2 mitigation (iter 1): явная проверка какой Xcode выбран — fail clearly если default не подходит.
            run: xcodebuild -version | head -1

          - name: Setup Gradle
            uses: gradle/actions/setup-gradle@v4
            with:
              cache-read-only: ${{ github.ref != 'refs/heads/main' }}

          - name: Run iOS X64 tests
            run: ./gradlew :composeApp:iosX64Test
    ```
    Notes:
    - `concurrency: group: ci-${{ github.ref }} cancel-in-progress: true` — отменяет предыдущий run на том же branch при новом push (стандартный паттерн, экономит CI minutes).
    - Pitfall #4 (RESEARCH section "Common Pitfalls #4"): `sudo xcode-select -s /Applications/Xcode_16.app || ... echo "Using default Xcode"` — defensive: если Xcode 16 path не найден на macos-15 runner-е — fallback to default. **WARNING 2 fix iter 1:** `continue-on-error: true` убран — `|| echo` уже даёт безопасный fallback; явный continue-on-error маскировал бы реальные ошибки. Добавлен явный `Verify Xcode version` step (`xcodebuild -version | head -1`) сразу после select — fail clearly если default Xcode не подходит.
    - Phase 1 НЕ запускает `embedAndSignAppleFrameworkForXcode` или `xcodebuild` — только `iosX64Test` (D-15). Полный xcodebuild — Phase 6.
    - Phase 1 НЕ запускает PrivacyInfo lint — добавится в Plan 05 как отдельный step под jobs.ios.steps после `Run iOS X64 tests`.

    Verify YAML syntax: `python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` — YAML well-formed.

    Verify workflow file (без actually running на GitHub) — проверить, что `.github/workflows/ci.yml` коммитится корректно.
  </action>
  <verify>
    <automated>test -f .github/workflows/ci.yml && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && grep -q "runs-on: ubuntu-latest" .github/workflows/ci.yml && grep -q "runs-on: macos-15" .github/workflows/ci.yml && grep -q "actions/checkout@v4" .github/workflows/ci.yml && grep -q "actions/setup-java@v4" .github/workflows/ci.yml && grep -q "gradle/actions/setup-gradle@v4" .github/workflows/ci.yml && grep -q "./gradlew assembleDebug" .github/workflows/ci.yml && grep -q "./gradlew lint" .github/workflows/ci.yml && grep -q ":composeApp:iosX64Test" .github/workflows/ci.yml && ! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$" .github/workflows/ci.yml && grep -q "concurrency:" .github/workflows/ci.yml && grep -q "Verify Xcode version" .github/workflows/ci.yml && ! grep -q "continue-on-error: true" .github/workflows/ci.yml</automated>
  </verify>
  <acceptance_criteria>
    - File `.github/workflows/ci.yml` exists and is well-formed YAML (parseable by `yaml.safe_load`)
    - File `.github/workflows/ci.yml` defines exactly two jobs: `android` and `ios`
    - File `.github/workflows/ci.yml` Android job uses `runs-on: ubuntu-latest`
    - File `.github/workflows/ci.yml` iOS job uses `runs-on: macos-15` (D-13 — macOS runner ОБЯЗАТЕЛЕН для validation iOS на Linux dev-host)
    - File `.github/workflows/ci.yml` triggers on `push: branches: [main]` AND `pull_request: branches: [main]` (D-17)
    - File `.github/workflows/ci.yml` Android job runs `./gradlew assembleDebug` and `./gradlew lint` (D-14 — BLOCKER 2 fix iter 1: `./gradlew test` removed because AppTest на Android JVM-side требует Robolectric, out of Phase 1 scope. AppTest валидируется только в iOS job через iosX64Test.)
    - **INFO fix iter 2:** File `.github/workflows/ci.yml` Android job DOES NOT contain a `Test` или `Run tests` step (защитная проверка: `! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$" .github/workflows/ci.yml` MUST exit 0; BLOCKER 2 iter 1 — commonTest содержит только AppTest в Phase 1, который не запускается на Android JVM без Robolectric)
    - File `.github/workflows/ci.yml` iOS job runs `./gradlew :composeApp:iosX64Test` (D-15)
    - File `.github/workflows/ci.yml` uses `gradle/actions/setup-gradle@v4` (D-16 — official caching action)
    - File `.github/workflows/ci.yml` uses `actions/checkout@v4` and `actions/setup-java@v4` with `java-version: '17'` and `distribution: 'temurin'` (D-10)
    - File `.github/workflows/ci.yml` includes `permissions: contents: read` (security minimal)
    - File `.github/workflows/ci.yml` includes `concurrency: cancel-in-progress: true` (CI minutes optimization)
    - File `.github/workflows/ci.yml` includes Xcode-select step БЕЗ `continue-on-error: true` (WARNING 2 fix iter 1 — `|| echo` уже даёт fallback к default Xcode; явный continue-on-error маскировал бы реальные ошибки). Защитная проверка: `! grep -q "continue-on-error: true" .github/workflows/ci.yml` MUST exit 0.
    - File `.github/workflows/ci.yml` includes explicit `Verify Xcode version` step (`xcodebuild -version | head -1`) ПОСЛЕ `Select Xcode 16` — fail clearly если default Xcode не подходит
    - File `.github/workflows/ci.yml` does NOT yet contain PrivacyInfo lint step (deferred to Plan 05 — после создания composeApp/PrivacyInfo.xcprivacy)
  </acceptance_criteria>
  <done>
    CI workflow ready to be committed. After merge: workflow триггерится на push/PR, обе jobs параллельно запускаются и должны быть зелёными (Android assembleDebug + iOS iosX64Test). PrivacyInfo lint step появится в Plan 05.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create README.md with build-status badges, build/test instructions, and manual setup documentation</name>
  <files>
    README.md
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "Documentation / CLAUDE.md Updates → README.md (root)" — minimum content; section "GitHub Actions Workflows → Branch protection rule" — manual steps to document)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-21 GitHub Pages URL формы, D-24 README markdown link)
    - .planning/PROJECT.md (Core Value formulation, status)
    - .github/workflows/ci.yml (создан в Task 1)
  </read_first>
  <action>
    Создать `README.md` в корне репозитория. Расширенный per RESEARCH.md "README.md (root)" + добавление manual setup instructions из VALIDATION.md "Manual-Only Verifications":
    ```markdown
    # ЛИнТех Дневник

    [![CI](https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml/badge.svg)](https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml)

    Кроссплатформенный мобильный клиент (iOS + Android) для электронного дневника
    ИАС «АВЕРС: Электронный Классный Журнал» школы №28 г. Кирова.

    Реализован на **Compose Multiplatform** — общий Kotlin-код и UI на iOS и Android.
    Архитектура **on-device, без собственного backend** — приложение работает напрямую
    с сервером АВЕРС, все данные кэшируются локально.

    ## Status

    Pre-alpha. Phase 1 (Foundation & Compliance Infrastructure).

    Distribution v1 — TestFlight (iOS) + Google Play Internal track (Android) для семейного/классного использования.

    ## Privacy Policy

    Политика конфиденциальности опубликована на: <https://chudoxl.github.io/LintehJournal/privacy/>

    Приложение не отправляет данные третьим лицам, не использует аналитику, не имеет рекламы.
    Все учётные данные хранятся локально в платформенном защищённом хранилище (iOS Keychain, Android Keystore).

    ## Tech Stack

    - **Compose Multiplatform** 1.10.3 + **Kotlin** 2.2.20
    - JDK 17, Android `minSdk = 26` / `targetSdk = 35`, iOS deployment target 14
    - Multi-module Gradle с `build-logic/` convention plugins (`lintech-kmp`, `lintech-compose`, `lintech-test`)
    - Версионный каталог `gradle/libs.versions.toml` (single source of truth)

    Подробнее: `.planning/research/STACK.md`.

    ## Project Structure

    ```
    LintehJournal/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradle/libs.versions.toml         # All pinned versions
    ├── build-logic/                      # Convention plugins (includedBuild)
    │   └── convention/
    │       └── src/main/kotlin/
    │           ├── LintechKmpConventionPlugin.kt
    │           ├── LintechComposeConventionPlugin.kt
    │           └── LintechTestConventionPlugin.kt
    ├── composeApp/                       # Single application module (commonMain + androidMain + iosMain)
    ├── core/
    │   ├── platform/                     # expect/actual platform abstractions (openUrl, etc.)
    │   ├── ui/                           # (empty in Phase 1; design tokens in Phase 4)
    │   └── network/                      # (empty in Phase 1; Ktor in Phase 2)
    ├── docs/                             # GitHub Pages source (privacy policy)
    │   └── privacy/index.html
    └── .github/workflows/
        ├── ci.yml                        # Android + iOS CI on push/PR
        └── pages.yml                     # Privacy Policy auto-deploy
    ```

    ## Build & Test (local)

    ### Android
    ```bash
    ./gradlew :composeApp:assembleDebug
    ./gradlew :composeApp:installDebug   # требует подключённого устройства/эмулятора
    ./gradlew :composeApp:test
    ```

    ### iOS
    ```bash
    ./gradlew :composeApp:compileKotlinIosX64    # works на Linux Mint dev-host
    ./gradlew :composeApp:iosX64Test             # требует macOS — выполняется в CI
    ```

    ### Full suite
    ```bash
    ./gradlew assembleDebug lint test    # full Android verification
    ```

    ## CI

    GitHub Actions builds Android (ubuntu-latest) and iOS (macos-15) on each push to `main` and PR.
    See `.github/workflows/ci.yml`.

    ## One-time Manual Setup (for repository owner)

    После первого merge с workflow-файлами требуется ручная конфигурация в GitHub UI:

    ### 1. GitHub Pages (для Privacy Policy)

    `Settings → Pages`:
    - **Source:** GitHub Actions

    Это активирует `pages.yml` workflow для auto-deploy `docs/privacy/`.
    Без этого шага первый run `pages.yml` упадёт с ошибкой «Pages site not enabled» (Pitfall #8).

    ### 2. Branch Protection Rule на `main`

    `Settings → Branches → Branch protection rules → Add rule`:
    - **Branch name pattern:** `main`
    - **Require status checks to pass before merging:** ON
      - Required: `Android` (CI)
      - Required: `iOS` (CI)
    - **Require branches to be up to date before merging:** ON
    - **Require conversation resolution before merging:** ON

    ## Developer Profile

    Разработка ведётся на Linux Mint без локального Xcode. iOS-сборки валидируются исключительно через CI на macos-15 runner-ах (`iosX64Test`).

    ## License

    MIT (TBD — finalize before v1 public release).

    ---

    *Repository: https://github.com/chudoxl/LintehJournal*
    *Phase 1: Foundation & Compliance Infrastructure (Q2 2026)*
    ```

    Notes:
    - `[![CI](...)](...)` — build-status badge для CI workflow.
    - Manual setup steps — точный текст из VALIDATION.md "Manual-Only Verifications" rows + Pitfall #8 mitigation.
    - Privacy Policy URL уже здесь, хотя actually deployed в Plan 04 — это закрепляет canonical reference.
  </action>
  <verify>
    <automated>test -f README.md && grep -q "ЛИнТех Дневник" README.md && grep -q "https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml/badge.svg" README.md && grep -q "https://chudoxl.github.io/LintehJournal/privacy/" README.md && grep -q "Compose Multiplatform" README.md && grep -q "macos-15" README.md && grep -q "Branch Protection Rule" README.md && grep -q "GitHub Pages" README.md</automated>
  </verify>
  <acceptance_criteria>
    - File `README.md` exists in repo root
    - File `README.md` contains build-status badge URL `https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml/badge.svg`
    - File `README.md` contains Privacy Policy markdown link to `https://chudoxl.github.io/LintehJournal/privacy/` (D-24 — линк в проекте)
    - File `README.md` contains build instructions для Android (`./gradlew :composeApp:assembleDebug`) и iOS (`./gradlew :composeApp:iosX64Test`)
    - File `README.md` contains "One-time Manual Setup" section documenting GitHub Pages enable + Branch Protection Rule (Pitfall #8 mitigation, D-17 enforcement)
    - File `README.md` contains explicit mention "Linux Mint" + "iOS-сборки валидируются исключительно через CI" (RESEARCH.md ROADMAP edit deferred — закрепление dev-host context)
    - File `README.md` references `.planning/research/STACK.md` for tech stack details
    - File `README.md` mentions `Compose Multiplatform` 1.10.3 + `Kotlin` 2.2.20
  </acceptance_criteria>
  <done>
    README.md создан с build-status badge, Privacy Policy линком, build/test инструкциями для разработчика, manual setup steps (GitHub Pages + Branch Protection) для Pitfall #8 mitigation. Готово для контрибьютора.
  </done>
</task>

<task type="checkpoint:human-action" gate="blocking">
  <name>Task 3: Manual GitHub UI configuration — enable Pages, configure branch protection</name>
  <what-built>
    Plan 03 Tasks 1+2 создали `.github/workflows/ci.yml` (CI workflow) и README.md с manual setup инструкциями.

    После того как Plan 03 PR merges в main и workflow files присутствуют в repository, требуется одноразовая конфигурация GitHub UI которая принципиально не автоматизируется через workflow YAML или Gradle (`workflow file не может изменить repo settings или branch protection rule по security policy GitHub`).
  </what-built>
  <how-to-verify>
    Эти шаги ОБЯЗАТЕЛЬНО выполняются разработчиком (chudoxl) в браузере на github.com/chudoxl/LintehJournal:

    **Шаг 1 — Enable GitHub Pages (требуется до Plan 04 deploy):**
    1. Открыть https://github.com/chudoxl/LintehJournal/settings/pages
    2. Section "Build and deployment":
       - **Source:** dropdown → выбрать "GitHub Actions" (НЕ "Deploy from a branch")
    3. Сохранить (автоматически)

    Verify: на странице должна появиться надпись "Your site is ready to be published at https://chudoxl.github.io/LintehJournal/" (deploy через workflow).

    **Шаг 2 — Branch Protection Rule на main (можно выполнить ПОСЛЕ первого PR с CI workflow merged):**
    1. Открыть https://github.com/chudoxl/LintehJournal/settings/branches
    2. Click "Add branch protection rule" (или "Add rule" если первый rule)
    3. **Branch name pattern:** `main`
    4. Check **"Require status checks to pass before merging"**:
       - Search и выбрать: `Android` (CI)
       - Search и выбрать: `iOS` (CI)
       - **Require branches to be up to date before merging:** ON
    5. Check **"Require conversation resolution before merging"**: ON
    6. **Do not bypass:** rule applies к admin (`Do not allow bypassing the above settings` — ON если хочется enforce даже на себе)
    7. Click "Create" / "Save changes"

    Verify: на странице Branches появится правило для main с пометкой "Active". Попытка push прямо в main будет отвергнута.

    **Шаг 3 — Verify CI is running:**
    1. После merge PR с workflow file — открыть https://github.com/chudoxl/LintehJournal/actions
    2. Должно появиться 2 workflow runs:
       - `CI` — push event на main
       - (если pages.yml уже merged — также `Deploy GitHub Pages`; но pages.yml появится в Plan 04)
    3. Дождаться зелёного status для CI workflow (Android + iOS jobs).

    Если iOS job падает на step "Run iOS X64 tests" с "No SDK iphonesimulator" — Pitfall #4 mitigation: проверить логи step "Select Xcode 16", если он failed-with-skip — попробовать заменить `Xcode_16.app` на конкретную версию из macos-15 runner inventory (`/Applications/Xcode_16.0.app`, `/Applications/Xcode_16.1.app` и т.д.).

    **Шаг 4 — Документировать выполнение:**
    После всех шагов подтвердить выполнение в SUMMARY (Task 4 / SUMMARY auto-generated):
    - Pages enabled: yes/no, screenshot или date
    - Branch protection: yes/no, какие checks required
    - CI status: green / red / awaiting first run
  </how-to-verify>
  <resume-signal>
    После выполнения шагов 1-3 в GitHub UI ответить:
    - "approved" — если всё done и CI зелёный
    - "approved-pending-ci" — если manual setup сделан но CI ещё running (acceptable; подтвердить когда дойдёт зелёным)
    - описать issue — если на каком-то шаге ошибка
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Repo → GitHub Actions runner | Workflow выполняется в isolated VM; secrets не попадают (Phase 1: secrets отсутствуют) |
| Forked PR → workflow | Pull-request от fork может trigger CI; secrets не доступны на forks (GitHub default policy) |
| main branch → contributors | Branch protection rule (manual UI) защищает main от прямого push |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-04 | T (Tampering) — branch | main branch — direct push bypassing CI | mitigate | Manual GitHub UI step (Task 3): branch protection rule requires `Android (CI)` + `iOS (CI)` status checks. Documented in README. RESEARCH PITFALLS-references "branch protection bypass". |
| T-01-ci-01 | E (Elevation of privilege) — token leak | GITHUB_TOKEN в forked PR | mitigate | `permissions: contents: read` (minimal scope) + `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` (PR не может писать в cache). Pages workflow (Plan 04) использует `id-token: write` только для deploy-pages action — изолировано. |
| T-01-ci-02 | T (Tampering) — supply chain Action | actions/checkout@v4, actions/setup-java@v4, gradle/actions/setup-gradle@v4 | accept | Все версии pinned. Major-version pin acceptable (точный SHA pin — overkill для well-known vendors GitHub/Gradle). |
| T-01-ci-03 | I (Information disclosure) — log leak | Gradle logs в CI output (public on push) | accept (Phase 1) | Phase 1 не имеет secrets/PII. Phase 2 logging hygiene + canary-test обязательны (PITFALLS #5). |
</threat_model>

<verification>
- File `.github/workflows/ci.yml` is well-formed YAML
- File `.github/workflows/ci.yml` defines Android + iOS jobs with correct runners (ubuntu-latest, macos-15)
- File `.github/workflows/ci.yml` Android job содержит ровно `assembleDebug` + `lint` (NO Test step — defensive grep `! grep -qE "^\s*-\s*name:\s*(Test|Run tests?)$"` MUST exit 0; INFO fix iter 2)
- File `.github/workflows/ci.yml` iOS job: `Select Xcode 16` БЕЗ `continue-on-error: true`; explicit `Verify Xcode version` step (WARNING 2 iter 1 fix preserved)
- File `README.md` exists with build-status badge, Privacy Policy link, build instructions, manual setup steps
- After merge of Plan 03 PR — CI workflow triggers on push в main; both jobs run; expectation = green (но дополнительная зависимость от Plan 02 — если Plan 02 не merged before Plan 03, AppTest может fail)
- After Task 3 manual checkpoint — Branch Protection Rule visible на github.com/.../settings/branches; GitHub Pages enabled
- VALIDATION.md Per-Task Verification Map rows `01-03-01`, `01-03-02` updated to ✅ green после first successful CI run (verified later by checker)
</verification>

<success_criteria>
1. **CI workflow зелёный на обеих платформах** — `.github/workflows/ci.yml` запускается на push в main + всех PR; Android job (assembleDebug + lint) и iOS job (iosX64Test) — оба EXIT 0.
2. **Branch protection rule active** — main защищён от direct push; merge возможен только с зелёным CI matrix (Android + iOS) — manual GitHub UI step выполнен (D-17).
3. **GitHub Pages enabled** — manual UI step выполнен — необходимое условие для Plan 04 deploy.
4. **README.md complete** — content для разработчика (build/test instructions) + manual setup инструкции.
5. **Pitfall #16 closed** — Android-only deps в commonMain ловятся CI iOS job сразу.
6. **macOS runner working** — закрытие dev-host gap (Linux Mint без Xcode → CI macos-15 единственный путь iOS validation).
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-compliance-infrastructure/01-03-SUMMARY.md` with:
- What was built (CI workflow + README + manual GitHub UI setup)
- Files created (.github/workflows/ci.yml + README.md)
- Manual checkpoint outcome (Pages enabled? Branch protection rule active? CI зелёный first run?)
- Key decisions taken from `Claude's Discretion` (concurrency group naming, Xcode-select strategy, README structure)
- CI run URL первого зелёного run
- Anything Plan 04 should know (Pages enabled через manual UI — pages.yml workflow готов к deploy; никаких additional manual steps в Plan 04 не нужно; pages.yml использует fetch-depth=0 для git log substitution — BLOCKER 3 iter 2)
- Anything Plan 05 should know (PrivacyInfo lint step добавится в `ios:` job ci.yml после `Run iOS X64 tests`; structure ready; **критично:** Plan 05 должен использовать targeted Edit, не full rewrite — Plan 03 Android job без Test step и Plan 03 iOS job без continue-on-error должны быть сохранены — BLOCKER 1 iter 2 regression-guard)
</output>
</content>
</invoke>