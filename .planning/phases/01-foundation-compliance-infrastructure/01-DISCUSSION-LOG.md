# Phase 1: Foundation & Compliance Infrastructure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-27
**Phase:** 1-foundation-compliance-infrastructure
**Areas discussed:** Module skeleton, CI matrix, Privacy Policy, Hello LinTech stub

---

## Module Skeleton

### Granularity (number of modules)

| Option | Description | Selected |
|--------|-------------|----------|
| Full skeleton (~10 modules) | `:composeApp + :build-logic + :core:{domain,data,network,database,platform,ui} + :feature:foundation` validation stub | |
| Mid-way (~5 modules) | `:composeApp + :build-logic + :core:{platform,ui,network}`; добавляем domain/data/database в Phase 3 | ✓ |
| Minimum (~3 modules) | `:composeApp + :build-logic + :core:platform`; YAGNI | |

**User's choice:** Mid-way. Покрывает Hello World и Phase 2 reverse-engineering без преждевременного создания пустых build-файлов.

### Naming convention

| Option | Description | Selected |
|--------|-------------|----------|
| `:core:network`, `:feature:auth` | Иерархическое, NowInAndroid-style | ✓ |
| `:network`, `:auth` | Плоское | |
| Ты решишь | Claude default | |

**User's choice:** Hierarchical `:core:*` / `:feature:*`.

### Convention plugins (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| KMP база | `kotlin-multiplatform`, targets, source sets, jvmToolchain | ✓ |
| Compose Multiplatform | compose plugin + runtime/material3/components.resources | ✓ |
| Static analysis (detekt + ktlint) | Detekt + ktlint-gradle с fail-on-warning | |
| Test conventions (Mokkery + kotlin.test + Kotest) | testing-deps в commonTest автоматически | ✓ |

**User's choice:** KMP + Compose + Test. Static analysis НЕ выбран — осознанное решение, рассмотрим в Phase 4.

### Version Catalog location

| Option | Description | Selected |
|--------|-------------|----------|
| `gradle/libs.versions.toml` | Стандартный Gradle 8+ путь | ✓ |
| `build-logic/libs.versions.toml` | Внутри included build | |

**User's choice:** Standard `gradle/libs.versions.toml`.

### Entry-points organization

| Option | Description | Selected |
|--------|-------------|----------|
| Единый `:composeApp` | commonMain App() + androidMain MainActivity + iosMain MainViewController | ✓ |
| `:composeApp` + отдельный Xcode iosApp/ | Больше свободы для SwiftUI-обёрток, больше boilerplate | |

**User's choice:** Единый `:composeApp` (стандартный JetBrains-шаблон).

### Source set targets

| Option | Description | Selected |
|--------|-------------|----------|
| `android + iosX64 + iosArm64 + iosSimulatorArm64` | Включая Intel-симулятор для CI | ✓ |
| Только arm64 (без iosX64) | Быстрее, но не работает на бесплатных GitHub macos-runners | |
| + JVM (desktop) | Быстрые тесты, но overkill | |

**User's choice:** Полный набор включая iosX64 — критично для CI на бесплатных Intel macos-runners.

### Dependency rules

| Option | Description | Selected |
|--------|-------------|----------|
| Слои вниз (PR-review enforce) | `:composeApp → :feature:* → :core:ui → :core:network → :core:platform` | ✓ |
| Module-graph plugin | Build-time enforcement | |
| Без явных правил | Риск circular deps | |

**User's choice:** Слои вниз через PR-review.

### Package naming

| Option | Description | Selected |
|--------|-------------|----------|
| `ru.school28.lintech.*` | Школа-специфичный | |
| `ru.lintech.journal.*` | Нейтральный | |
| `io.github.<handle>.lintech.*` | Personal namespace | |
| Ты решишь | Claude default | |
| **Custom (Other)** | `io.github.chudoxl.linteh.journal.*` | ✓ |

**User's choice:** Custom — `io.github.chudoxl.linteh.journal.*`. **Заметка:** транслитерация «linteh» (не «lintech»), соответствует имени репозитория `LintehJournal`.

### JDK toolchain

| Option | Description | Selected |
|--------|-------------|----------|
| JDK 17 | LTS, Android Studio default, KMP-проверено | ✓ |
| JDK 21 | Свежее LTS | |

**User's choice:** JDK 17.

### Android SDK

| Option | Description | Selected |
|--------|-------------|----------|
| `minSdk 26, targetSdk 35` | Android 8.0+, Google Play 2026 | ✓ |
| `minSdk 24, targetSdk 35` | Android 7.0+, шире охват | |
| `minSdk 28, targetSdk 35` | Android 9.0+, отрезает много старых телефонов | |

**User's choice:** `minSdk 26, targetSdk 35`.

### iOS deployment target

| Option | Description | Selected |
|--------|-------------|----------|
| iOS 14 | Минимум для CMP 1.10 | ✓ |
| iOS 15 | Срезает iPhone 6s | |
| iOS 16 | Срезает iPhone 7/8 | |

**User's choice:** iOS 14.

### Sub-package naming

| Option | Description | Selected |
|--------|-------------|----------|
| Module-aligned | `io.github.chudoxl.linteh.journal.core.network.*` | ✓ |
| Flat | `io.github.chudoxl.linteh.journal.network.*` | |

**User's choice:** Module-aligned.

---

## CI Matrix

### CI provider and macOS runners

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub Actions, Linux + macOS | ubuntu-latest для Android, macos-15 для iOS | |
| GitHub Actions, Android-only в Phase 1 | macOS runner добавим позже | (initial) |
| Self-hosted Mac | Apple Silicon как runner | |
| Bitrise / EAS | Mobile-specialized CI | |

**User's choice:** Initial = Android-only. После уточнения о dev-host (Linux Mint, локально iOS невозможен) → переменился на «добавить iosX64Test на macos-runner».

### CI jobs (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| Android: assembleDebug + lint + test | Обязательный минимум | ✓ |
| iOS: iosX64Test | Прямое требование success criterion #1 | ✓ (after re-decision) |
| iOS: embedAndSignAppleFrameworkForXcode | XCFramework для Xcode-интеграции | (deferred) |
| iOS: xcodebuild test | Реальный iOS-build в симуляторе | (deferred) |

**User's choice:** Android jobs всегда; iOS iosX64Test добавлен в финальном решении.

### Caching strategy

| Option | Description | Selected |
|--------|-------------|----------|
| `gradle/actions/setup-gradle` | Официальный action от Gradle Inc. | ✓ |
| `actions/cache` вручную | Больше контроля | |
| Без кэша | Простота, но медленно | |

**User's choice:** `gradle/actions/setup-gradle`.

### Triggers and branch protection

| Option | Description | Selected |
|--------|-------------|----------|
| На push в main + PR | Branch protection green CI required | ✓ |
| Только на PR | main без CI | |
| main + PR + cron weekly | + еженедельный для deps-drift | |

**User's choice:** Push + PR + branch protection.

### Resolution: Android-only ↔ ROADMAP success criterion #1

После того как Claude указал на конфликт с ROADMAP («обе платформы зелёные на каждом коммите») и на провал closes pitfall #16 (Android-only deps в commonMain не отлавливаются):

| Option | Description | Selected |
|--------|-------------|----------|
| Добавить iosX64Test на macOS-runner | Стандартное решение, держим success criterion | ✓ |
| Android-only + обновить ROADMAP | Открыто отказаться от criterion | |
| Android-only public + локальный iOS pre-commit | Невозможно на Linux dev-host | |
| iOS CI в weekly cron | Компромисс между weekly и per-commit | |

**User's choice:** macOS runner добавлен.

### Dev-host

| Option | Description | Selected |
|--------|-------------|----------|
| Linux (Pop!_OS / Ubuntu) | Linux platform | |
| Mac (Apple Silicon) | Локальный iOS-build возможен | |
| Mac (Intel) | Медленный iOS-build | |
| Linux + Mac | Оба | |
| **Custom (Other)** | Linux Mint | ✓ |

**User's choice:** Linux Mint. Подтверждает: macOS CI runner — единственный путь iOS-валидации.

### iOS Hello World validation без локального Mac

| Option | Description | Selected |
|--------|-------------|----------|
| macOS CI runner — единственный iOS-build путь | iosX64Test screenshot достаточен для Phase 1 | ✓ |
| Cloud Mac (MacInCloud / MacStadium) | Платный удалённый Xcode | (deferred to Phase 4) |
| GitHub Codespaces с macOS | Не поддерживается | |
| Будет Mac позже | iOS validation отложена до приобретения Mac | |

**User's choice:** macOS CI runner.

### Signing

| Option | Description | Selected |
|--------|-------------|----------|
| Только debug в Phase 1, release в Phase 6 | Apple cert + production keystore позже | ✓ |
| Debug + production keystore сразу | Apple Developer Program enrollment сразу | |
| Debug + Android keystore (no Apple) | Mid-way | |

**User's choice:** Debug-only в Phase 1.

### Screenshot tests in Phase 1

| Option | Description | Selected |
|--------|-------------|----------|
| Compose UI Test в commonTest | runComposeUiTest cross-platform | ✓ |
| Paparazzi (Android only) | Pixel-perfect, но не KMP | |
| Roborazzi (KMP screenshot) | Alpha-статус 2026 | |
| Без screenshot-тестов | Минимум кода | |

**User's choice:** runComposeUiTest.

### Secrets management

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub repo secrets | Стандартный путь | ✓ |
| 1Password CLI | Overkill для соло | |
| Не решаем в Phase 1 | Отложить до Phase 6 | |

**User's choice:** GitHub repo secrets (резервируем naming convention; в Phase 1 secrets не используются).

---

## Privacy Policy

### Hosting

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub Pages | Бесплатно, GitHub-native | ✓ |
| Notion / Telegraph public page | Без git-history | |
| Свой домен / VPS | Operational cost | |

**User's choice:** GitHub Pages.

### Languages

| Option | Description | Selected |
|--------|-------------|----------|
| RU only в v1 | EN добавим в v2 | ✓ |
| RU + EN сразу | Подготовка к v2 | |

**User's choice:** RU only.

### Template / content

| Option | Description | Selected |
|--------|-------------|----------|
| Custom пишем с нуля | Отражает on-device-only архитектуру | ✓ |
| Termly / iubenda generator | Может включать ложные clauses | |
| GDPR-template для opensource | Backend-ориентированные clauses | |
| Stub в Phase 1, полный в Phase 6 | Нарушает success criterion #3 | |

**User's choice:** Custom.

### Linkage in app

| Option | Description | Selected |
|--------|-------------|----------|
| Строка ресурсов + README | Res.string.privacy_policy_url + Hello LinTech text | ✓ |
| + deep-link button | Кнопка «Открыть» с openUrl | (D-31 reuse в той же фазе) |

**User's choice:** Resources + README + clickable URL (объединено с D-31 openUrl, см. Hello stub).

### PrivacyInfo.xcprivacy scope

| Option | Description | Selected |
|--------|-------------|----------|
| Ровно из ROADMAP success criterion #4 | CA92.1 + C617.1 + Tracking=false + collected=[] | ✓ |
| + DiskSpace + SystemBootTime | На всякий случай | |
| Минимум: только NSPrivacyTracking + пустые массивы | Нарушает ROADMAP | |

**User's choice:** Точно по ROADMAP.

### CI lint Privacy Manifest

| Option | Description | Selected |
|--------|-------------|----------|
| plutil + сверка списка | Полная валидация | ✓ |
| Только plutil -lint | Без сверки списка | |
| Без lint | Сборка fail-нет при поломанном plist | |

**User's choice:** Полная валидация.

### Policy versioning

| Option | Description | Selected |
|--------|-------------|----------|
| Git-history + Last-Modified в footer | Простота | ✓ |
| Semver в имени файла | Старые версии живут отдельно | |
| Не версионируем в v1 | Полностью текущая | |

**User's choice:** Git-history + Last-Modified footer.

---

## Hello LinTech Stub

### Stub scope

| Option | Description | Selected |
|--------|-------------|----------|
| Минимум+ (бренд + версия + privacy URL) | Один экран валидирует Resources + BuildConfig + URL handling | ✓ |
| Pure «Hello LinTech» | Минимум кода | |
| Skeleton Navigation 3 (вкладки) | Navigation 3 на iOS = ALPHA | |

**User's choice:** Минимум+.

### Version source on screen

| Option | Description | Selected |
|--------|-------------|----------|
| BuildKonfig плагин | Single source-of-truth для commonMain | ✓ |
| expect/actual val APP_VERSION | Свои руками | |
| Hardcode в commonMain | Risk of desync | |

**User's choice:** BuildKonfig.

### Versioning scheme

| Option | Description | Selected |
|--------|-------------|----------|
| SemVer + auto-incrementing build number | versionName ручной, versionCode = github.run_number | ✓ |
| Git-tag based | Overkill в Phase 1 | |
| 0.0.x линейка | Шумно | |

**User's choice:** SemVer + auto-build.

### URL handling

| Option | Description | Selected |
|--------|-------------|----------|
| expect/actual openUrl(url) | Reusable для всех будущих линков | ✓ |
| ClickableText без выхода в браузер | Хуже UX | |

**User's choice:** expect/actual openUrl. **Также** становится первым `:core:platform` paved-path для последующих abstractions (KVault, BG-tasks).

---

## Claude's Discretion

(See `01-CONTEXT.md` → `<decisions>` → `### Claude's Discretion`)

- Точная версия kotlinx-datetime
- Точные KSP/Compose/AGP versions
- Внутренний layout build-logic-плагинов
- CI workflow YAML структура (matrix vs separate jobs)
- Шапка README, оформление Privacy Policy HTML
- GitHub Pages source: `docs/` vs `gh-pages` branch
- Точный текст Privacy Policy
- BuildKonfig — один общий или per-module

## Deferred Ideas

- ROADMAP success criterion #2 нужно скорректировать (Linux dev-host не имеет Xcode)
- Static analysis (detekt + ktlint) — Phase 4
- Roborazzi pixel-screenshot — Phase 4
- Production signing pipeline (keystore + Apple cert + TestFlight upload) — Phase 6
- Cloud Mac (MacInCloud/MacStadium) — Phase 4 для Xcode dev-loop
- gradle-modules-graph plugin — после Phase 5 (когда модулей >10)
- EN-перевод Privacy Policy — v2
- Privacy Policy versioning через separate URLs — v2
- embedAndSignAppleFrameworkForXcode + xcodebuild test в CI — Phase 4
- Renovate / Dependabot config — после Phase 1

---

*Discussion completed: 2026-04-27*
