# Roadmap: ЛИнТех Дневник

## Overview

Compose Multiplatform мобильный клиент к ИАС АВЕРС (закрытый ExtJS-API школы №28 г. Кирова) собирается снизу вверх по слоям: фундамент проекта и compliance‑инфраструктура → реверс закрытого API АВЕРС (зона наивысшей неопределённости) → защищённая авторизация → первая end‑to‑end вертикаль (UI shell + оценки + offline + UX‑полировка) → активация мульти‑аккаунта и остальных вертикалей (расписание, ДЗ, посещаемость, сообщения) → фоновая синхронизация и локальные уведомления. v1 — личное/семейное использование через TestFlight + Google Play Internal track; публичный submission и формальная регистрация (РКН, согласие школы) перенесены в v2.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation & Compliance Infrastructure** — Gradle multi-module skeleton, convention plugins, SwiftPM, CI обе платформы, Privacy Manifest stub, Privacy Policy опубликована
- [ ] **Phase 2: API Reverse-Engineering & Network Layer** — mitmproxy/HAR-захват закрытого ExtJS-API АВЕРС, Ktor per-account клиент с persistent cookies, versioned API-модуль (HIGHEST uncertainty)
- [ ] **Phase 3: Auth & Secure Credential Storage** — login/password АВЕРС, Keychain/Keystore через KVault с защитой от iCloud-утечки, logout с очисткой данных
- [ ] **Phase 4: UI Shell, Grades & Offline Foundation** — core:ui + composeApp + Navigation 3, оценки end-to-end, offline-first паттерн, staleness indicators, тёмная тема, локализация RU, accessibility, прогноз и график
- [ ] **Phase 5: Multi-Account, Schedule, Homework, Attendance & Messages** — per-account scope активирован, оставшиеся вертикали копируют паттерн grades
- [ ] **Phase 6: Background Sync & Local Notifications** — WorkManager + BGAppRefreshTask + Alarmee, diff detector, локальные уведомления с честным UX-копирайтом и default-obfuscated lock screen

## Phase Details

### Phase 1: Foundation & Compliance Infrastructure
**Goal**: Гарантированно‑воспроизводимая сборка обеих платформ, готовая инфраструктура для всего последующего кода и опубликованные compliance-артефакты для personal-use distribution
**Depends on**: Nothing (first phase)
**Requirements**: COMP-01, COMP-02
**Success Criteria** (what must be TRUE):
  1. CI собирает iOS и Android из коробки (assembleDebug + iosX64Test) — обе платформы зелёные на каждом коммите
  2. Разработчик может открыть проект в Android Studio и запустить заглушку «Hello LinTech» на Android-устройстве/эмуляторе. iOS «Hello LinTech» валидируется через автоматический `iosX64Test` screenshot-test в CI на macos-15 runner-е (dev-host разработчика — Linux Mint, локальный Xcode недоступен)
  3. Privacy Policy опубликована на отдельном URL (GitHub Pages) и линк виден из проекта (README + строка ресурсов для будущего «О приложении»)
  4. iOS-сборка содержит `PrivacyInfo.xcprivacy` с required-reason API (`NSPrivacyAccessedAPICategoryUserDefaults` CA92.1, `NSPrivacyAccessedAPICategoryFileTimestamp` C617.1), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` — Xcode «Validate App» не выдаёт ITMS-91053; CI lint (`plutil -lint` + grep CA92.1, C617.1, NSPrivacyTracking=false) на macos-job не выдаёт ошибок на каждом коммите
  5. Convention plugins (`build-logic/`) применяются к фейковому модулю — добавление нового KMP-модуля займёт ≤5 строк build.gradle.kts
**Plans:** 6 plans

Plans:
- [x] 01-01-skeleton-PLAN.md — Gradle wrapper + version catalog + build-logic convention plugins + 4 KMP-module skeletons (completed 2026-04-28)
- [x] 01-02-hello-linteh-PLAN.md — expect/actual openUrl + BuildKonfig + Hello LinTech composable + AppTest (completed 2026-04-28)
- [x] 01-03-ci-workflows-PLAN.md — GitHub Actions CI (Android ubuntu-latest + iOS macos-15) + README + manual GitHub UI setup (completed 2026-04-28; Task 3 manual UI deferred — tracked in HUMAN-UAT)
- [x] 01-04-privacy-policy-PLAN.md — Privacy Policy HTML + GitHub Pages auto-deploy (completed 2026-04-28; Task 3 live URL check deferred — combined into HUMAN-UAT)
- [x] 01-05-privacy-manifest-PLAN.md — PrivacyInfo.xcprivacy + apple-privacy-manifests plugin + CI plutil-lint step (completed 2026-04-28)
- [x] 01-06-docs-PLAN.md — ROADMAP edit (Linux Mint dev-host) + CLAUDE.md fill + README finalize (completed 2026-04-28)

**Closes pitfalls:** #15 (SwiftPM с первого дня — не CocoaPods), #16 (CI собирает обе платформы — Android-only deps в commonMain ловятся сразу), #9 (Privacy Manifest заглушка), частично #3 (Privacy Policy опубликована — снимает privacy-ветку юр. риска для personal-use scope)

### Phase 2: API Reverse-Engineering & Network Layer
**Goal**: Задокументированный contract закрытого ExtJS-API АВЕРС и работающий Ktor-стек, который умеет логиниться и тянуть JSON, оставаясь устойчивым к обновлениям вендора
**Depends on**: Phase 1
**Requirements**: (нет — infrastructure для всех последующих фаз; самая высокая неопределённость в проекте — закрытый ExtJS-API без публичной документации)
**Success Criteria** (what must be TRUE):
  1. В репозитории лежат HAR-snapshots реальных запросов АВЕРС (login, оценки, расписание, ДЗ, посещаемость, сообщения) — захваченные через **Chrome DevTools** для `journal.school28-kirov.ru` (D-02 correction: dev-host = Linux Mint; Chrome DevTools HAR-export проще mitmproxy, mobile UA divergence риск задокументирован и митигируется параметризованным UA в `HttpClientFactory` + первым реальным Android-run в Phase 4)
  2. Документ `aversApiV4_23813.md` описывает: login flow (cookie/CSRF), формат ответов (ExtJS `{success, data}` или прямой), endpoint-карту, anti-bot пороги, выяснено отдаются ли «замены», прикреплённые файлы ДЗ и веса оценок
  3. `HttpClientFactory.forAccount(id)` возвращает Ktor-клиент c persistent cookies в Room-таблице, UA mimic Mobile Safari, throttling и retry — повторяет реальный login против тестового аккаунта без срабатывания CAPTCHA
  4. Canary-endpoint при старте приложения сравнивает ответ с эталоном; remote kill-switch (статический JSON на CDN) умеет показать пользователю баннер «обновите приложение»
  5. Логирование запросов — `LogLevel.NONE` в release, `sanitizeHeader` для Authorization/Cookie, canary-test «kanareyka_PASSWORD_DO_NOT_LEAK_42» в CI не находит совпадений в логах
**Plans:** 9 plans

Plans:

**Wave 1** *(parallelisable; Plan 02 is `autonomous: false` — manual HAR capture)*
- [x] 02-01-PLAN.md — Gradle deps + `:core:database`+`:core:api-avers-v4` skeletons + sanitize-har tooling + log-redactor canary + ROADMAP edit (mitmproxy → Chrome DevTools)
- [ ] 02-02-PLAN.md — HAR captures (account-A + account-B × 6 endpoints, 12 sanitized fixtures)

**Wave 2** *(blocked on Wave 1 completion)*
- [ ] 02-03-PLAN.md — Room JournalDatabase + DatabaseFactory + Cookie schema v1 + iOS NSFileProtectionComplete
- [ ] 02-04-PLAN.md — HttpClientFactory + plugin chain + HttpRequestRedactor + AversAuthInterceptor + CredentialProvider

**Wave 3** *(blocked on Wave 2)*
- [ ] 02-05-PLAN.md — RoomCookiesStorage + CookieMapper + AccountDataPurger

**Wave 4** *(blocked on Wave 3)*
- [ ] 02-06-PLAN.md — `:core:api-avers-v4` DTOs + ApiResult + AversApiError + 6-endpoint contract tests via HAR replay

**Wave 5** *(parallelisable; both blocked on Wave 4)*
- [ ] 02-07-PLAN.md — KillSwitchClient + docs/api-config.json (GitHub Pages-deployed)
- [ ] 02-09-PLAN.md — CI iOS test invocations (`:core:database/network/api-avers-v4:iosX64Test`) + canary scripts wired + iOS Native HAR resource loading

**Wave 6** *(blocked on Waves 4 & 5)*
- [ ] 02-08-PLAN.md — docs/aversApiV4_23813.md (API contract narrative) + changelog + tools/manual-smoke.sh

**Cross-cutting constraints** (truths appearing in 2+ plans — executor MUST preserve across waves):
- HttpRequestRedactor canary `kanareyka_PASSWORD_DO_NOT_LEAK_42` greps clean in BOTH debug and release builds (D-28; introduced in 02-01, validated in 02-04 / 02-09)
- Single source of truth versions in `gradle/libs.versions.toml` — no version literals in module `build.gradle.kts` (D-02; entrenched 02-01, respected by 02-03..09)
- Per-account scope invariant — `journal_${accountId}.db` filename pattern + cookies scoped per-account (D-15..18; created 02-03, consumed 02-04 / 02-05)
- `core/api-avers-v4/build.gradle.kts` — `fixtures.dir` system property must be passed to ALL Test tasks (Android JVM AND iOS Native); landed in 02-06, extended to iOS in 02-09

**Closes pitfalls:** #2 (API fragility — versioned `aversApiV4_23813` модуль + canary + remote kill-switch + HAR-snapshot тесты), #11 (anti-bot — UA mimic, throttling, retry-after, WebView fallback за feature flag), #19 (SSL — system trust, no pinning), #5 (logging hygiene — sanitizeHeader + redactor + canary-test)

**Research flag:** HIGHEST — закрытый ExtJS-API не документирован, всё в этой фазе — гипотезы до момента mitmproxy-захвата

### Phase 3: Auth & Secure Credential Storage
**Goal**: Пользователь может безопасно войти в свой АВЕРС-аккаунт; чужие учётные данные защищены от утечки через iCloud sync, логи и crash reports
**Depends on**: Phase 2
**Requirements**: AUTH-01, AUTH-02, AUTH-03
**Success Criteria** (what must be TRUE):
  1. Пользователь вводит логин/пароль АВЕРС и оказывается в приложении в авторизованном состоянии за ≤5 секунд при стабильной сети
  2. Учётные данные сохраняются в платформенном защищённом хранилище — на iOS unit-test проверяет `kSecAttrSynchronizable=false` и `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` в attributes-словаре; на Android — EncryptedSharedPreferences через MasterKey
  3. После рестарта приложения пользователь автоматически авторизован через сохранённые cookies — без повторного ввода пароля
  4. Кнопка «Выйти» удаляет учётные данные из Keychain/Keystore, cookies из Room и весь локальный кэш аккаунта; повторный запуск показывает экран логина
  5. Canary-test «kanareyka_PASSWORD_DO_NOT_LEAK_42» на CI прогоняет full login flow — пароль не появляется ни в одном Sentry breadcrumb, Logcat-выводе или crash report
**Plans**: TBD
**UI hint**: yes

**Closes pitfalls:** #1 (Keychain iCloud sync — unit-test на synchronizable=false), #5 (logging hygiene — canary-test обязателен перед merge); биометрия в v1 OUT (см. PROJECT.md), fallback на системный пароль устройства

### Phase 4: UI Shell, Grades & Offline Foundation
**Goal**: Пользователь может открыть приложение и работать с оценками — текущими, итоговыми, прогнозом, графиком — даже без сети, с очевидным индикатором свежести данных и доступностью для людей с ограниченными возможностями
**Depends on**: Phase 3
**Requirements**: GRAD-01, GRAD-02, GRAD-03, GRAD-04, GRAD-05, OFFL-01, OFFL-02, OFFL-03, OFFL-04, UI-01, UI-02, UI-03, UI-04, ANALYT-01, ANALYT-02
**Success Criteria** (what must be TRUE):
  1. Пользователь видит экран оценок с текущими, итоговыми, средним баллом (вычисленным на устройстве) и историей по периодам; новые оценки помечены индикатором, оценки имеют цветовое кодирование
  2. Все экраны открываются без сети — данные подгружаются из Room-кэша; на каждом экране виден возраст данных («обновлено 2 ч назад»); >24ч жёлтая плашка, >72ч красная; OfflineBanner появляется при потере сети
  3. Pull-to-refresh на каждом экране запускает явное обновление с визуальной индикацией прогресса; экран остаётся interactive со старыми данными во время refresh
  4. Приложение следует системной тёмной/светлой теме автоматически; все строки UI на русском; VoiceOver и TalkBack озвучивают все интерактивные элементы; iOS Dynamic Type масштабирует текст без обрезаний таблиц оценок
  5. Пользователь видит прогноз итоговой оценки за период (с дисклеймером, если веса оценок недоступны от АВЕРС) и график динамики среднего балла; в настройках можно включить шрифт для дислексиков
**Plans**: TBD
**UI hint**: yes

**Closes pitfalls:** #7 (iOS swipe-back ADR принят рано — выбран один из custom Compose / UIKit-wrap / Appyx), #8 (LazyColumn nested scroll — структурно избегаем nested LazyColumn в HorizontalPager, тестируем на физическом iPhone), #13 (cache staleness — visible indicator в первой же фиче), #14 (Kotlin/Swift interop — SKIE если потребуется)

### Phase 5: Multi-Account, Schedule, Homework, Attendance & Messages
**Goal**: Семья с двумя+ детьми пользуется одним приложением с быстрым переключением и полной изоляцией данных; пользователь видит расписание (день/неделя/замены/звонки), ДЗ, посещаемость и сообщения для активного ребёнка
**Depends on**: Phase 4
**Requirements**: ACCT-01, ACCT-02, ACCT-03, ACCT-04, SCHED-01, SCHED-02, SCHED-03, SCHED-04, HW-01, ATT-01, ATT-02, MSG-01
**Success Criteria** (what must be TRUE):
  1. Пользователь добавляет второй аккаунт АВЕРС (ребёнок №2), переключается на него за ≤2 тапа из любого экрана через top-bar dropdown — все экраны (оценки, расписание, ДЗ) сразу показывают данные второго ребёнка
  2. Property-based test «100 случайных переключений между аккаунтами» проходит — данные одного ребёнка никогда не попадают в БД, HTTP-клиент или image-кэш другого; каждый аккаунт имеет отдельный файл `journal_${accountId}.db`
  3. Пользователь видит расписание уроков на день и неделю с навигацией, кабинетами, замены визуально маркируются; на главном экране — обратный отсчёт до конца текущего/начала следующего урока
  4. Пользователь просматривает список ДЗ по предметам, дневную посещаемость с отметками Н/Б, итоги пропусков по периодам, сообщения от учителей в read-only режиме
  5. «Удалить аккаунт» физически удаляет файл БД, KVault-записи, cookies и image-кэш этого аккаунта — после удаления повторное добавление того же логина создаёт чистый аккаунт без следов предыдущего
**Plans**: TBD
**UI hint**: yes

**Closes pitfalls:** #6 (cross-account leak — per-account scope как архитектурный инвариант + property test, не «помним прокинуть accountId»), #8 (расписание = один LazyColumn со sticky-header дней + горизонтальный TabRow, не nested LazyColumn в Pager)

### Phase 6: Background Sync & Local Notifications
**Goal**: Пользователь получает локальные уведомления о новых оценках, ДЗ и заменах в расписании — с честно декларированными ограничениями iOS background и без утечки ПДн на lock screen
**Depends on**: Phase 5
**Requirements**: NOTIF-01, NOTIF-02, NOTIF-03, NOTIF-04, NOTIF-05
**Success Criteria** (what must be TRUE):
  1. Приложение проверяет новые данные в фоне — на Android через WorkManager periodic 15min, на iOS через `BGAppRefreshTask` (каждые ~30 минут как hint) + `BGProcessingTask` ночью; identifier-ы зарегистрированы в Info.plist
  2. При появлении новой оценки/ДЗ/замены в расписании пользователь получает локальное уведомление; DiffDetector в БД (`notifications_state` таблица) гарантирует отсутствие дубликатов
  3. Содержимое уведомлений на lock screen по умолчанию обезличено («Получена новая оценка», без ФИО/предмета/значения); в настройках есть тумблер «Показывать детали» (default off)
  4. В настройках пользователь может включать/отключать отдельные типы уведомлений (оценки / ДЗ / замены) независимо
  5. Onboarding-экран честно объясняет ограничения iOS background («уведомления приходят, когда iOS разрешает фоновую активность; для срочного — откройте приложение»); debug-меню показывает реальную частоту BGAppRefreshTask runs за последние 50 запусков
**Plans**: TBD

**Closes pitfalls:** #4 (BGAppRefreshTask честный UX — onboarding-копирайт, foreground refresh как primary path, tracking реальной частоты), #12 (push с PII на lock screen — default-obfuscated)

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Compliance Infrastructure | 0/TBD | Not started | - |
| 2. API Reverse-Engineering & Network Layer | 0/TBD | Not started | - |
| 3. Auth & Secure Credential Storage | 0/TBD | Not started | - |
| 4. UI Shell, Grades & Offline Foundation | 0/TBD | Not started | - |
| 5. Multi-Account, Schedule, Homework, Attendance & Messages | 0/TBD | Not started | - |
| 6. Background Sync & Local Notifications | 0/TBD | Not started | - |

---

*Roadmap created: 2026-04-27*
*v1 scope: personal/family use via TestFlight + Google Play Internal track. Public submission, формальная регистрация в РКН, письменное согласие школы №28 — отложены в v2 (см. PROJECT.md → Out of Scope, REQUIREMENTS.md → v2 / DIST-V2-*).*
