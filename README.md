# ЛИнТех Дневник

[![CI](https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml/badge.svg)](https://github.com/chudoxl/LintehJournal/actions/workflows/ci.yml)

Кроссплатформенный мобильный клиент (iOS + Android) для электронного дневника
ИАС «АВЕРС: Электронный Классный Журнал» школы №28 г. Кирова.

Реализован на **Compose Multiplatform** — общий Kotlin-код и UI на iOS и Android.
Архитектура **on-device, без собственного backend** — приложение работает напрямую
с сервером АВЕРС, все данные кэшируются локально.

## Status

**Phase 1 (Foundation & Compliance Infrastructure): COMPLETE**

- Multi-module Gradle skeleton (`:composeApp` + `:core:platform` / `:core:ui` / `:core:network` + `build-logic/`)
- Convention plugins (`lintech-kmp`, `lintech-compose`, `lintech-test`) — adding new module = ≤5 строк plugins-блока
- GitHub Actions CI: Android (ubuntu-latest) + iOS (macos-15) на каждый push/PR
- Privacy Policy опубликована: <https://chudoxl.github.io/LintehJournal/privacy/>
- iOS `PrivacyInfo.xcprivacy` с required-reason API + CI lint regression-protect
- Hello LinTech composable как первый working экран (Android-side runs локально; iOS-side validates через CI `iosX64Test`)

Distribution v1 — TestFlight (iOS) + Google Play Internal track (Android) для семейного/классного использования. Public submission и formal legal compliance — отложены в v2.

**Next: Phase 2 — API Reverse-Engineering & Network Layer.**

## Privacy Policy

Политика конфиденциальности опубликована на: <https://chudoxl.github.io/LintehJournal/privacy/>

Приложение не отправляет данные третьим лицам, не использует аналитику, не имеет рекламы.
Все учётные данные хранятся локально в платформенном защищённом хранилище (iOS Keychain, Android Keystore).

iOS-сборка содержит `PrivacyInfo.xcprivacy` с required-reason API declarations (`NSPrivacyAccessedAPICategoryUserDefaults` CA92.1, `NSPrivacyAccessedAPICategoryFileTimestamp` C617.1), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` — упаковывается автоматически через `org.jetbrains.kotlin.apple-privacy-manifests:1.0.0` plugin. CI lint (`plutil -lint` + grep на macos-15) предотвращает регрессии.

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
