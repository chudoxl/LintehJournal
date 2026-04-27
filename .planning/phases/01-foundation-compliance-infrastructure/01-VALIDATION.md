---
phase: 1
slug: foundation-compliance-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-27
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + Compose UI Test (`runComposeUiTest`) + Mokkery + Turbine + Kotest assertions |
| **Config file** | `gradle/libs.versions.toml`, `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt` (Wave 0 устанавливает) |
| **Quick run command** | `./gradlew :composeApp:commonTest --rerun-tasks` |
| **Full suite command** | `./gradlew assembleDebug lint test iosX64Test` |
| **Estimated runtime** | ~120-180 секунд (cold cache); ~30-60 секунд (warm Gradle cache) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew compileDebugKotlinAndroid compileKotlinIosX64` (smoke compile проверка)
- **After every plan wave:** Run `./gradlew assembleDebug iosX64Test`
- **Before `/gsd-verify-work`:** Full suite must be green локально + GitHub Actions matrix зелёный (ubuntu + macos)
- **Max feedback latency:** ~60 секунд для smoke-compile, ~180 секунд для full wave-проверки

---

## Per-Task Verification Map

> Заполняется planner-ом при создании PLAN.md. Каждая задача либо имеет автоматизированную команду, либо помечена `[manual]` с явным обоснованием.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-XX-XX | TBD | TBD | COMP-01 / COMP-02 | TBD | TBD | TBD | TBD | TBD | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Phase 1 — greenfield, никакой test-infrastructure ещё нет. Wave 0 (init/skeleton) обязан установить:

- [ ] `gradle/libs.versions.toml` — все версии (Kotlin 2.2.20, KSP 2.2.20-2.0.4, CMP 1.10.3, AGP, kotlinx-*, Mokkery, Turbine, Kotest)
- [ ] `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt` — добавляет `kotlin.test`, Mokkery, Turbine, Kotest assertions в commonTest всех KMP-модулей
- [ ] `composeApp/src/commonTest/kotlin/.../AppTest.kt` — minimum smoke-test через `runComposeUiTest` для Hello LinTech composable (testTag: "hello-linteh-screen", "open-privacy-button")
- [ ] `composeApp/src/androidUnitTest/kotlin/` — placeholder директория, чтобы `androidUnitTest` task существовал
- [ ] CI workflows (Wave 2) запускают `./gradlew assembleDebug lint test` (Android) + `./gradlew iosX64Test` (iOS) — смыкание test-infrastructure с CI matrix

---

## Manual-Only Verifications

Phase 1 содержит несколько behaviour, которые принципиально не автоматизируются на CI (требуют локального окружения / визуальной верификации):

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Hello LinTech запускается на Android-устройстве разработчика | Success Criterion #2 (адаптировано) | Локальный Android-эмулятор / устройство; CI не имеет UI-display | 1. `./gradlew :composeApp:installDebug` 2. Открыть приложение на устройстве 3. Убедиться: видны «ЛИнТех Дневник», версия, Privacy URL, кнопка «Открыть» 4. Тапнуть кнопку → открывается браузер на `https://chudoxl.github.io/LintehJournal/privacy/` |
| Privacy Policy доступна по URL | Success Criterion #3 (COMP-01) | Сетевой ресурс — проверка после деплоя | `curl -I https://chudoxl.github.io/LintehJournal/privacy/` → HTTP/2 200; контент-тип `text/html`; страница содержит «ЛИнТех Дневник» и абзац о on-device хранении |
| Xcode «Validate App» не выдаёт ITMS-91053 | Success Criterion #4 (COMP-02) | Требует Xcode, отсутствует на dev-host (Linux Mint); проверится на macOS-CI или при первой TestFlight upload (Phase 6) | В Phase 1 manually verified через CI lint: `plutil -lint PrivacyInfo.xcprivacy` + grep `CA92.1` + `C617.1`. Полная Xcode validation — Phase 6 |
| Convention plugin add-module path ≤5 строк | Success Criterion #5 | Требует ручной проверки developer experience | Создать temporary `:core:fake` модуль; `build.gradle.kts` должен содержать `plugins { id("lintech.kmp") }` + минимум зависимостей; запустить `./gradlew :core:fake:build` — должен пройти. После проверки удалить модуль. |
| GitHub branch protection rule: main требует зелёного CI | D-17 (CONTEXT.md) | GitHub UI configuration, не коммитится в репо | Settings → Branches → Branch protection rules → main → Require status checks (android-build, ios-build) |
| GitHub Pages enabled в настройках репозитория | D-21 (CONTEXT.md) | GitHub UI / API configuration | Settings → Pages → Source: «GitHub Actions» (рекомендованный workflow `actions/deploy-pages`) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify или явно помечены `[manual]` с причиной
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (libs.versions.toml + convention plugins + первый AppTest)
- [ ] No watch-mode flags (никаких `--continuous`, `gradle --watch-fs` в CI/test command)
- [ ] Feedback latency < 180 секунд (full wave-проверка)
- [ ] `nyquist_compliant: true` set in frontmatter после approval planner-ом

**Approval:** pending
