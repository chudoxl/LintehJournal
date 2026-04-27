# Phase 1: Foundation & Compliance Infrastructure - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Гарантированно‑воспроизводимая сборка iOS + Android из Gradle multi-module скелета через build-logic convention plugins, SwiftPM-обвязка для iOS, GitHub Actions CI обеих платформ, и compliance-стартовый набор для personal-use distribution: опубликованная Privacy Policy на GitHub Pages + iOS Privacy Manifest stub с required-reason API. Закрывает success criteria COMP-01, COMP-02 и pitfalls #15 (SwiftPM с дня 1), #16 (CI обе платформы), #9 (Privacy Manifest), частично #3 (Privacy Policy опубликована).

**В scope не входит:** реальный код фич (оценки, расписание, ДЗ — Phases 4+); auth и secure storage (Phase 3); reverse-engineering API АВЕРС (Phase 2); production signing keys и TestFlight upload (Phase 6).

</domain>

<decisions>
## Implementation Decisions

### Module Skeleton

- **D-01:** Mid-way granularity — 5 модулей в Phase 1: `:composeApp`, `:build-logic`, `:core:platform`, `:core:ui`, `:core:network`. `:core:{domain,data,database}` добавляются в Phase 3 (Auth) когда появится первый persistent-код. `:feature:*` модули — по мере появления фич.
- **D-02:** Иерархическое именование (Now in Android-style): `:core:*` / `:feature:*`. Не плоское. Соответствует ARCHITECTURE.md → "feature-by-layer multi-module".
- **D-03:** Convention plugins в `build-logic/`: `lintech-kmp` (KMP targets, source sets, jvmToolchain, freeCompilerArgs), `lintech-compose` (compose plugin + compose.runtime/material3/components.resources для UI-модулей), `lintech-test` (Mokkery + kotlin.test + Kotest assertions + Turbine, добавляются в commonTest автоматически). **НЕ включать detekt/ktlint в Phase 1** — осознанное решение, возвращаемся в Phase 4 когда появится UI-код.
- **D-04:** Version catalog в `gradle/libs.versions.toml` (стандартный single-source-of-truth Gradle 8+; renovate понимает).
- **D-05:** Единый `:composeApp` с commonMain (App() composable) + androidMain (MainActivity) + iosMain (MainViewController). Стандартный JetBrains-шаблон. iOS-интеграция через SwiftPM XCFramework — НЕ CocoaPods (closes pitfall #15).
- **D-06:** Source set targets для KMP-модулей: `android` + `iosX64` + `iosArm64` + `iosSimulatorArm64`. **iosX64 обязателен** — бесплатные GitHub macos-runners на Intel; без iosX64 success criterion #1 невыполним.
- **D-07:** Dependency rules — слои вниз: `:composeApp` → `:feature:*` → `:core:ui` → `:core:network` → `:core:platform`. Enforce через PR-review + README. Build-time enforcement (gradle-modules-graph plugin) — overkill в Phase 1, рассмотрим когда модулей станет >10.
- **D-08:** Package root — **`io.github.chudoxl.linteh.journal.*`**. Personal namespace для personal-use v1; транслитерация **«linteh»** (НЕ «lintech») соответствует имени репозитория `LintehJournal`. При v2 public submission bundle id может быть переоформлен.
- **D-09:** Sub-package naming — module-aligned: `io.github.chudoxl.linteh.journal.core.network.*`, `...core.platform.*`, `...core.ui.*`. Каждый Gradle-модуль имеет свой sub-package, совпадающий с физической структурой.
- **D-10:** JDK toolchain 17 (LTS; Android Studio Hedgehog+ default; рекомендован для KMP/Compose 1.10).
- **D-11:** Android `minSdk = 26` (Android 8.0+), `targetSdk = 35` (Android 15; Google Play требует с авг 2026 для новых публикаций; EncryptedSharedPreferences/BiometricPrompt работают от 26).
- **D-12:** iOS deployment target = 14 (минимум для Compose Multiplatform 1.10; 95%+ активных iPhone в 2026).

### CI Matrix

- **D-13:** GitHub Actions с **Linux (ubuntu-latest) + macOS (macos-15) runners**. macOS runner ОБЯЗАТЕЛЕН — dev-машина разработчика на Linux Mint, локально iOS Xcode-сборка невозможна; macOS CI = единственный путь валидации iOS-кода.
- **D-14:** Android job: `./gradlew assembleDebug lint test` (assembleDebug ловит KMP source-set ошибки; lint = Android Lint; test = commonTest + androidUnitTest).
- **D-15:** iOS job: `./gradlew iosX64Test` (запускает Compose UI Test через runComposeUiTest — валидирует, что Hello LinTech рендерится). На Phase 1 этого достаточно; embedAndSignAppleFrameworkForXcode + xcodebuild test откладываются в более поздние фазы.
- **D-16:** Кэширование: `gradle/actions/setup-gradle` (официальный action, кэширует `~/.gradle/caches` + build-cache + KMP klib-cache; важно — Kotlin/Native compile медленный).
- **D-17:** Triggers — push в main + все PR. Branch protection rule: main требует зелёного CI для merge.
- **D-18:** Signing — только debug-keystore в Phase 1 (Gradle generates default debug.keystore автоматически, secrets не нужны). Production Android keystore + Apple Developer Certificate + App Store Connect API key — отложено в Phase 6 (вместе с TestFlight upload).
- **D-19:** Screenshot/UI тесты — `runComposeUiTest { ... }` в commonTest (cross-platform для Android+iOS, использует testTag-модификаторы вместо текстовых селекторов). Paparazzi (Android-only) и Roborazzi (alpha 2026) — overkill для Phase 1.
- **D-20:** Secrets management — GitHub repo secrets (стандартный путь). Phase 1 secrets НЕ нужны (debug-only); резервируем naming convention для Phase 6: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `APPLE_API_KEY_ID`, `APPLE_API_KEY_BASE64` и т.д.

### Privacy Policy

- **D-21:** Hosting — **GitHub Pages**. URL формы `https://chudoxl.github.io/LintehJournal/privacy/`. Источник в `docs/` директории main-branch (или `gh-pages` branch — researcher выберет более удобный auto-deploy через GitHub Actions). Бесплатно, GitHub-native, переживёт переход на собственный домен в v2.
- **D-22:** Языки в v1 — **RU only**. EN добавится в v2 перед App Store submission для Apple reviewers.
- **D-23:** Содержание — custom-написанная политика ~1 страница, отражает on-device-only архитектуру из PROJECT.md: «приложение не собирает аналитику, не отправляет данные третьим лицам, общается только с journal.school28-kirov.ru, учётные данные хранятся в Keychain/Keystore локально». НЕ Termly/iubenda — generators могут включать ложные clauses про analytics. НЕ stub-плейсхолдер — нарушает COMP-01.
- **D-24:** Linkage из приложения: строка ресурсов `Res.string.privacy_policy_url` в `:composeApp/commonMain/composeResources/values/strings.xml`; Hello LinTech-экран показывает URL текстом + кнопка «Открыть» (использует D-31 `openUrl()`); в `README.md` — markdown-ссылка на политику. Удовлетворяет COMP-01 success criterion #3 «линк виден из проекта».
- **D-25:** PrivacyInfo.xcprivacy — **точно из ROADMAP success criterion #4**:
  - `NSPrivacyAccessedAPICategoryUserDefaults` reason `CA92.1` (multiplatform-settings → NSUserDefaults для non-secret prefs)
  - `NSPrivacyAccessedAPICategoryFileTimestamp` reason `C617.1` (Room/SQLite filesystem-stat для cache age)
  - `NSPrivacyTracking = false`
  - `NSPrivacyCollectedDataTypes = []` (пустой массив — мы ничего не собираем)
  - `NSPrivacyTrackingDomains = []`
  Файл размещается в `:composeApp/iosApp/Resources/PrivacyInfo.xcprivacy` (или эквивалент) и упаковывается в .ipa.
- **D-26:** CI lint Privacy Manifest на macOS-job: `plutil -lint PrivacyInfo.xcprivacy` + bash-скрипт-сверка `NSPrivacyAccessedAPITypes` содержит ожидаемые reason codes (CA92.1 + C617.1). Ловит случай когда декларацию случайно убрали.
- **D-27:** Versioning политики — git-history (commit/blame) + дата `Last-Modified` в footer HTML-страницы. БЕЗ отдельных версионных URL в v1 (privacy-v1.html). Перед v2 App Store submission — ревизия и версионирование.

### Hello LinTech Stub

- **D-28:** Объём первого экрана — **Минимум+**: текст «ЛИнТех Дневник» (брендинг), строка версии из BuildConfig, текст Privacy Policy URL, кнопка «Открыть». **Без skeleton Navigation 3** в Phase 1 — Navigation 3 на iOS пока ALPHA, не вливаемся в alpha-API ради заглушек. Navigation 3 появится в Phase 4 когда нужны реальные экраны.
- **D-29:** Источник версии в UI — **BuildKonfig плагин** (`com.codingfeline.buildkonfig`). Генерирует commonMain `object BuildKonfig { const val VERSION_NAME, VERSION_CODE, IS_DEBUG }` из gradle.properties. Один source-of-truth на обе платформы.
- **D-30:** Versioning scheme — SemVer + auto-incrementing build number:
  - `versionName` = `0.1.0` в `gradle.properties` (ручной bump перед каждой фазой; Phase 1 = `0.1.0`)
  - `versionCode` = `github.run_number` в CI / `git rev-list --count HEAD` локально (монотонный целочисленный)
  - iOS `CFBundleShortVersionString` ← `versionName`; `CFBundleVersion` ← `versionCode`
  - BuildKonfig читает из gradle.properties, единое поведение
- **D-31:** URL handling — `expect/actual openUrl(url: String): Unit` в `:core:platform`:
  - `commonMain`: `expect fun openUrl(url: String)`
  - `androidMain`: через `Intent.ACTION_VIEW` + `Intent.FLAG_ACTIVITY_NEW_TASK` (нужен Context — через CompositionLocal или DI)
  - `iosMain`: через `UIApplication.sharedApplication.openURL(NSURL.URLWithString(url))` (или openURL:options:completionHandler: для iOS 10+)
  Reusable для всех будущих внешних линков (поддержка, AVERS-сайт, etc.).

### Claude's Discretion

Researcher и planner свободны в выборе:
- Точная версия `kotlinx-datetime` (0.7.x stable vs 0.8.0-rc01) — researcher проверит совместимость с финальной serialization 1.9
- Точные KSP / Compose / Android Gradle Plugin versions — resolve через `libs.versions.toml` с проверкой совместимости (KSP=Kotlin major.minor)
- Внутренний layout build-logic-плагинов (apply-by-id «io.github.chudoxl.linteh.kmp» vs class reference; организация через extensions)
- Конкретные task-имена и порядок jobs в CI workflow YAML; matrix vs separate jobs для android/ios
- Шапка README.md, оформление Privacy Policy HTML, стилизация (если Plain HTML — inline CSS)
- Имя GitHub Pages source: `docs/` vs `gh-pages` branch
- Точный текст Privacy Policy (custom-формулировки) с условием соответствия on-device семантике из PROJECT.md
- Сценарий BuildKonfig: один общий конфиг или per-module (вероятно — один в `:composeApp`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project decisions

- `.planning/PROJECT.md` — core value, constraints, key decisions table (CMP+Kotlin tech choice, on-device, personal-use scope, biometry deferred), Out of Scope reasoning
- `.planning/REQUIREMENTS.md` §Compliance & Infrastructure (COMP-01, COMP-02) — формальные приёмочные тесты для Phase 1
- `.planning/ROADMAP.md` §Phase 1 — goal, success criteria, depends-on, closes-pitfalls
- `.planning/STATE.md` — current position, blockers (Phase 2 highest uncertainty, Phase 6 iOS BG empirical)

### Research outputs (LOCKED для Phase 1)

- `.planning/research/STACK.md` — Recommended Stack table с версиями (Kotlin 2.2.20, CMP 1.10.3, Ktor 3.4.3, Room 2.8.x KMP, Koin 4.x, AndroidX Lifecycle 2.10.0, KVault, Coil 3, Kermit). Anti-stack list (`What NOT to Use`) — обязателен к соблюдению. Version Compatibility table.
- `.planning/research/ARCHITECTURE.md` — feature-by-layer multi-module pattern; `:core:*` / `:feature:*` структура; per-account scope как архитектурный инвариант
- `.planning/research/PITFALLS.md` — #15 SwiftPM с первого дня, #16 CI обе платформы, #9 Privacy Manifest, #3 Privacy Policy/legal — все четыре в scope Phase 1
- `.planning/research/SUMMARY.md` — executive summary; Key Findings; risk ranking
- `.planning/research/FEATURES.md` — конкурентный анализ (ЭлЖур, Дневник.ру, СГО — рейтинги 2.2-2.7) — фон, не блокер для Phase 1

### External documentation (для researcher Phase 1)

- [Compose Multiplatform 1.10.3 — JetBrains](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/) — Hot Reload bundled, Navigation 3, @Preview unified
- [Compatibility and versions — Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) — Kotlin/CMP/AGP версионная матрица
- [Kotlin Multiplatform Project Setup — JetBrains](https://kotlinlang.org/docs/multiplatform-create-app.html) — стандартный шаблон `composeApp` структуры
- [Apple Privacy Manifest required reason API — Apple Developer](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api) — reason codes (CA92.1, C617.1)
- [GitHub Pages — Publishing source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site) — `docs/` vs `gh-pages`
- [BuildKonfig — codingfeline](https://github.com/yshrsmz/BuildKonfig) — KMP замена BuildConfig
- [Now in Android sample (Google) — module structure](https://github.com/android/nowinandroid) — paved-path для `:core:*` / `:feature:*` именования и build-logic convention plugins (заимствуем pattern, НЕ Android-only deps)
- [gradle/actions/setup-gradle](https://github.com/gradle/actions) — official caching action для CI

### Decision constraints из PROJECT.md (do NOT relitigate)

- **Compose Multiplatform 1.10.3 + Kotlin 2.2.20** — стек locked, не выбираем альтернативы
- **SwiftPM ONLY, не CocoaPods** — pitfall #15
- **`io.github.chudoxl.linteh.journal.*`** — package root locked в этой фазе
- **JDK 17, Android minSdk 26 / targetSdk 35, iOS 14** — locked
- **Distribution v1 = TestFlight + Google Play Internal track** — влияет на bundle id и signing в Phase 6, в Phase 1 НЕ затрагивается

</canonical_refs>

<code_context>
## Existing Code Insights

Codebase пуст — это greenfield Phase 1. Никаких reusable assets, patterns, или integration points нет. Всё, что создаётся в этой фазе, СТАНОВИТСЯ canonical pattern для всех последующих фаз.

### Patterns this phase establishes

- **Convention plugin pattern** — все будущие модули применяют один из `lintech-kmp`/`lintech-compose`/`lintech-test`. Добавление нового модуля = новая директория + 3-5 строк build.gradle.kts (success criterion #5).
- **expect/actual путь** — `openUrl()` (D-31) задаёт template для последующих platform abstractions: KVault wrappers (Phase 3), WorkManager/BGTaskScheduler (Phase 6).
- **BuildKonfig as version source** — все будущие фичи берут `BuildKonfig.VERSION_NAME` (Sentry breadcrumb tags, About-screen, AVERS UA mimic в Phase 2).
- **Compose Resources как локализационный слой** — `Res.string.*` устанавливается с Phase 1, RU-only пока. Все последующие пользовательские строки идут сюда.

### Integration points для последующих фаз

- `:core:network` появится в Phase 1 — Phase 2 (API Reverse-Engineering) добавит сюда Ktor HttpClient + AVERS endpoints
- `:core:platform` — Phase 3 положит сюда KVault wrapper, Phase 6 — WorkManager/BGTaskScheduler abstraction
- `:core:ui` — Phase 4 добавит дизайн-токены, OfflineBanner, staleness-индикаторы
- CI workflow — Phase 6 расширит matrix на release-build и TestFlight upload

</code_context>

<specifics>
## Specific Ideas

- **Транслитерация**: `linteh` (НЕ `lintech`) — соответствует имени репозитория `LintehJournal`. Закрепляется в package root, README, Privacy Policy URL slug, GitHub Pages path.
- **Linux Mint dev-host** — критическая константа: разработчик НЕ имеет локального Mac. Все iOS-валидации идут через CI, и это диктует приоритет macos-CI runner с Phase 1.
- **«ЛИнТех Дневник» с заглавными ЛТ** — официальный display name приложения. Используется в strings.xml, App Store name (когда дойдём до v2), Hello LinTech-экране. ASCII-имя для bundle id / package — `linteh` (lowercase).

</specifics>

<deferred>
## Deferred Ideas

### Корректировки ROADMAP.md, требуемые planner-у Phase 1

- **ROADMAP success criterion #2 нужно скорректировать**: «запустить заглушку «Hello LinTech» на симуляторе iPhone и Android-устройстве» → «iOS Hello LinTech валидируется через `iosX64Test` screenshot test в CI; Android — через локальный запуск на устройстве/эмуляторе разработчика». Причина: dev-host = Linux Mint, локальный Xcode невозможен. Planner должен включить этот ROADMAP-edit как явную задачу в PLAN.md.
- **Закрепить D-25 PrivacyInfo.xcprivacy список как finalized**: ROADMAP success criterion #4 уже специфичен; planner добавляет CI-проверку из D-26.

### Идеи к будущим фазам

- **Static analysis (detekt + ktlint)** — рассмотреть в Phase 4, когда появится UI-код. Сейчас на голой инфраструктуре польза минимальна.
- **Roborazzi pixel-screenshot tests** — рассмотреть в Phase 4 (UI Shell + Grades) когда появятся реальные визуальные компоненты.
- **Production signing pipeline** (Android keystore + Apple cert + App Store Connect API key + TestFlight upload) — Phase 6.
- **Cloud Mac (MacInCloud / MacStadium / scaleway)** — рассмотреть в Phase 4 для Xcode dev-loop когда SwiftUI-interop / визуальная отладка станут необходимы. Сейчас macOS CI достаточен.
- **gradle-modules-graph plugin для enforce dependency rules** — рассмотреть когда модулей >10 (вероятно после Phase 5).
- **EN-перевод Privacy Policy** — v2 перед App Store submission.
- **Privacy Policy versioning через separate URLs** — v2 перед App Store submission.
- **embedAndSignAppleFrameworkForXcode + xcodebuild test в CI** — добавить когда :composeApp обогатится реальной UI-логикой (Phase 4).
- **Renovate / Dependabot config** — рассмотреть после Phase 1 когда стек стабилизируется и появится смысл в auto-PR на bump.

### Не folded todos

None — todo backlog был пуст.

</deferred>

---

*Phase: 1-foundation-compliance-infrastructure*
*Context gathered: 2026-04-27*
