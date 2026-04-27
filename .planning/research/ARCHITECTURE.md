# Architecture Research

**Domain:** Compose Multiplatform мобильный клиент к закрытому SPA-API (ИАС АВЕРС), on-device-only, мульти-аккаунт, offline-first, iOS+Android
**Researched:** 2026-04-27
**Confidence:** HIGH (для слоёв, Ktor/SQLDelight/Koin, expect/actual), MEDIUM (для Navigation 3 — пока alpha; для multi-account — паттерн стандартный, но не «коробочный»), LOW (только в части background sync на iOS — экосистемные обёртки молодые)

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                  PRESENTATION (composeApp + :feature:*:ui)           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │ Auth UI  │  │Grades UI │  │Schedule  │  │ Homework / Settings  │  │
│  │ + VM     │  │ + VM     │  │ UI + VM  │  │ UI + VM              │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────────┬───────────┘  │
│       │             │             │                    │             │
│       │  Navigation 3 (NavKey + SnapshotStateList) — единый back stack
│       │             │             │                    │             │
├───────┴─────────────┴─────────────┴────────────────────┴─────────────┤
│                       DOMAIN (:feature:*:domain, :core:domain)        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  Use cases (GetGradesForActiveAccount, RefreshSchedule, ...) │    │
│  │  Repository INTERFACES (only interfaces, no impls)           │    │
│  │  Pure domain models (Grade, Lesson, Homework, Account)       │    │
│  │  AccountContext — «текущий аккаунт» как доменная сущность    │    │
│  └──────────────────────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────────────────────┤
│                          DATA (:core:data, :feature:*:data)          │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────┐  │
│  │ Repositories (impl)│  │ DTO ⇄ Domain mapper│  │ SyncCoordinator│  │
│  │ — Single source    │  │                    │  │ (per-account)  │  │
│  │ of truth = БД      │  │                    │  │                │  │
│  └─────────┬──────────┘  └─────────┬──────────┘  └────────┬───────┘  │
│            │                       │                       │         │
│  ┌─────────▼──────────┐  ┌─────────▼──────────┐  ┌────────▼───────┐  │
│  │ Local source       │  │ Remote source      │  │ AccountStore   │  │
│  │ (SQLDelight DAO,   │  │ (Ktor HttpClient   │  │ (active acct + │  │
│  │ per-account schema)│  │ — instance per     │  │ list of accts) │  │
│  │                    │  │ account)           │  │                │  │
│  └─────────┬──────────┘  └─────────┬──────────┘  └────────┬───────┘  │
│            │                       │                       │         │
├────────────┴───────────────────────┴───────────────────────┴─────────┤
│                  PLATFORM (:core:platform, expect/actual)            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌─────────────┐  │
│  │ SecureStore  │ │BackgroundSync│ │ Notifier     │ │ HttpEngine  │  │
│  │ (Keychain /  │ │(WorkManager /│ │(local notifs)│ │ (Darwin /   │  │
│  │ EncryptedSP) │ │BGTaskSched)  │ │              │ │ OkHttp)     │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └─────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
                                                                       
        AndroidApp (MainActivity, FCM stub)         iosApp (UIScene)   
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **composeApp** | Сборка App composable, корневая навигация (Navigation 3 back stack), DI-инициализация (`startKoin`), темизация, root error boundary | `commonMain` + `androidMain`/`iosMain` entry points; **никаких** бизнес-репозиториев здесь |
| **:feature:auth** | Логин-экран, multi-account picker, добавление/удаление аккаунта, biometric prompt | UI слой + ViewModel (lifecycle-viewmodel-compose) + AuthRepository + use cases (`Login`, `SwitchAccount`, `LogoutAll`) |
| **:feature:grades / :schedule / :homework** | Доменные фичи. Каждая = vertical slice: ui + domain + data | По одной Gradle-подсборке на фичу; зависят от `:core:*`, не друг от друга |
| **:core:network** | Конфигурация Ktor `HttpClient`, фабрика per-account клиентов, JSON config, общие плагины (Logging, ContentNegotiation, HttpCookies, HttpRequestRetry) | `expect HttpClientEngine` — actual Darwin/OkHttp; `HttpClientFactory.forAccount(accountId): HttpClient` |
| **:core:database** | Схема SQLDelight, фабрика `Database.forAccount(accountId)`, миграции, общие конвертеры | SQLDelight (KMP-зрелее Room в iOS на 2026); один файл БД на аккаунт |
| **:core:domain** | Сквозные модели (Account, AccountId, Result/AppError), общие use cases (`ObserveActiveAccount`) | Чистый Kotlin, нет зависимостей от Android/iOS |
| **:core:data** | `AccountStore`, `SecureCredentialStore`, `SyncCoordinator`, маппер ошибок | Зависит от `:core:network`, `:core:database`, `:core:platform` |
| **:core:platform** | Все expect/actual: `SecureStore`, `BackgroundScheduler`, `Notifier`, `BiometricPrompt`, `Clock`, `AppDirs` | `commonMain` объявляет интерфейсы/expect, `androidMain`/`iosMain` — реализации; экспонируется в Koin |
| **:core:ui** | Дизайн-токены, общие composable (`AppScaffold`, `AccountSwitcher`, `OfflineBanner`, error/empty states) | Compose Multiplatform; зависит только от material3 и kotlinx |
| **AccountStore** | Источник истины «какой аккаунт активен», список аккаунтов, событие «аккаунт переключился» | `StateFlow<AccountId?>`, persisted в Settings; dependents наблюдают |
| **SecureCredentialStore** | Хранение пар (login, password, cookies, deviceId) per-account в Keychain/Keystore | expect/actual поверх `multiplatform-settings` no-arg + Keychain/EncryptedSharedPreferences |
| **SyncCoordinator** | Триггерит refresh репозиториев при смене аккаунта, при возврате online, по расписанию (WorkManager/BGTask) | Подписан на AccountStore + ConnectivityMonitor; вызывает `Repository.refresh()` |

---

## Recommended Project Structure

Принимаем **feature-by-layer гибрид**: верхний уровень — feature/core, внутри feature — слои. Это рекомендуют как Carrion.dev и Murali M (Compose Multiplatform clean architecture, 2025), так и сообщество в KMP-track-е.

```
LintehJournal/
├── composeApp/                          # Точка сборки: MainActivity (Android) + iosApp delegate
│   ├── src/commonMain/                  # App composable, корневой NavBackStack, Koin init, theme
│   ├── src/androidMain/                 # MainActivity, Application, FCM-stub (на будущее)
│   └── src/iosMain/                     # MainViewController(), iOS-side BGTask registration
│
├── iosApp/                              # Xcode-проект; импортирует composeApp как XCFramework
│   └── iosApp/                          # SwiftUI обёртка → MainViewController()
│
├── core/
│   ├── domain/                          # Чистый Kotlin. Нет Android/iOS imports.
│   │   └── src/commonMain/
│   │       ├── model/                   # Account, AccountId, AppError, Result<T>
│   │       ├── repository/              # Интерфейсы AccountRepository, AuthRepository (только signatures)
│   │       └── usecase/                 # ObserveActiveAccount, SwitchAccount
│   │
│   ├── data/                            # Реализация репозиториев + AccountStore + SyncCoordinator
│   │   └── src/commonMain/
│   │       ├── account/                 # AccountStoreImpl, ActiveAccountHolder
│   │       ├── credentials/             # SecureCredentialStore (использует :core:platform)
│   │       └── sync/                    # SyncCoordinator, ConnectivityMonitor
│   │
│   ├── network/                         # Ktor configuration
│   │   └── src/commonMain/
│   │       ├── HttpClientFactory.kt     # forAccount(accountId): HttpClient
│   │       ├── plugins/                 # AversAuthPlugin, RetryPolicy, Logging
│   │       └── cookies/                 # PersistentCookiesStorage (per-account, в БД)
│   │
│   ├── database/                        # SQLDelight
│   │   ├── src/commonMain/sqldelight/   # *.sq schema
│   │   ├── src/commonMain/kotlin/       # DatabaseFactory.forAccount(accountId)
│   │   ├── src/androidMain/             # AndroidSqliteDriver
│   │   └── src/iosMain/                 # NativeSqliteDriver
│   │
│   ├── platform/                        # ВСЕ expect/actual platform-specific вещи
│   │   ├── src/commonMain/              # expect interface SecureStore, BackgroundScheduler, Notifier, BiometricPrompt, Clock
│   │   ├── src/androidMain/             # actual: Keystore+EncryptedSP, WorkManager, NotificationManagerCompat
│   │   └── src/iosMain/                 # actual: Keychain (Security framework), BGTaskScheduler, UNUserNotificationCenter
│   │
│   └── ui/                              # Дизайн-система (тема, AppScaffold, OfflineBanner, AccountSwitcher)
│       └── src/commonMain/
│
├── feature/
│   ├── auth/
│   │   ├── domain/                      # LoginUseCase, AddAccountUseCase, LogoutUseCase
│   │   ├── data/                        # AuthRepositoryImpl (Ktor + AccountStore + SecureCredentialStore)
│   │   └── ui/                          # LoginScreen, AccountSwitcher, AuthViewModel (lifecycle-viewmodel)
│   │
│   ├── grades/
│   │   ├── domain/                      # GetGradesForActiveAccount, GradesRepository (interface)
│   │   ├── data/                        # GradesRepositoryImpl, GradesRemoteSource, GradesLocalSource (DAO)
│   │   └── ui/                          # GradesScreen, GradeDetailsScreen, ViewModels
│   │
│   ├── schedule/  (та же тройка domain/data/ui)
│   └── homework/  (та же тройка domain/data/ui)
│
└── build-logic/                         # Convention plugins (kmp-library, kmp-feature, kmp-android)
    └── src/main/kotlin/                 # Уменьшаем boilerplate в build.gradle.kts фич
```

### Structure Rationale

- **feature/ × layer внутри:** vertical slices изолируют доменный код. Удаление фичи = удаление папки. Layer-boundaries внутри фичи (`domain` → `data` → `ui` строго в одну сторону) защищают от утечек реализации.
- **Гранулярные core/* модули:** позволяют фичам зависеть только от того, что им нужно. Например `:feature:grades:domain` зависит **только** от `:core:domain` — никакого Ktor/SQLDelight в его classpath.
- **`:core:platform` как единственная точка expect/actual:** избегаем разбросанных по фичам `expect class`-ов. Фичи получают чистые интерфейсы из DI, не зная про платформы.
- **Один umbrella «iOS framework» через composeApp:** на 2026 KMP всё ещё рекомендует **один XCFramework** на приложение (multi-framework даёт duplicate stdlib и раздутый бинарь — Touchlab/Carrion). composeApp служит umbrella-модулем.
- **build-logic convention plugins:** при ~10 модулях boilerplate в `build.gradle.kts` становится катастрофой. JetBrains Multiplatform docs (2026) рекомендуют convention plugins для KMP с тремя+ модулями.

---

## Architectural Patterns

### Pattern 1: Single Source of Truth (DB-as-truth) + Reactive Repository

**What:** UI наблюдает `Flow<T>` из локальной БД. Сеть пишет **только** в БД. UI никогда не подписан напрямую на сеть.

**When to use:** Всегда в этом проекте — все основные экраны должны открываться offline.

**Trade-offs:**
- (+) Offline-first работает «бесплатно»; UI всегда консистентен
- (+) Гонки между «загружаю» и «обновлено пушом» исчезают — БД линеаризует
- (−) Каждое поле, которое нужно UI, должно быть в схеме БД
- (−) При первой загрузке (БД пуста) нужно явно показывать `Loading` (Flow вернёт `emptyList`, а не «loading»)

**Example:**
```kotlin
// :feature:grades:data
class GradesRepositoryImpl(
    private val local: GradesLocalSource,   // SQLDelight DAO
    private val remote: GradesRemoteSource, // Ktor
    private val accounts: AccountStore,
    private val clock: Clock,
) : GradesRepository {

    // Реактивный поток: UI получает обновления из БД автоматически.
    override fun observeGrades(): Flow<List<Grade>> =
        accounts.activeAccountId
            .filterNotNull()
            .flatMapLatest { acc -> local.observeGrades(acc) }

    // Refresh: сеть → БД. Не возвращает данные напрямую — UI получит их через observeGrades().
    override suspend fun refresh(): Result<Unit> = runCatching {
        val acc = accounts.requireActiveAccountId()
        val dto = remote.fetchGrades(acc)             // Ktor под капотом per-account клиент
        local.replaceGrades(acc, dto.toDomain())      // single transaction
        local.touchSyncMeta(acc, "grades", clock.now())
    }.fold(::success, { e -> failure(e.toAppError()) })
}
```

```kotlin
// :feature:grades:ui — ViewModel
class GradesViewModel(
    observeGrades: ObserveGradesUseCase,
    private val refresh: RefreshGradesUseCase,
) : ViewModel() {
    val state: StateFlow<GradesUiState> = observeGrades()
        .map { GradesUiState.Loaded(it) }
        .onStart { emit(GradesUiState.Loading) }
        .catch { emit(GradesUiState.Error(it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesUiState.Loading)

    fun onPullToRefresh() = viewModelScope.launch { refresh() }
}
```

**Почему именно так, а не «классический» NetworkBoundResource:**
NetworkBoundResource (Google sample, ~2018) выдавал `Resource<Loading|Success|Error>` обёртку над данными. В 2025–2026 community-консенсус (Zignuts, droidcon, ProAndroidDev) — **разделить два потока**: `observe()` отдаёт чистые данные из БД, `refresh()` отдаёт `Result<Unit>`. UI комбинирует их в свой UiState. Чище для тестов и Compose recomposition.

---

### Pattern 2: ViewModel via `lifecycle-viewmodel-compose` (KMP-стабильна с 2024)

**What:** Используем androidx-овский `lifecycle-viewmodel` + `lifecycle-viewmodel-compose`, который JetBrains портировала в KMP. Это **официальный** путь в Compose Multiplatform docs.

**When to use:** Всегда. Для нашего проекта Decompose / Voyager / Orbit — overengineering.

**Trade-offs:**
- (+) Официальный путь JetBrains; та же ментальная модель, что Jetpack Compose
- (+) Хорошо ложится на Navigation 3 (каждая запись back-stack-а — LifecycleOwner)
- (+) Минимум зависимостей; ViewModel живёт в `commonMain`
- (−) На non-JVM (iOS) **обязательно передавать initializer**: `viewModel { GradesViewModel(...) }`, без аргументов (`viewModel()`) не сработает из-за отсутствия рефлексии в K/N
- (−) `SavedStateHandle` на iOS до сих пор в догоняющем статусе — для process-death restore придётся допиливать вручную (но iOS меньше страдает от kill-restore)

**Example:**
```kotlin
// commonMain — ViewModel в чистом виде
class GradesViewModel(
    observeGrades: ObserveGradesUseCase,
    private val refresh: RefreshGradesUseCase,
) : ViewModel() { /* ... */ }

// commonMain — Composable
@Composable
fun GradesScreen(
    viewModel: GradesViewModel = viewModel { 
        koinInject<GradesViewModel>() // или фабрика напрямую
    }
) {
    val state by viewModel.state.collectAsState()
    // UI...
}
```

**Почему не Decompose / Voyager / Orbit:**
- **Decompose** — мощнее (BLoC-tree, чистая навигация без Compose), но сложнее, чужая ментальная модель, не покрывает Compose-специфики; оправдан в больших product-suite-проектах с shared web/desktop
- **Voyager** — приятный API, но `ScreenModel` ≠ `ViewModel`; навигация Voyager-а конкурирует с Navigation 3 от JetBrains, и community-momentum уходит в сторону официального решения
- **Orbit MVI** — отличный для жёсткого MVI, но добавляет ещё одну ментальную модель сверху ViewModel-а; в нашем CRUD-фокусе это лишнее
- **lifecycle-viewmodel-compose** — путь наименьшего сопротивления, рекомендован JetBrains, единственный из четырёх портирован в Android Studio templates KMP в 2026

---

### Pattern 3: Per-Account HttpClient + Persistent CookiesStorage в БД

**What:** Для каждого аккаунта — отдельный `HttpClient` с собственным `CookiesStorage`, который персистится в SQLDelight. `HttpClientFactory.forAccount(id)` кэширует инстансы.

**When to use:** Multi-account проекты, где сервер использует cookie-сессии (а АВЕРС, судя по ExtJS-фронту, почти наверняка).

**Trade-offs:**
- (+) Полная изоляция: аккаунт A никогда не отправит cookie аккаунта B
- (+) Cookies переживают рестарт приложения (хранятся в БД, не в памяти)
- (+) При переключении аккаунта — мгновенно (просто берём другой HttpClient из кэша)
- (−) N клиентов = N плагин-pipeline-ов; для 5–10 аккаунтов это не проблема
- (−) Нужно вручную закрывать `HttpClient.close()` при удалении аккаунта

**Example:**
```kotlin
// :core:network
class HttpClientFactory(
    private val engine: HttpClientEngine,         // expect/actual: Darwin / OkHttp
    private val cookiesStorageFactory: (AccountId) -> CookiesStorage,
    private val baseUrl: String,                  // school28-kirov.ru
) {
    private val clients = mutableMapOf<AccountId, HttpClient>()

    fun forAccount(id: AccountId): HttpClient = clients.getOrPut(id) {
        HttpClient(engine) {
            install(HttpCookies) {
                storage = cookiesStorageFactory(id)   // per-account storage
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpRequestRetry) { retryOnExceptionIf(3) { _, e -> e is IOException } }
            install(Logging) { level = LogLevel.INFO }
            defaultRequest { url(baseUrl) }
        }
    }

    fun evict(id: AccountId) { clients.remove(id)?.close() }
}

// PersistentCookiesStorage — пишет в SQLDelight таблицу cookies(account_id, host, name, value, expires)
class PersistentCookiesStorage(
    private val accountId: AccountId,
    private val dao: CookiesDao,
) : CookiesStorage { /* read/write per-account row */ }
```

**Альтернатива:** один HttpClient + плагин, переключающий cookie storage. Проигрывает: мутабельное состояние, гонки при concurrent refresh у двух аккаунтов (например, фоновый sync ребёнка-1, в это время пользователь смотрит ребёнка-2).

---

### Pattern 4: Active Account как `StateFlow` + всё подписано на него

**What:** `AccountStore` — single source of truth для «какой аккаунт сейчас активен». Все use cases / ViewModels подписаны на этот StateFlow и автоматически реагируют на переключение.

**When to use:** Всегда в multi-account приложениях. Альтернатива (передавать accountId явно в каждый вызов) приводит к багам «забыл прокинуть».

**Trade-offs:**
- (+) Переключение аккаунта = одна запись в Settings; UI перестраивается через Flow
- (+) Тестируется тривиально (фейк AccountStore)
- (−) Все репозиторные методы становятся «контекстными» — нужно понимать, что результат зависит от активного аккаунта в момент исполнения

**Example:**
```kotlin
class AccountStoreImpl(
    private val settings: Settings,          // multiplatform-settings, persisted
    private val secure: SecureCredentialStore,
) : AccountStore {

    private val _active = MutableStateFlow(loadActiveFromSettings())
    override val activeAccountId: StateFlow<AccountId?> = _active.asStateFlow()

    override suspend fun switchTo(id: AccountId) {
        require(secure.hasCredentials(id)) { "Account $id not registered" }
        settings.putString(KEY_ACTIVE, id.value)
        _active.value = id
    }

    override suspend fun listAccounts(): List<Account> = secure.allAccounts()

    override suspend fun addAccount(login: String, password: String, baseUrl: String): AccountId {
        val id = AccountId.generate()
        secure.put(id, Credentials(login, password, baseUrl))
        if (_active.value == null) switchTo(id)
        return id
    }
}
```

В каждом use case активный аккаунт — это `accounts.activeAccountId.first { it != null }` или `accounts.requireActiveAccountId()`. SyncCoordinator подписан на `activeAccountId.distinctUntilChanged()` и на каждое изменение — триггерит refresh репозиториев.

---

### Pattern 5: BackgroundScheduler через expect/actual (WorkManager ↔ BGTaskScheduler)

**What:** Общий интерфейс `BackgroundScheduler` в `:core:platform`, разные actual-реализации. Использовать готовую обёртку (`brewkits/kmpworkmanager` или `kprakash2/multiplatform-work-manager`) или свою тонкую.

**When to use:** Любой фоновый sync. У нас — periodic refresh оценок/расписания/ДЗ (и далее локальные нотификации о diff-е).

**Trade-offs:**
- (+) Бизнес-логика (что синхронизировать) живёт в common коде; платформа отвечает только за «когда разбудить»
- (−) iOS BGAppRefreshTask **не гарантирует** запуск; интервалы — рекомендация для iOS, не контракт. На Android WorkManager — гораздо надёжнее.
- (−) iOS требует регистрации task-identifier-ов в Info.plist при старте приложения; нельзя добавить динамически
- (−) Готовые KMP-обёртки ещё молодые (`kmpworkmanager` ~2025); HIGH risk выбрать «не ту» — рекомендую начать со своей тонкой обёртки и заменить, если экосистема созреет

**Example:**
```kotlin
// commonMain
interface BackgroundScheduler {
    suspend fun schedulePeriodicSync(taskId: String, intervalMinutes: Int)
    suspend fun cancel(taskId: String)
}

interface BackgroundTaskRunner {
    /** Зарегистрировать обработчик. Вызывается из MainActivity / iOS AppDelegate. */
    fun registerHandler(taskId: String, handler: suspend () -> Unit)
}

// androidMain — WorkManager
class AndroidBackgroundScheduler(private val context: Context) : BackgroundScheduler {
    override suspend fun schedulePeriodicSync(taskId: String, intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong(), MINUTES)
            .addTag(taskId).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(taskId, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

// iosMain — BGTaskScheduler
class IosBackgroundScheduler : BackgroundScheduler {
    override suspend fun schedulePeriodicSync(taskId: String, intervalMinutes: Int) {
        val request = BGAppRefreshTaskRequest(identifier = taskId).apply {
            earliestBeginDate = NSDate().dateByAddingTimeInterval(intervalMinutes * 60.0)
        }
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }
}
```

Ограничения **обязательно** проговорить пользователю в onboarding: «iOS может отложить обновления». Альтернативу (мгновенный push) мы выбили из scope сознательно (PROJECT.md / Key Decisions).

---

### Pattern 6: Koin `expect val platformModule` для DI

**What:** Один `commonModule` в `commonMain` декларирует все use cases, репозитории, ViewModels через интерфейсы. `expect val platformModule: Module` объявляется в commonMain, реализуется в androidMain/iosMain — возвращает реализации `SecureStore`, `BackgroundScheduler` и т.д.

**When to use:** Всегда. Это идиоматический KMP+Koin паттерн на 2026.

**Trade-offs:**
- (+) Common код видит только интерфейсы; никаких `expect class Repository`
- (+) Koin Annotations на 2026 тоже уже умеют KMP — можно перейти на них во второй итерации, если захочется меньше boilerplate
- (−) Koin запускается медленнее, чем dagger/kotlin-inject; для мобильного приложения этим можно пренебречь

**Example:**
```kotlin
// :core:platform/commonMain
expect val platformModule: Module

// :core:platform/androidMain
actual val platformModule: Module = module {
    single<SecureStore> { AndroidKeystoreSecureStore(get()) }
    single<BackgroundScheduler> { AndroidBackgroundScheduler(get()) }
    single<HttpClientEngine> { OkHttp.create() }
}

// :core:platform/iosMain
actual val platformModule: Module = module {
    single<SecureStore> { IosKeychainSecureStore() }
    single<BackgroundScheduler> { IosBackgroundScheduler() }
    single<HttpClientEngine> { Darwin.create() }
}

// composeApp
fun initKoin() = startKoin {
    modules(platformModule, networkModule, databaseModule, dataModule, featureModules)
}
```

---

## Data Flow

### Read Flow (UI отображает данные)

```
[GradesScreen composable]
        │ collectAsState()
        ▼
[GradesViewModel.state: StateFlow<UiState>]
        │ .stateIn(...)
        ▼
[Flow<List<Grade>>] ◄────────────────┐
        │ map domain                 │
        ▼                            │
[GradesRepository.observeGrades()]   │
        │                            │
        │ flatMapLatest on activeAccountId
        ▼                            │
[AccountStore.activeAccountId] ──► [SQLDelight: grades WHERE account_id = ?]
                                     │
                                     ▼
                                  [Disk]
```

### Write Flow (refresh из сети)

```
[Pull-to-refresh / SyncCoordinator timer / login event]
        │
        ▼
[GradesViewModel.onRefresh()]
        │ refresh use case
        ▼
[GradesRepository.refresh(): Result<Unit>]
        │
        ├──► [GradesRemoteSource.fetch] ─► Ktor (per-account HttpClient)
        │            │                          │
        │            ▼                          ▼
        │        [DTO]                      [АВЕРС server]
        │            │
        │            ▼
        │        [DTO → Domain mapper]
        │            │
        ▼            ▼
[GradesLocalSource.replaceGrades(account, list)] ─► SQLDelight transaction
        │
        ▼
[Триггерит emit в observeGrades()] ─► UI автоматически перерисовывается
```

### Multi-Account Switch Flow

```
[User taps "ребёнок Петя" в AccountSwitcher]
        │
        ▼
[AccountStore.switchTo(petyaId)]
        │
        ├──► [Settings.put(activeAccountId = petyaId)]
        │
        └──► [_active.value = petyaId]
                  │
                  └─► все Flow-ы, подписанные на activeAccountId, перепрыгивают
                       │
                       ├─► UI: observeGrades().flatMapLatest → новый Flow<List<Grade>> (Петины оценки из БД, мгновенно)
                       │
                       └─► SyncCoordinator: триггерит refresh для нового аккаунта (в фоне)
                               │
                               └─► HttpClientFactory.forAccount(petyaId) → берёт кешированный клиент с Петиными cookies
```

### Background Sync Flow

```
[OS wakes up app: WorkManager (Android) / BGTaskScheduler (iOS)]
        │
        ▼
[BackgroundTaskRunner.handler] (зарегистрирован в App.onCreate / iOS appDidFinishLaunching)
        │
        ▼
[SyncCoordinator.runScheduledSync()]
        │
        ├─► forEach (account in accounts.listAccounts()):
        │       │
        │       ├─► gradesRepo.refresh(account)
        │       ├─► scheduleRepo.refresh(account)
        │       └─► homeworkRepo.refresh(account)
        │
        ├─► [DiffDetector] сравнивает new state vs previous (из notifications_state таблицы)
        │
        └─► [Notifier.show("Новая оценка по математике у Пети")]
              (expect/actual: NotificationManagerCompat / UNUserNotificationCenter)
```

### State Management

```
┌─ AccountStore.activeAccountId: StateFlow<AccountId?>  ◄── persisted in Settings
│
├─ UI State (per screen):
│   GradesViewModel.state: StateFlow<GradesUiState>
│       = Loading | Loaded(grades, isRefreshing, lastSync) | Error(message, isOffline)
│   .stateIn(WhileSubscribed(5_000)) ─ держит данные 5с после ухода с экрана
│
├─ Sync State (глобальный):
│   SyncCoordinator.syncStatus: StateFlow<SyncStatus>
│       = Idle | InProgress(account, feature) | Failed(error)
│
└─ Connectivity:
    ConnectivityMonitor.isOnline: StateFlow<Boolean> (expect/actual)
```

### Key Data Flows

1. **Cold start, online:** UI открывается → observeGrades() возвращает что есть в БД (мгновенно) → SyncCoordinator триггерит refresh → repository пишет в БД → UI обновляется.
2. **Cold start, offline:** UI открывается → observeGrades() возвращает кэш → SyncCoordinator видит `!isOnline`, ждёт → как только online, refresh.
3. **Switch account:** AccountStore.switchTo → activeAccountId.flatMapLatest → новый Flow из БД (мгновенно) + фоновый refresh.
4. **Background wake:** OS триггерит worker → SyncCoordinator опрашивает все аккаунты → diff detector → локальная нотификация.
5. **Logout single account:** Repository.deleteAccount(id) → удаление БД-записей этого аккаунта → SecureCredentialStore.remove(id) → HttpClientFactory.evict(id) → если был активным, AccountStore.switchTo(first remaining or null).

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0–100 пользователей (v1, школа №28) | Текущая архитектура. Один SQLDelight-файл на аккаунт. Один Ktor-клиент на аккаунт. Никаких оптимизаций — просто корректная реализация. |
| 100–10k пользователей (v1.x) | То же, плюс crash reporting (Firebase Crashlytics — KMP support через GitLive или `firebase-kotlin-sdk`), аналитика метрик sync (success rate per account). |
| 10k+ или multi-school (v2) | Конфигурируемый baseUrl per-account уже есть. Добавить server-discovery (выбор инсталляции АВЕРС). Расщепить SQLDelight-файл на (per-school × per-account). Если появится backend-прокси — добавить FCM/APNs ветку рядом с polling. |

### Scaling Priorities

1. **Первый bottleneck — iOS background tasks unreliability.** Что ломается: пользователи жалуются на «оценки приходят с задержкой». Решение: (a) проговорить ограничение в onboarding; (b) если станет критично — backend-прокси с APNs (revisit Key Decision из PROJECT.md).
2. **Второй bottleneck — БД растёт безгранично.** При длительном использовании оценки/расписание за годы накопятся. Решение: retention-политика на уровне SyncCoordinator (хранить только текущий + предыдущий учебный год, остальное архивировать или удалять).
3. **Третий bottleneck — параллельный sync N аккаунтов на iOS.** BGAppRefreshTask имеет жёсткий timeout (~30c). При 5+ аккаунтах sync может не успевать. Решение: round-robin (один аккаунт за wake), или приоритизация активного.

---

## Anti-Patterns

### Anti-Pattern 1: «Single mega-shared module»

**What people do:** Кладут всё в один `:shared` модуль с папками `network/`, `database/`, `ui/`. Заводят один Koin-модуль на весь проект.
**Why it's wrong:** На 5+ фичах перекомпиляция замедляется драматически (Touchlab подтверждает: linkDebugFrameworkIos плохо инкрементальный); тесты текут (изменил кнопку → перетестируй сеть); нарушаются boundaries — UI начинает дёргать DAO напрямую.
**Do this instead:** Feature-by-layer с самого начала. Boilerplate на старте — да, но при росте проекта окупается. Для нашего долгосрочного проекта это не дискутируется.

### Anti-Pattern 2: «Cookies в памяти, перелогин при рестарте»

**What people do:** Используют дефолтный `AcceptAllCookiesStorage` (in-memory), не персистят cookies.
**Why it's wrong:** Каждый рестарт приложения = re-login = удар по серверу АВЕРС, риск rate-limit / CAPTCHA, плохой UX.
**Do this instead:** `PersistentCookiesStorage` per-account, хранит cookies в SQLDelight-таблице. При старте приложения cookies уже есть; re-login только если получили 401/302 на login.

### Anti-Pattern 3: «expect class» вместо «expect val + interface»

**What people do:**
```kotlin
expect class SecureStore() { fun get(key: String): String? }
```
**Why it's wrong:** `expect class` сложнее тестировать (нельзя замокать через интерфейс), плохо работает с DI (Koin не понимает «expect class»), требует совпадения сигнатур конструктора между платформами (а нативно Keychain нужен `String accessGroup`, Keystore — `Context`).
**Do this instead:**
```kotlin
// commonMain
interface SecureStore { fun get(key: String): String? }
expect val platformModule: Module    // содержит binding SecureStore → impl

// androidMain
class AndroidSecureStore(ctx: Context) : SecureStore { ... }
actual val platformModule = module { single<SecureStore> { AndroidSecureStore(get()) } }
```

### Anti-Pattern 4: «Пробрасывание accountId явно через все use cases»

**What people do:** `getGrades(accountId: AccountId): Flow<List<Grade>>` — accountId параметр в каждом методе.
**Why it's wrong:** Утекает в UI; ViewModel должна где-то взять accountId; забыл прокинуть → баг с показом не тех данных. UI вообще не должен знать про accountId.
**Do this instead:** AccountStore — domain-level контекст. Use cases читают активный аккаунт сами. Excplicit accountId передаём только в admin-операции (DeleteAccount, MergeAccounts), не в read-операции.

### Anti-Pattern 5: «UI subscribes to network»

**What people do:** ViewModel вызывает `repo.fetchGrades()` → получает результат → выставляет в state. БД либо не используется, либо используется как «cache» сбоку.
**Why it's wrong:** Offline ломается, гонки (refresh пришёл позже, чем переключение аккаунта — увидели чужие оценки), сложно тестировать.
**Do this instead:** UI наблюдает `repo.observeGrades()` (Flow из БД). Сеть — это `repo.refresh()`, который пишет в БД. UI и refresh — независимы.

### Anti-Pattern 6: «Voyager + Navigation 3 в одном проекте»

**What people do:** Начинают с Voyager (проще), потом для каких-то экранов используют Navigation 3.
**Why it's wrong:** Два back stack-а конкурируют за back-press; lifecycle разный; deep links дублируются.
**Do this instead:** Выбрать один. На 2026 — Navigation 3 (alpha, но это путь JetBrains; ставить на коня, который выиграет). Если рискованно — `navigation-compose 2.9.x` (beta, но стабильнее).

### Anti-Pattern 7: «Один HttpClient, переключаемый между аккаунтами»

**What people do:** Один `HttpClient` + плагин, который для каждого запроса меняет cookie storage / Authorization header.
**Why it's wrong:** Гонки при concurrent refresh нескольких аккаунтов (фоновый sync для Пети vs пользователь смотрит Машу). Утечка cookies между аккаунтами при race-condition.
**Do this instead:** `HttpClientFactory.forAccount(id)` — отдельный клиент с отдельным storage. Pattern 3 выше.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| ИАС АВЕРС server (`journal.school28-kirov.ru`) | Ktor HttpClient per-account, persistent cookies, JSON via kotlinx.serialization (`ignoreUnknownKeys = true`) | API не публичный — реверсить через DevTools/mitmproxy. Ожидать ExtJS-style endpoints (`/api/...?action=...&format=json`). CSRF токен / hidden form fields возможны. |
| iOS Keychain | `Security.framework` через cinterop (или `multiplatform-settings-no-arg` keychain backend) | accessGroup = bundle id; kSecAttrAccessibleAfterFirstUnlock |
| Android Keystore + EncryptedSharedPreferences | `androidx.security:security-crypto` | Master key в Keystore; данные в EncryptedSharedPreferences |
| Android WorkManager | `androidx.work:work-runtime-ktx`; `CoroutineWorker` | Periodic sync; constraints (network, battery-not-low) |
| iOS BGTaskScheduler | `BackgroundTasks.framework` через cinterop | Регистрация identifier-ов в Info.plist обязательна; ОС отложит, если приложение редко используется |
| Local notifications | Android: `NotificationManagerCompat`; iOS: `UNUserNotificationCenter` | Permission request на iOS — UX-сценарий нужен в onboarding |
| Biometric prompt (опционально) | Android: `androidx.biometric`; iOS: `LocalAuthentication.framework` | Защищает разблокировку аккаунта |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `:feature:*:ui` ↔ `:feature:*:domain` | Прямые вызовы use cases (через DI) | UI не знает про data слой. Только domain. |
| `:feature:*:domain` ↔ `:feature:*:data` | Через Repository интерфейс (объявлен в domain, реализован в data) | Dependency Inversion — классика clean arch |
| `:feature:*:data` ↔ `:core:network` | Прямой импорт (Ktor HttpClient) | data-слой знает про Ktor; ui/domain — нет |
| `:feature:*:data` ↔ `:core:database` | Прямой импорт (SQLDelight DAO) | Аналогично |
| `:feature:*:data` ↔ `:core:platform` | Через DI (интерфейсы SecureStore, etc.) | Не зависит напрямую от модуля platform — только от интерфейсов в `:core:domain` или `:core:platform/api` |
| Все слои ↔ `:core:domain` | Импорт моделей и интерфейсов | core:domain — самый «нижний», ни от чего не зависит |
| `composeApp` ↔ всё | Импорт ВСЕГО (для Koin wiring и navigation) | Единственное место, где видны все feature-модули |

---

## Build Order Implications для Roadmap

Зависимости диктуют порядок построения. Идём снизу вверх:

### Уровень 0 — Скелет проекта (фундамент, без него ничего не строится)

1. **Gradle multi-module setup** + convention plugins (`build-logic/`)
2. **`:core:domain`** — пустой, но с базовыми типами (Result, AppError, AccountId)
3. **`:core:platform`** — каркас expect/actual для SecureStore, Clock; platformModule

**Почему первым:** все остальные модули зависят от core:domain. Convention plugins сэкономят часы потом.

### Уровень 1 — Инфраструктура данных

4. **`:core:database`** (SQLDelight) — схема (пустая или минимальная), DatabaseFactory
5. **`:core:network`** (Ktor) — HttpClientFactory, базовые плагины
6. **`:core:data`** — AccountStore (на multiplatform-settings), SecureCredentialStore (на :core:platform)

**Почему здесь:** все features будут использовать БД, сеть, AccountStore. Сначала пробрасываем «трубы».

### Уровень 2 — Auth (ключевой gateway)

7. **`:feature:auth:domain`** — Login, AddAccount, SwitchAccount use cases, AuthRepository interface
8. **`:feature:auth:data`** — реверс-инжиниринг АВЕРС login flow, AuthRepositoryImpl
9. **`:feature:auth:ui`** — экран логина (минимальный), AccountSwitcher

**Почему здесь:** без авторизации остальные features не имеют данных. Первый end-to-end путь: login → cookies в БД → запрос к АВЕРС → JSON.

### Уровень 3 — UI shell + первая фича

10. **`:core:ui`** — тема, AppScaffold, AccountSwitcher composable
11. **composeApp скелет** — App composable, Navigation 3 setup, Koin init
12. **`:feature:grades`** (полная вертикаль) — первая «настоящая» фича; на ней отлаживаем offline-first паттерн end-to-end

**Почему grades первой:** оценки — самая ценная фича по PROJECT.md core value, наименее зависит от UI-сложности (просто список), хороший пилот для Pattern 1 (single source of truth).

### Уровень 4 — Остальные фичи (параллельно, копируют паттерн grades)

13. **`:feature:schedule`** — расписание
14. **`:feature:homework`** — домашние задания

### Уровень 5 — Background sync + notifications

15. **`:core:platform`** дополняем BackgroundScheduler + Notifier expect/actual
16. **`:core:data`** — SyncCoordinator, DiffDetector, ConnectivityMonitor
17. Wiring в composeApp (Android: WorkManager init; iOS: BGTask registration в Info.plist + AppDelegate)

**Почему в конце:** требует, чтобы фичи уже были (что синхронизировать?) и notification-таблицы в БД (с чем сравнивать?). Делать раньше — оптимизация без юзкейса.

### Уровень 6 — Polish

18. Biometric prompt (опционально)
19. Error states / OfflineBanner / pull-to-refresh шлифовка
20. Privacy Manifest (iOS), Data Safety (Android), политика конфиденциальности
21. Crash reporting

### Критическая зависимость по слоям (правило)

```
core:domain  ◄── ничего; всё зависит от него
   ▲
   │
core:platform (api) ◄── core:data, feature:*:data
   ▲
   │
core:network, core:database, core:data  ◄── feature:*:data
   ▲
   │
feature:*:domain  ◄── feature:*:ui
   ▲
   │
core:ui  ◄── feature:*:ui, composeApp
   ▲
   │
feature:*:ui  ◄── composeApp (только composeApp видит все features)
   ▲
   │
composeApp (umbrella для iOS XCFramework)
```

**Никогда не строить feature:ui раньше, чем feature:domain раньше, чем feature:data.** Это правило часто нарушают, начиная с UI-моков; для нашего offline-first проекта это плохо — мок-данные не покажут реальные паттерны Flow/StateFlow.

---

## Sources

- [Compose Multiplatform 1.10.0 release — Navigation 3](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/)
- [Compose Multiplatform 1.8.0 — iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- [Common ViewModel | Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Lifecycle | Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html)
- [Navigation 3 in Compose Multiplatform | Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Kotlin Multiplatform Architecture Best Practices for Mobile Apps — Carrion.dev](https://carrion.dev/en/posts/kmp-architecture/)
- [Compose Multiplatform: Multi-Module App Using Clean Architecture — Murali M (Medium)](https://medium.com/@muralivitt/compose-multiplatform-multi-module-app-using-clean-architecture-303d3d424067)
- [Scaling Kotlin Multiplatform: A Feature-Oriented Clean Architecture for Real Products — Fadi Bouteraa (Medium)](https://medium.com/@fadibouteraa/scaling-kotlin-multiplatform-a-feature-oriented-clean-architecture-for-real-products-2a838ddf1314)
- [Optimizing Gradle Builds in Multi-module Projects — Touchlab](https://touchlab.co/optimizing-gradle-builds-in-Multi-module-projects)
- [Modularizing a Kotlin Multiplatform Mobile Project — akjaw](https://akjaw.com/modularizing-a-kotlin-multiplatform-mobile-project/)
- [Kotlin Multiplatform Scalability Challenges on a Large Project — ProAndroidDev](https://proandroiddev.com/kotlin-multiplatform-scalability-challenges-on-a-large-project-b3140e12da9d)
- [My Compose Multiplatform Project Structure — Dalen Codes](https://dalen.codes/p/my-cmp-project-structure)
- [The Complete Guide to Offline-First Architecture in Android — droidcon](https://www.droidcon.com/2025/12/16/the-complete-guide-to-offline-first-architecture-in-android/)
- [Offline-First Android App Architecture: 2026 Expert Guide — Zignuts](https://www.zignuts.com/blog/android-architecture-with-jetpack-compose-and-kotlin)
- [Designing Reliable Offline-First Android Apps — Kayvan Kaseb (Medium, Jan 2026)](https://medium.com/kayvan-kaseb/designing-reliable-offline-first-android-apps-d50b92d0d6ec)
- [Offline-First Sync + Secure Storage + Deploying KMP Apps — Ignatiah Xavier](https://medium.com/@ignatiah.x/offline-first-sync-secure-storage-deploying-kmp-apps-play-store-testflight-7be9b6ba3d56)
- [How to Design a Scalable Repository Pattern with Kotlin Flows (2025 Guide)](https://medium.com/@sheikhrehman5870/how-to-design-a-scalable-repository-pattern-with-kotlin-flows-2025-guide-88c587848441)
- [Database Solutions for KMP/CMP: SQLDelight vs Room — Murali M](https://medium.com/@muralivitt/database-solutions-for-kmp-cmp-sqldelight-vs-room-ea9a52c7bce7)
- [Local Database: Comparing Realm, SQLDelight, and Room — ProAndroidDev](https://proandroiddev.com/which-local-database-should-you-choose-in-2025-comparing-realm-sqldelight-and-room-4221b354c899)
- [Background Sync in Kotlin Multiplatform: WorkManager + BGTask — Ignatiah Xavier](https://medium.com/@ignatiah.x/background-sync-in-kotlin-multiplatform-workmanager-android-background-tasks-ios-1f92ad56d84b)
- [Sleeping But Working: Cross-Platform Background Sync with KMP — KMP Bits](https://www.kmpbits.com/posts/background-sync-kmp)
- [Thomas Kioko — Background Tasks in Kotlin Multiplatform](https://thomaskioko.me/posts/kmp_background_tasks/)
- [KMP WorkManager — brewkits/kmpworkmanager](https://github.com/brewkits/kmpworkmanager)
- [Cookies | Ktor Documentation](https://ktor.io/docs/client-cookies.html)
- [CookiesStorage — Ktor API](https://api.ktor.io/ktor-client/ktor-client-core/io.ktor.client.plugins.cookies/-cookies-storage/index.html)
- [KMP Advanced Patterns | Koin docs](https://insert-koin.io/docs/reference/koin-mp/kmp/)
- [Achieving Platform-Specific Implementations with Koin in KMM — ProAndroidDev](https://proandroiddev.com/achieving-platform-specific-implementations-with-koin-in-kmm-5cb029ba4f3b)
- [Voyager — adrielcafe/voyager](https://github.com/adrielcafe/voyager)
- [Decompose — official docs](https://github.com/arkivanov/Decompose) (referenced via comparison articles)
- [Navigating the Waters of Kotlin Multiplatform: Exploring Navigation Solutions — droidcon (2024)](https://www.droidcon.com/2024/04/09/navigating-the-waters-of-kotlin-multiplatform-exploring-navigation-solutions/)

---
*Architecture research for: Compose Multiplatform on-device-only multi-account client to legacy SPA-API*
*Researched: 2026-04-27*
