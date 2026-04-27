# Project Research Summary

**Project:** ЛИнТех Дневник — мобильный клиент к ИАС АВЕРС ЭКЖ для школы №28 г. Кирова
**Domain:** Cross-platform мобильный клиент (iOS+Android, Compose Multiplatform) к закрытому ExtJS-SPA электронного дневника, on-device, мульти-аккаунт, offline-first, без собственного backend
**Researched:** 2026-04-27
**Confidence:** HIGH (стек, архитектура, пользовательские ожидания); MEDIUM (фичи продукта, ограниченные потолком закрытого API АВЕРС); LOW (точный контракт API АВЕРС — раскроется только реверсингом)

---

## Executive Summary

Это **on-device-only** мобильный клиент к проприетарному школьному журналу с реверс-инжинирингом закрытого ExtJS-API. Компромисс «без backend» уже принят в PROJECT.md и определяет всё остальное: данные хранятся в Keychain/Keystore + SQLite per-account, push реализуется через фоновый polling (WorkManager / BGAppRefreshTask) + локальные нотификации с честной декларацией задержек. Стек однозначен: **Kotlin 2.2.20 + Compose Multiplatform 1.10.3 + Ktor 3.4.3 + Room 2.8.x KMP + Koin 4.x + AndroidX Lifecycle ViewModel 2.10**, с paved-path библиотеками (KVault, moko-biometry, Alarmee) для cross-platform secure storage / biometry / local notifications.

Ключевая архитектура — **feature-by-layer multi-module**: `:core:{domain,data,network,database,platform,ui}` + `:feature:{auth,grades,schedule,homework}/{domain,data,ui}`. Применяются пять обязательных паттернов: (1) **Single Source of Truth = БД**, UI наблюдает Flow из SQLite, сеть пишет только в БД; (2) **Per-account HttpClient + persistent cookies в БД**; (3) **AccountStore как StateFlow**, всё подписано на активный аккаунт; (4) **Per-account scope** изоляции (отдельный файл БД на аккаунт `journal_${accountId}.db`); (5) **Koin `expect val platformModule`** для DI без `expect class`. Конкуренты (ЭлЖур, Дневник.ру, СГО) имеют рейтинги 2.2–2.7 — низкий потолок качества, поэтому **простая стабильность + корректный средний балл + отсутствие рекламы/трекеров** уже становится differentiator-ом.

Главные риски ранжированы: **(1) Юридический** — без письменного согласия школы №28 и регистрации в РКН проект может быть остановлен на любом этапе, это блокер до начала кода. **(2) Хрупкость API АВЕРС** — закрытый ExtJS-эндпоинт без SLA, версионировать с первого дня. **(3) Утечка пароля АВЕРС** через iCloud Keychain sync, crash reports, или общий HTTP-клиент между аккаунтами — все три категории требуют активной защиты, не пассивной осторожности. **(4) iOS BGAppRefreshTask** не доставляет обещанного UX — управлять ожиданиями в onboarding/copywriting. **(5) iOS Privacy Manifest** — обязательный с 2024, иначе App Store reject.

## Key Findings

### Recommended Stack

Стек прескриптивный — выбран один путь на каждый слой, альтернативы документированы в STACK.md. Все версии — последние стабильные на апрель 2026, совместимость проверена.

**Core technologies (стек-with-versions для прямого использования в roadmap):**

| Layer | Library | Version | Why |
|---|---|---|---|
| Language | Kotlin | **2.2.20** | JetBrains-рекомендованная для iOS/Web таргетов |
| UI | Compose Multiplatform | **1.10.3** | iOS Stable (с 1.8.0); Hot Reload bundled; @Preview unified |
| HTTP | Ktor Client | **3.4.3** | KMP-first, Darwin (iOS) + OkHttp (Android), HttpCookies для session АВЕРС |
| JSON | kotlinx.serialization | **1.9.0** | Стандарт KMP; `ignoreUnknownKeys = true` обязательно |
| Date/Time | kotlinx-datetime | **0.8.0-rc01** или 0.7.x stable | Критично для расписания/ДЗ |
| Database | AndroidX Room (KMP) | **2.8.x** | Production KMP с 2.7+; знаком разработчику; suspend DAO + Flow |
| Navigation | AndroidX Navigation 3 | **1.0.0-alpha08** | Stable Android, alpha iOS — путь JetBrains |
| ViewModel | lifecycle-viewmodel-compose (KMP) | **2.10.0** | `org.jetbrains.androidx.lifecycle:*`, не `androidx.*` |
| DI | Koin | **4.x** | KMP-first, минимум boilerplate, `expect val platformModule` |
| Settings (non-secret) | multiplatform-settings | **1.3.x** | Active account, theme, lastSync |
| Secure storage | **KVault** (Liftric) | latest | Wraps Keychain (iOS) + EncryptedSharedPreferences (Android). Альтернатива: KSafe |
| Biometry | moko-biometry | **0.4.x** | Face ID / Touch ID / fingerprint, Compose-обёртка |
| Local notifications | Alarmee | **2.4.0** | KMP-обёртка над AlarmManager + UNUserNotificationCenter |
| Background tasks | **expect/actual вручную** (WorkManager + BGTaskScheduler) | androidx.work 2.10+ | KMP-обёртки молодые, контроль точнее своими руками |
| Image loading | Coil 3 | **3.x** | KMP с 3.0; `coil-network-ktor3` shares HTTP client |
| Logging | Kermit | **2.0.4** | Logcat / OSLog / Println-fallback |
| Tests | kotlin.test + Kotest assertions + Turbine + Mokkery + Ktor MockEngine | — | **MockK НЕ подходит** для iOS-таргетов — только JVM |

**iOS-интеграция:** SwiftPM (CocoaPods deprecated, trunk readonly с 2 декабря 2026); composeApp = umbrella XCFramework; SKIE плагин для Swift→Kotlin interop когда понадобится Swift-обёртка push handler-а.

**Anti-stack (НЕ использовать):** MockK (iOS не работает), moko-resources (заморожен — берём CMP Resources), SharedPreferences raw для credentials, NSUserDefaults для credentials, Ktor CIO engine (HTTP/1.1 only), Gson/Moshi/java.time/Joda (JVM-only), Hilt (Android-only), Navigation 2.x в commonMain (deprecated), Realm (платная лицензия).

### Expected Features

**Must have (table stakes — без этого приложение удаляют):**

- Авторизация login/password АВЕРС с persistent cookies
- **Безопасное хранение credentials** (Keychain/Keystore) + биометрия / PIN
- **Текущие и итоговые оценки** + **корректный средний балл** (считаем сами, не доверяя серверу — это уже differentiator из-за главной жалобы на ЭлЖур)
- **Расписание** на день/неделю с **кабинетами и заменами**
- **Домашние задания** с предметами/сроками/(возможно) прикреплёнными файлами
- Посещаемость (Н/Б, итоги пропусков по периодам)
- Сообщения от учителя (read-only)
- **Полный offline** для всех экранов (SQLite кэш + visible staleness indicator)
- **Push** через polling + локальные нотификации (с честной декларацией задержек)
- **Мульти-аккаунт** с быстрым переключением (ключевой differentiator из PROJECT.md)
- Тёмная тема, локализация RU, pull-to-refresh, корректное определение учебного периода

**Should have (differentiators v1.x — превращают «как у всех» в «лучше всех»):**

- iOS Home Screen + Lock Screen Widget (следующий урок / ДЗ); Android App Widget
- **Прогноз итоговой оценки** с учётом весов (главный родительский запрос)
- График динамики среднего балла
- Расписание звонков с обратным отсчётом
- Экспорт расписания/ДЗ в системный календарь (.ics / EventKit / CalendarContract)
- Шрифт для дислексиков (российский кириллический, accessibility-differentiator)
- Smart-индикатор «новое» на оценках/ДЗ; цветовое кодирование оценок
- **Без рекламы, без трекеров, без IAP** — это активный feature, не отсутствие функционала; усиливает privacy-positioning

**Defer v2+:**

- Apple Watch / Wear OS companion
- Чат с учителем (двунаправленный, требует POST-эндпоинтов АВЕРС)
- Поддержка других школ / других АВЕРС-инсталляций (архитектура должна допускать, активная работа — не v1)
- Поддержка других ЭЖ (ЭлЖур, Дневник.ру) — кардинальная смена позиционирования
- Минимальный backend для silent push (revisit Key Decision если UX задержек неприемлем)

**Anti-features (NEVER):** реклама, сторонние аналитики (Firebase Analytics / Yandex.Metrica / Amplitude), in-app purchases / paid PRO, геолокация ребёнка (privacy-катастрофа + 152-ФЗ), геймификация, чат-ленты соц-фичи, OAuth/Госуслуги (АВЕРС не поддерживает).

### Architecture Approach

**Feature-by-layer гибрид:** на верхнем уровне — feature/core, внутри feature — слои `domain` → `data` → `ui` строго в одну сторону. Каждый `:feature:*` — vertical slice; удаление фичи = удаление папки. Зависимости диктуют **build order снизу вверх**: `core:domain` → `core:platform/network/database/data` → `feature:auth` → `core:ui` + composeApp shell + первая feature (`grades`) → остальные features копируют паттерн → background sync + notifications в конце.

**Major components:**

1. **composeApp** — umbrella XCFramework для iOS, корневая навигация (Navigation 3), Koin init. Только wiring, никаких репозиториев.
2. **`:core:platform`** — единственная точка `expect/actual`: `SecureStore`, `BackgroundScheduler`, `Notifier`, `BiometricPrompt`, `Clock`, `HttpClientEngine`. Платформенный код прячется за интерфейсами и инжектится через Koin (`expect val platformModule: Module`).
3. **`:core:network`** — `HttpClientFactory.forAccount(id): HttpClient` — **отдельный клиент per-account** с собственным `PersistentCookiesStorage`, пишущим в SQLite таблицу `cookies(account_id, ...)`. Изоляция cookie между аккаунтами на архитектурном уровне.
4. **`:core:database`** — Room KMP, **отдельный файл БД на аккаунт** (`journal_${accountId}.db`); `DatabaseFactory.forAccount(id)`. Полная изоляция данных, чужие оценки физически недоступны.
5. **`:core:data`** — `AccountStore` (`StateFlow<AccountId?>`) + `SecureCredentialStore` (KVault) + `SyncCoordinator` + `ConnectivityMonitor`. Все use cases подписаны на activeAccountId, переключение аккаунта = одна запись в Settings, UI и sync перепрыгивают через Flow.
6. **`:feature:auth`** — login flow (после реверсинга API), AccountSwitcher composable, biometric prompt. Первая end-to-end вертикаль.
7. **`:feature:{grades,schedule,homework}`** — vertical slices, копирующие паттерн grades. Каждая использует **Single Source of Truth = БД**: `observeGrades()` отдаёт `Flow<List<Grade>>` из SQLite; `refresh()` пишет в БД; UI и сеть независимы.

**Ключевое архитектурное правило:** Pull-to-refresh / SyncCoordinator timer / login event → repo.refresh() → DTO → mapper → БД transaction → `Flow.emit` автоматически перерисовывает UI. UI **никогда** не подписывается на сеть напрямую — это убирает гонки, поддерживает offline бесплатно.

### Critical Pitfalls

**Топ-5 (отранжированы по комбинации вероятности × ущерба):**

1. **Юридический риск (ИИЦ АВЕРС / школа / Роскомнадзор по 152-ФЗ)** — без письменного согласия школы №28 и регистрации в РКН проект может быть закрыт на любом этапе. Штрафы до 18 миллионов рублей за утечку ПДн с 2025 года. **Фаза 0**: получить письменное согласие школы, попытаться получить non-objection от ИИЦ АВЕРС, подать уведомление в РКН через Госуслуги, опубликовать privacy policy на отдельном URL.
2. **Хрупкость reverse-engineered API АВЕРС** — закрытый ExtJS-эндпоинт без SLA; вендорское обновление ломает приложение у всех одновременно. **Фаза 1**: версионированный модуль `aversApiV4_23813`, schema-driven парсинг с явными контрактами, HAR-snapshot тесты от mitmproxy-захвата, canary endpoint при старте, remote kill-switch (статический JSON на CDN), мониторинг parse-failures в Sentry.
3. **Утечка пароля АВЕРС: (a) iCloud Keychain sync; (b) логи/crash reports; (c) общий HTTP-клиент между аккаунтами.** **Фаза 2** для (a)+(b): жёстко `kSecAttrSynchronizable=false`, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, `LogLevel.NONE` в release, sanitizeHeader, `HttpRequestRedactor` маскирующий password/cookie/authorization, canary-test «kanareyka_PASSWORD_DO_NOT_LEAK_42» на любом merge. **Фаза 4** для (c): `HttpClientFactory.forAccount(id)`, отдельный файл БД на аккаунт, per-account DI scope, property test «100 случайных переключений».
4. **iOS BGAppRefreshTask не доставляет обещанного push UX** — Apple запускает «когда повезёт», не при батарее <20% или Low Power Mode, 30 секунд hard limit. App Store Review может отклонить за 2.5.4 «background mode не по назначению». **Фаза 6**: честный copywriting в onboarding и App Store description («уведомления при фоновой проверке по расписанию iOS», не «push о новых оценках»); комбо BGAppRefresh + BGProcessingTask ночью; foreground-refresh как primary path; tracking реальной частоты в debug-меню; explicit App Review Notes.
5. **iOS Privacy Manifest (`PrivacyInfo.xcprivacy`) обязательный с мая 2024, во всех third-party SDK с февраля 2025** — иначе ITMS-91053 reject в App Store Connect. Compose Multiplatform — это и есть «third-party SDK». **Фаза 0** заглушка + **Phase Submission** финал: подключить JetBrains-supplied manifest для CMP, декларировать `NSPrivacyAccessedAPICategoryUserDefaults` (CA92.1), `NSPrivacyAccessedAPICategoryFileTimestamp` (C617.1) для Room, `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]`. Аудит каждой third-party iOS-зависимости.

Дополнительные moderate pitfalls (детали в PITFALLS.md): bundle size iOS ~25-50 MB из-за Skia (App Thinning + ≤30MB target); anti-bot защита АВЕРС (UA mimic, throttling, WebView fallback на CAPTCHA); push с PII на lock screen (default obfuscated); cache staleness без visible indicator; SwiftPM vs CocoaPods (SPM с первого дня); Android-only deps в `commonMain` (CI собирает обе платформы); LazyColumn в HorizontalPager на iOS (известные открытые issues #4279, #4016 — избегать nested scroll структурно); iOS swipe-back gesture (выбрать подход рано: custom Compose / UIKit-wrap / Appyx).

## Implications for Roadmap

Зависимости стека, архитектуры и юридического контекста диктуют 8-фазный roadmap. Каждая фаза имеет один коммит-точку и один риск, который она закрывает.

### Phase 0: Legal & Project Foundation
**Rationale:** Без юридического согласия дальнейшая разработка — ставка с высоким риском выброшенной работы. Скелет проекта и Privacy Manifest заглушка ставятся параллельно.
**Delivers:** Письменное согласие школы №28; уведомление РКН подано; Privacy Policy опубликована на отдельном URL (GitHub Pages); Gradle multi-module skeleton; convention plugins (`build-logic/`); SwiftPM настроен (не CocoaPods); CI собирает обе платформы (iOS+Android); `PrivacyInfo.xcprivacy` заглушка; `:core:domain` с базовыми типами; `:core:platform` каркас expect/actual.
**Addresses:** Foundation для всего остального (нет фич — есть инфраструктура).
**Avoids:** Pitfalls #3 (юридический), #15 (CocoaPods deprecated), #16 (Android-only deps), #9 (Privacy Manifest заглушка).

### Phase 1: API Reverse-Engineering & Network Layer
**Rationale:** Без работающего HTTP-клиента к АВЕРС нет данных — все features ждут. mitmproxy + DevTools-захват эндпоинтов: login, оценки, расписание, ДЗ, посещаемость, сообщения. **Это самая высокая неопределённость в проекте.**
**Delivers:** HAR-snapshots реальных запросов АВЕРС в репозитории; задокументированный contract (login flow, CSRF/cookie pattern, format ответов, anti-bot пороги); `:core:network` с `HttpClientFactory.forAccount(id)` + Darwin/OkHttp engines + Logging (sanitized) + ContentNegotiation + HttpCookies + DefaultRequest + HttpTimeout + HttpRequestRetry; `PersistentCookiesStorage` пишущий в Room таблицу cookies; UA-mimic Mobile Safari; throttling на клиенте; canary endpoint при старте; remote kill-switch JSON на CDN; модуль изолирован как `aversApiV4_23813` для будущего версионирования.
**Uses:** Ktor 3.4.3, kotlinx.serialization 1.9 (`ignoreUnknownKeys=true`), Room 2.8.x (для cookies-таблицы).
**Avoids:** Pitfalls #2 (API fragility — versioned module + canary + monitoring), #11 (anti-bot — UA mimic, throttling, retry-after, WebView fallback готов в коде но за feature flag), #19 (SSL — system trust, no pinning), #5 (логи без password — sanitizeHeader + HttpRequestRedactor).

### Phase 2: Auth & Secure Credential Storage
**Rationale:** Первая end-to-end вертикаль login → cookies в БД → запрос к АВЕРС → JSON. Без auth остальные фичи не имеют данных. **Все защиты от утечки паролей закладываются здесь — не «отрефакторим потом».**
**Delivers:** `:feature:auth:{domain,data,ui}`; `SecureCredentialStore` через KVault (`expect val platformModule`); biometric prompt через moko-biometry; LoginScreen + AccountSwitcher composable; `AuthRepository.login(login, password): Result<AccountId>`; canary-test пароля «kanareyka_PASSWORD_DO_NOT_LEAK_42» в CI; iOS Keychain жёстко с `kSecAttrSynchronizable=false` + `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (unit test проверяет attributes-словарь); Android `EncryptedSharedPreferences` или MasterKey с `setUserAuthenticationRequired(true)`; biometric fallback на PIN/системный пароль для устройств без Face ID/Touch ID.
**Uses:** KVault (Liftric), moko-biometry, multiplatform-settings, Ktor (для login request).
**Avoids:** Pitfalls #1 (iCloud sync — unit test на synchronizable=false), #5 (logging hygiene — canary test), #17 (biometry fallback на iPhone SE), частично #6 (single-account scope первый, multi-account в фазе 4 поверх).

### Phase 3: UI Shell + First Vertical (Grades)
**Rationale:** Оценки — главная ценность по PROJECT.md core value, наименее зависят от UI-сложности (просто список), хороший пилот для **Single Source of Truth + Reactive Repository** паттерна. Архитектурные решения по навигации фиксируются здесь раз и навсегда — менять потом = переписать половину.
**Delivers:** `:core:ui` (тема Material 3, AppScaffold, OfflineBanner, AccountSwitcher composable, дизайн-токены); composeApp скелет (App composable, Navigation 3 setup, Koin init с `platformModule + networkModule + databaseModule + dataModule + featureModules`); `:feature:grades:{domain,data,ui}` end-to-end (GradesRepository с `observeGrades(): Flow<List<Grade>>` из Room + `refresh(): Result<Unit>` пишет в Room; GradesViewModel через `lifecycle-viewmodel-compose` 2.10 с `viewModel { koinInject() }` фабрикой; GradesScreen с pull-to-refresh + visible staleness indicator «обновлено 2ч назад» + жёлтая/красная плашка при stale > 24/72h); ADR на iOS swipe-back gesture (custom Compose / UIKit-wrap / Appyx — выбран один путь, документирован).
**Uses:** Compose Multiplatform 1.10.3, Navigation 3 alpha-08, lifecycle-viewmodel-compose 2.10.0, Koin 4 + koin-compose-navigation3, Room 2.8 KMP, kotlinx.coroutines + Flow.
**Avoids:** Pitfalls #7 (swipe-back ADR — решение раннее), #8 (LazyColumn nested scroll — структурно избегаем nested LazyColumn в HorizontalPager, тестируем на физическом iPhone), #13 (cache staleness — visible indicator в первой же фиче).

### Phase 4: Multi-Account
**Rationale:** Ключевой differentiator из PROJECT.md (семьи с двумя+ детьми). **Per-account scope** должен быть архитектурой с самого начала — но активируется и тестируется здесь. До этого Phase 2/3 работают с single-account, но через тот же AccountStore API (просто accounts.size == 1).
**Delivers:** `AccountStoreImpl` с `StateFlow<AccountId?>` и `addAccount/switchTo/listAccounts/deleteAccount`; per-account `HttpClient` cache в `HttpClientFactory` (`forAccount(id)` getOrPut, `evict(id)` close); per-account database `Database.forAccount(id)` (отдельный файл `journal_${accountId}.db`); `SyncCoordinator` подписан на `activeAccountId.distinctUntilChanged()` и триггерит refresh при смене; AccountSwitcher UI (top-bar dropdown, ≤2 тапа на переключение); полный logout/delete-account flow (удаляет файлы БД + KVault entries + cookies + image cache + push tokens); property-based test «100 случайных переключений в случайном порядке» — нет утечки между аккаунтами.
**Uses:** AccountStore, HttpClientFactory, DatabaseFactory, KVault, SecureCredentialStore.
**Avoids:** Pitfalls #6 (cross-account leak — per-account scope + property test = архитектурный инвариант, не «помним прокинуть accountId»).

### Phase 5: Schedule & Homework Verticals
**Rationale:** Повторяют паттерн grades. Могут идти параллельно — копируют шаблон vertical slice. Schedule сложнее из-за nested scroll рисков (см. Pitfall #8) и интеграции замен. Homework включает прикреплённые файлы (download + disk cache) если АВЕРС отдаёт.
**Delivers:** `:feature:schedule:{domain,data,ui}` с расписанием на день/неделю, явной маркировкой замен, кабинетами; `:feature:homework:{domain,data,ui}` с группировкой по предметам, сроками, опциональным download прикреплённых файлов через Coil 3 (`coil-network-ktor3` shares HTTP client) с size-limited disk cache; посещаемость и сообщения как «лёгкие» под-экраны (можно встроить в schedule/homework или вынести отдельно `:feature:attendance`, `:feature:messages` если объём оправдает).
**Uses:** тот же стек что Phase 3 + Coil 3 для homework attachments.
**Avoids:** Pitfalls #8 (расписание = один LazyColumn со sticky-header дней + горизонтальный TabRow, не nested LazyColumn в Pager), #13 (staleness indicator в каждом экране).

### Phase 6: Background Sync, Local Notifications & Differentiators
**Rationale:** Требует, чтобы фичи уже были (что синхронизировать?) и чтобы в БД были `notifications_state` таблицы (с чем сравнивать diff). Делать раньше — оптимизация без юзкейса. Здесь же добавляются дешёвые differentiators (smart-индикатор «новое», графики, прогноз).
**Delivers:** `BackgroundScheduler` expect/actual (Android: WorkManager periodic 15min; iOS: `BGAppRefreshTask` + `BGProcessingTask` ночью; identifiers зарегистрированы в Info.plist); `Notifier` через Alarmee; `SyncCoordinator.runScheduledSync()` опрашивает все аккаунты round-robin (с приоритетом активного при iOS 30s timeout); `DiffDetector` сравнивает new vs notifications_state в БД; локальные уведомления **default-obfuscated** («Получена новая оценка», без ФИО/оценки на lock screen) с настройкой «показывать детали»; honest copywriting в onboarding («Уведомления приходят когда iOS разрешает фоновую активность... Для срочного — откройте приложение»); foreground refresh при app start/switch как primary path; debug-меню с tracking реальной частоты BGAppRefreshTask runs. **Differentiators (cheap):** прогноз итоговой оценки, график динамики среднего балла, расписание звонков с обратным отсчётом, smart-индикатор «новое» (last_seen_at в БД), цветовое кодирование оценок, шрифт для дислексиков (toggle + bundle font), pull-to-refresh polish, экспорт в .ics календарь.
**Uses:** WorkManager (androidMain), BGTaskScheduler (iosMain), Alarmee, Room (notifications_state, last_seen_at, sync_meta), kotlinx-datetime.
**Avoids:** Pitfalls #4 (honest UX), #12 (PII на lock screen — default off), #13 (staleness — finalized).

### Phase 7: Submission & Production Hardening
**Rationale:** Финальный полишинг и магазинная подача. Все размытые риски «потом учтём» закрываются здесь.
**Delivers:** Apple Developer ($99/год) + Google Play Console ($25); App Store metadata (без обещаний realtime push); Google Play Data Safety декларация; iOS `PrivacyInfo.xcprivacy` финальный аудит (CMP + транзитивные деps Ktor/Room/kotlinx); App Thinning, strip-debug-symbols, размер ≤30 MB Universal IPA; widgets (iOS Home Screen + Lock Screen WidgetKit, Android Glance) через App Groups / Content Provider к shared Room; `Reset to factory` в настройках (полное удаление); App Review Notes объясняющие background polling use case; crash reporting opt-in (Sentry beforeSend hook фильтрует PII / network breadcrumbs / canary password); deep links через custom scheme (`lintehjournal://`) — Universal Links отложены (нужен свой домен, школа №28 не наш сервер); accessibility audit (VoiceOver/TalkBack, Dynamic Type, contrast); "Looks done but isn't" checklist из PITFALLS.md полностью пройден.
**Uses:** Все предыдущие.
**Avoids:** Pitfalls #9 (Privacy Manifest финал), #10 (bundle size), #18 (Universal Links — custom scheme вместо).

### Phase Ordering Rationale

- **Зависимости стека:** Gradle multi-module skeleton → core:domain (база типов) → core:platform/network/database (инфраструктура) → core:data + AccountStore → feature:auth (первая вертикаль с реальным API) → core:ui + composeApp shell + feature:grades (полный паттерн) → schedule/homework (копируют) → background sync (нужны фичи + DB) → submission.
- **Архитектура диктует:** «никогда не строить feature:ui раньше feature:domain раньше feature:data» — это правило часто нарушают, начиная с UI-моков. Для offline-first проекта это плохо: моки не покажут реальные паттерны Flow/StateFlow.
- **Юридическое ограничение Phase 0:** не код-блокер технически, но без согласия школы фазы 1+ имеют высокий риск выброшенной работы; запараллелить с настройкой проекта.
- **Multi-account отдельной фазой 4, не в auth (фаза 2):** auth работает с одним аккаунтом через тот же AccountStore API, но активная изоляция (per-account scope, отдельные БД-файлы, property test) добавляется когда первая вертикаль уже доказала single-source-of-truth pattern.
- **Background sync в конце (фаза 6), не сразу:** нечего синхронизировать без фич. Также позволяет фазе 3 (UI shell) сосредоточиться на foreground-first UX — что и так должно быть primary path для iOS из-за Pitfall #4.

### Research Flags

**Phases needing dedicated research/spike (вызов `/gsd-research-phase` обязателен):**

- **Phase 1 (API Reverse-Engineering)** — **HIGHEST priority spike**. Закрытый ExtJS-API АВЕРС не документирован публично; нужен mitmproxy/Charles/DevTools-захват реальных запросов журнала школы №28. Открытые вопросы: (1) поддерживает ли АВЕРС какой-либо OAuth/token-flow или строго session-cookie; (2) есть ли CSRF-токен и в каком виде (meta-tag/cookie/header); (3) формат login response (302 redirect / JSON / HTML); (4) формат оценок/расписания/ДЗ (`{success, data}` ExtJS-style или прямой массив); (5) есть ли delta API (`/api/changes-since?ts=`) или только полная выгрузка; (6) anti-bot пороги (rate-limit, CAPTCHA after N login fails); (7) отдаются ли явные «замены», прикреплённые файлы ДЗ, веса оценок. **Без этой фазы roadmap дальше — гипотезы.**
- **Phase 6 (Background Sync iOS)** — молодые KMP-обёртки над BGTaskScheduler; решение «писать своё `expect/actual` или использовать `kmpworkmanager`/`multiplatform-work-manager`» требует оценки зрелости в момент имплементации. iOS поведение BGAppRefreshTask недетерминировано — нужны реальные measurements на физических устройствах в течение недели.
- **Phase 0 (Legal)** — не техническая research, но требует коммуникации с администрацией школы №28, ИИЦ АВЕРС (avers-journal.ru), консультации по 152-ФЗ. Outcome определяет go/no-go.

**Phases с standard well-documented patterns (можно skip dedicated research):**

- **Phase 2 (Auth & Secure Storage)** — KVault + moko-biometry + Ktor login; iOS Keychain атрибуты документированы Apple; canary-test password — стандартная практика.
- **Phase 3 (UI Shell + Grades)** — Compose Multiplatform + Navigation 3 + lifecycle-viewmodel-compose + Single Source of Truth — все паттерны в STACK.md и ARCHITECTURE.md.
- **Phase 4 (Multi-account)** — per-account scope паттерн стандартный, описан в ARCHITECTURE.md (Pattern 3+4).
- **Phase 5 (Schedule/Homework)** — копируют Phase 3 паттерн.
- **Phase 7 (Submission)** — Apple/Google docs покрывают всё, стандартный submission flow.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | Все версии — последние стабильные на апрель 2026, источники — официальные JetBrains/Google/touchlab/icerock; альтернативы документированы; anti-stack явный |
| Features | **MEDIUM-HIGH** | АВЕРС-капабилити подтверждены официальной документацией продукта; конкуренты (рейтинги 2.2–2.7) — публичные App Store/Google Play данные; **точный набор JSON-полей АВЕРС остаётся гипотезой до Phase 1 spike** |
| Architecture | **HIGH** | Стандартные KMP-паттерны, validated в production-кейсах (Cash App, McDonald's); feature-by-layer + Single Source of Truth + per-account scope — community consensus 2025-2026 |
| Pitfalls | **HIGH** | Основано на актуальных открытых GitHub issues JetBrains/compose-multiplatform, официальной документации Apple App Review / Privacy Manifest, известных мульти-аккаунт-ловушках, 152-ФЗ (ред. 24.06.2025); **LOW только в части domain-specific анти-бота АВЕРС** — гипотезы до Phase 1 |

**Overall confidence:** **HIGH** для технических решений и структуры roadmap; **MEDIUM** для оценки трудозатрат Phase 1 (зависит от реальной запутанности ExtJS-API и наличия anti-bot защит); **рискованная зона** — Phase 0 юридическое (бинарный outcome).

### Gaps to Address

- **API АВЕРС — неизвестные:** OAuth/token vs cookie-only, CSRF-формат, формат ответов (ExtJS-обёртка или прямой), delta-API, anti-bot пороги, отдаются ли веса оценок и явные замены. **Resolution:** Phase 1 mitmproxy-захват и HAR-snapshots; до этого все feature-роадмап-пункты ниже фазы 1 — ставка.
- **Согласие школы №28 / ИИЦ АВЕРС:** binary go/no-go. **Resolution:** Phase 0 переговоры; backup-стратегия (TestFlight + Sideload через AltStore) если откажут.
- **Регистрация в РКН:** требуется ли формально для on-device приложения (юр. неопределённость, склоняемся к «да, на всякий случай»). **Resolution:** Phase 0 подача уведомления через Госуслуги; консультация по 152-ФЗ при необходимости.
- **Прогноз итоговой оценки — формула с весами:** если АВЕРС не отдаёт веса оценок, нужно либо хардкодить «дефолтные веса школы №28» (узнать у учителя/завуча), либо отображать с дисклеймером. **Resolution:** Phase 1 (выяснить отдаёт ли API) → Phase 6 (имплементация прогноза) с fallback-стратегией.
- **iOS BGAppRefreshTask реальная частота:** только measurements на реальных устройствах в течение недели покажут реальный UX. **Resolution:** Phase 6 + post-launch monitoring через debug-меню.
- **Расписание звонков для школы №28:** может не отдаваться АВЕРС. **Resolution:** v1 — захардкодить актуальное; конфигурируемо в v2.
- **iOS swipe-back gesture choice:** custom Compose / UIKit-wrap / Appyx — три варианта с trade-off. **Resolution:** Phase 3 ADR с тестированием на физическом iPhone, не симуляторе.
- **Mokkery vs Mockative vs MocKMP:** для тестов, все три KMP-friendly. **Resolution:** начать с Mokkery (лучше документирован), при проблемах — миграция несложная.

## Sources

### Primary (HIGH confidence)
- **STACK.md** — детальный стек с версиями, alternatives, anti-stack, libs.versions.toml фрагмент
- **ARCHITECTURE.md** — слои, паттерны, build-order, anti-patterns, integration points
- **FEATURES.md** — table stakes / differentiators / anti-features, конкурентный анализ, on-device viability matrix
- **PITFALLS.md** — 19 pitfalls по категориям + tech-debt + security + UX + recovery + phase-mapping
- **PROJECT.md** — Core Value, Active requirements, Out of Scope, Key Decisions

### Stack & Architecture (HIGH — JetBrains/Google official)
- [Compose Multiplatform 1.8.0 Released — iOS Stable (2025-05)](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- [Compose Multiplatform 1.10.0 — Navigation 3, Hot Reload, @Preview (2026-01)](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/)
- [KMP Roadmap August 2025](https://blog.jetbrains.com/kotlin/2025/08/kmp-roadmap-aug-2025/)
- [Set up Room for KMP — Android Developers](https://developer.android.com/kotlin/multiplatform/room)
- [Common ViewModel — Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Navigation 3 in Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Ktor Client Cookies / Engines](https://ktor.io/docs/client-cookies.html)
- [Privacy Manifest for iOS apps — Kotlin docs](https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html)

### Pitfalls (HIGH — Apple/Google/JetBrains official + open issue tracker)
- [JetBrains/compose-multiplatform issues #4279 #4016 #4818 #4902 #5088 #4855](https://github.com/JetBrains/compose-multiplatform/issues)
- [Apple Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)
- [Apple kSecAttrSynchronizable](https://developer.apple.com/documentation/security/ksecattrsynchronizable)
- [Apple BGAppRefreshTask documentation](https://developer.apple.com/documentation/backgroundtasks/bgapprefreshtask)
- [Apple App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [152-ФЗ редакция 24.06.2025 (Контур)](https://normativ.kontur.ru/document?moduleId=1&documentId=501173)

### АВЕРС (HIGH — official product docs)
- [ИАС «АВЕРС: ЭКЖ» — описание продукта (cit-avers.ru)](https://cit-avers.ru/produktsiya/shkola/ias-avers-elektronnyj-klassnyj-zhurnal/)
- [Руководство пользователя ЭКЖ v3.1 — Родитель/Учащийся](http://docplayer.ru/26131097-Avers-informacionno-analiticheskaya-sistema-avers-elektronnyy-klassnyy-zhurnal-versiya-3-1-rukovodstvo-dlya-polzovatelya-v-roli-roditel-uchashchiysya.html)
- [journal.school28-kirov.ru — целевая инсталляция](https://journal.school28-kirov.ru/)

### Architecture community (MEDIUM-HIGH)
- [Carrion.dev — KMP architecture best practices](https://carrion.dev/en/posts/kmp-architecture/)
- [Touchlab — Optimizing Gradle in multi-module KMP](https://touchlab.co/optimizing-gradle-builds-in-Multi-module-projects)
- [droidcon — Offline-First Architecture 2025](https://www.droidcon.com/2025/12/16/the-complete-guide-to-offline-first-architecture-in-android/)
- [SKIE — Touchlab Swift interop plugin](https://skie.touchlab.co/)
- [KVault — Liftric Keychain/Keystore wrapper](https://github.com/Liftric/KVault)
- [moko-biometry, Alarmee, KMPNotifier](https://github.com/icerockdev/moko-biometry)

---
*Research completed: 2026-04-27*
*Ready for roadmap: yes (Phase 0/1 outcomes — gating decisions для остального плана)*
