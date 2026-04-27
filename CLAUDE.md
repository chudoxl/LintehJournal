<!-- GSD:project-start source:PROJECT.md -->
## Project

**ЛИнТех Дневник**

Кроссплатформенный мобильный клиент (iOS + Android) для электронного дневника **ИАС «АВЕРС: Электронный Классный Журнал» v4.1** (`journal.school28-kirov.ru`), целевая аудитория — ученики и родители школы №28 г. Кирова. Приложение работает напрямую с сервером АВЕРС без собственного backend; все пользовательские данные и кэш живут на устройстве. Реализуется на Compose Multiplatform — общий Kotlin‑код и UI на iOS и Android. **v1 распространяется в семейном/классном кругу через TestFlight и Google Play Internal track как личное использование**; публичная публикация в App Store / Google Play и формальные согласования (школа, РКН) — отдельный милестон v2 после первой стабильной сборки.

**Core Value:** **Удобный, быстрый и оффлайн-доступный мобильный доступ к оценкам, расписанию и домашним заданиям с поддержкой нескольких учеников в одном приложении** — то, чего не даёт ExtJS-сайт АВЕРС в мобильном браузере. Если из всех функций выживет только это — приложение всё равно решает проблему.

### Constraints

- **Tech Stack**: Compose Multiplatform (Kotlin 2.x + Compose Multiplatform 1.7+) — Принято заранее как осознанный технический выбор; обоснование: единый код и UI для iOS+Android, JetBrains-стек, перспектива зрелой платформы.
- **Architecture**: On-device, без собственного backend — Снижает операционные расходы, упрощает развёртывание, исключает классы ошибок (downtime сервера, миграции БД); цена — ограниченные push-уведомления.
- **Backend Source**: Сервер АВЕРС — единственный источник данных; недоступен для модификации с нашей стороны. API не публичный — придётся реверсить SPA-запросы.
- **Distribution v1**: TestFlight (iOS) + Google Play Internal track (Android) для семейного/классного круга — Apple Developer Program всё равно требуется ($99/год); Google Play Console ($25) тоже; **публичный submission отложен в v2** (см. Out of Scope).
- **Security**: Хранение чужих учётных данных (АВЕРС login/password) — обязателен iOS Keychain / Android Keystore, опционально biometric prompt; шифрование at-rest и in-transit; HTTPS-only.
- **Offline**: Все основные экраны (оценки, расписание, ДЗ) должны открываться без сети — необходим персистентный локальный кэш с инвалидацией.
- **Push**: Без backend — мгновенный push невозможен; используется фоновый polling (Android `WorkManager`, iOS `BGAppRefreshTask`) + локальные нотификации; задержки до нескольких часов на iOS приняты.
- **Legal/Privacy в v1**: Использование рассматривается как личное/семейное (без публичного релиза); политика конфиденциальности и iOS Privacy Manifest всё равно подготавливаются (best practice). **Формальная регистрация в РКН и письменное согласие школы №28 — отложены в v2** при подготовке к публичному релизу.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## TL;DR — Recommended Stack (one-liner)
## Compose Multiplatform для iOS — Production-Readiness в 2026
- Type-safe навигация с deep linking (Navigation 3 — stable на Android, alpha на iOS на 1.10.3)
- Native iOS scrolling physics, text editing с native selection, drag-and-drop
- VoiceOver, AssistiveTouch, Full Keyboard Access — accessibility
- iOS-specific IME customization (UIKit text input traits — keyboard type, autocorrection, return key) — с 1.9
- Swipe-back / end-edge pan gestures (`EndEdgePanGestureBehavior`) — с 1.10
- UIKit-interop с автоматическим sizing (intrinsic content size) — с 1.10
- Startup time сравним с native приложениями
- @Preview-аннотация унифицирована между платформами (с 1.10)
- Compose Hot Reload — bundled и stable (с 1.10)
| Ограничение | Влияние на проект | Митигация |
|---|---|---|
| Нет нативных iOS-компонентов («Cupertino kit») — всё в Material | UI выглядит «Android-like» на iOS | Принять как trade-off; либо стилизовать под нейтральный дизайн; либо вкраплять SwiftUI через UIKit interop для критичных мест |
| Высокий CPU/память на iOS в edge cases (issue #4912) | Может проявиться на расписании с большим списком | Профилировать на ранних версиях; избегать deeply-nested Composables и тяжёлого Canvas-рисования |
| Accessibility не на уровне SwiftUI; XCTest не «видит» Compose-views | Затруднён полный native UI-test на iOS | Использовать Compose UI Test API в commonTest; для smoke-тестов на iOS — отдельно SwiftUI-обёртка вокруг entry-point |
| Visual debugging на iOS — Xcode не инспектирует Compose-views | Больше времени на debug iOS-only багов | Использовать Compose Layout Inspector в Android Studio; на iOS — печатные дампы, Kermit-логирование |
| OS-level capabilities (notifications, background, biometrics, keychain) требуют platform code | Часть проекта будет в `iosMain`/`androidMain` через `expect/actual` | Это норма для KMP; ниже подобраны библиотеки, скрывающие большую часть работы |
| Push-уведомления через FCM/APNs требуют backend | Уже принято в Out of Scope; используется фоновый polling | Alarmee/KMPNotifier для local notifications + WorkManager/BGTaskScheduler |
## Recommended Stack
### Core Technologies
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Kotlin** | **2.2.20** | Язык | Рекомендуемая JetBrains версия для проектов с iOS/Web таргетами; Kotlin 2.x stabилен с 2024, K2-компилятор — производительность |
| **Compose Multiplatform** | **1.10.3** | UI framework | Stable iOS support; latest stable 1.10 series; bundled Hot Reload; unified @Preview; Navigation 3 support |
| **Ktor Client** | **3.4.3** | HTTP-клиент | KMP-first, HttpCookies-плагин для session-cookie auth (АВЕРС), Darwin-engine на iOS = нативный NSURLSession, OkHttp-engine на Android, kotlinx.serialization-интеграция |
| **kotlinx.serialization** | **1.9.0** | JSON | Стандартный JSON-сериализатор Kotlin, KMP-first, интеграция с Ktor через `ContentNegotiation`, поддержка `@SerialName`, `JsonElement` для динамичных схем АВЕРС |
| **kotlinx-datetime** | **0.8.0-rc01** (или ≥0.7.x stable, см. примечание) | Даты/время | Официальная KMP-library для `Instant`/`LocalDate`/`TimeZone`; `Instant`-сериализация требует kotlinx.serialization 1.9+; критично для расписания/ДЗ |
| **AndroidX Room (KMP)** | **2.8.x** | Локальный кэш / БД | С 2.7+ KMP-first, поддержка iOS (`kspIosArm64`, `kspIosSimulatorArm64`, `kspIosX64`), JVM. Production-ready в 2026 (анонс Google I/O 2025). Android-разработчику знаком, type-safe DAO, Flow-интеграция |
| **AndroidX Navigation 3** | **navigation3-runtime 1.0.0-alpha08** | Навигация | На Android — stable; на iOS — alpha (но рекомендуется JetBrains). Type-safe destinations, прямая манипуляция стеком. Альтернатива — Decompose (см. Alternatives). Для пары Android+iOS Navigation 3 — путь, обозначенный JetBrains |
| **AndroidX Lifecycle ViewModel (KMP)** | **org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0** | Презентационный слой | Stable KMP-support с 2.9.0 (май 2025); работает в `commonMain` под Compose Multiplatform; SavedStateHandle для restore-сценариев |
| **Koin** | **4.x** (4.0+) | DI | KMP-first, минимум boilerplate, нет KSP-кодогена → быстрая компиляция. Простая интеграция Compose (`KoinApplication`, `koinInject()`). Подходит для проекта одного разработчика |
### Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **multiplatform-settings (no-arg + russhwolf-settings-coroutines)** | **1.3.x** | Простой key-value (не секретный) | Хранение non-sensitive prefs: выбранный аккаунт, локаль, тема, дата последнего polling. На iOS = `NSUserDefaults`, на Android = `SharedPreferences`. Через `make-observable` обёртку — Flow-интеграция |
| **multiplatform-settings KeychainSettings** | **1.3.x** (часть того же артефакта, `multiplatform-settings`) | Secure storage credentials | Для iOS — `KeychainSettings(service = "...")`. **Внимание:** на iOS статус документирован как «experimental», но используется в production многими; на Android этого класса нет — нужен отдельный путь (см. ниже) |
| **EncryptedSharedPreferences (Android) + KeychainSettings (iOS)** через `expect/actual` | androidx.security-crypto **1.1.0-alpha07** (Jetpack security, замораживается) ИЛИ **Tink** для AES-GCM + Keystore-обёртка | Cross-platform secure storage для логин/пароль АВЕРС | На iOS — `KeychainSettings` (Keychain). На Android — Jetpack Security `EncryptedSharedPreferences` (deprecated, но рабочая) ИЛИ собственная обёртка Keystore + AES-GCM. **Альтернатива (рекомендуется):** **KVault** (Liftric) или **KSafe** — KMP-библиотеки, инкапсулирующие Keychain/Keystore с AES-256-GCM. KSafe — современнее (hardware-backed) |
| **KVault (Liftric)** | **latest** | Cross-platform Keychain/Keystore wrapper | **Рекомендуется как основной путь хранения credentials.** Wraps Keychain on iOS, EncryptedSharedPreferences on Android. Альтернатива: **KSafe** (более современная, hardware-backed AES-256-GCM, активная разработка) |
| **moko-biometry** | **0.4.x** (icerockdev/moko-biometry) | Biometric prompt (Face ID / fingerprint) | KMP-first, Compose Multiplatform support (Android+iOS). Один API через `BiometryAuthenticator.checkBiometryAuthentication()`. Альтернатива — собственный `expect/actual` поверх AndroidX BiometricPrompt + LocalAuthentication framework |
| **Alarmee** | **2.4.0** (Tweener/alarmee) | Local notifications + scheduling | KMP-обёртка над AlarmManager (Android) и UNUserNotificationCenter (iOS). Единый API для one-off и repeating локальных нотификаций. Идеально для проекта без backend |
| **WorkManager (Android)** + **BGTaskScheduler (iOS)** через `expect/actual` или **kprakash2/multiplatform-work-manager** | androidx.work **2.10+** | Фоновый polling АВЕРС | На Android — periodic `WorkRequest` (минимум 15 мин); на iOS — `BGAppRefreshTask` (регистрируется в `application(_:didFinishLaunchingWithOptions:)`). Готовые KMP-обёртки молодые, **рекомендуется писать `expect/actual` напрямую** — контроль точнее, дополнительной зависимости нет |
| **Coil 3** | **3.x** (coil-compose, coil-network-ktor) | Загрузка изображений | KMP-first с Coil 3.0; AsyncImage/SubcomposeAsyncImage в commonMain; интеграция с Ktor (использует тот же HTTP-клиент); диск+memory кэш. Для АВЕРС — может потребоваться для аватарок/прикреплённых файлов ДЗ |
| **Kermit** | **2.0.4+** (touchlab/Kermit) | Логирование | KMP-first, multi-platform: Logcat (Android), OSLog (iOS), Println-fallback (Desktop). Уровни, теги, severity-фильтры. Активно поддерживается Touchlab |
| **Compose Multiplatform Resources** | bundled | Строки/изображения/шрифты | Встроенный механизм `compose.components.resources` — `Res.string.xxx`, `Res.drawable.xxx`, `Res.font.xxx`. Альтернатива moko-resources устарела (см. Alternatives) |
| **Ktor Logging plugin + ContentNegotiation + HttpCookies + DefaultRequest + HttpTimeout** | bundled с Ktor | HTTP-клиент конфиг | Стандартный набор: cookies (session АВЕРС), JSON, base URL, таймауты, debug-логирование |
### Development Tools
| Tool | Purpose | Notes |
|------|---------|-------|
| **Gradle Version Catalog** (`libs.versions.toml`) | Управление версиями | Single source of truth — обязательно для KMP-проекта |
| **KSP** (Kotlin Symbol Processing) | Кодогенерация | Требуется для Room. Версии: KSP должна совпадать с Kotlin (см. ksp.dev) |
| **Compose Hot Reload** | Iteration speed | Bundled stable с 1.10.0 |
| **Xcode 16+** | iOS build/sign | Для production-сборки и распространения через App Store. Нужен Apple Silicon Mac или MacOS-CI (например, EAS, Bitrise, GitHub macOS-runners) |
| **Android Studio Hedgehog/Iguana+ или IntelliJ IDEA Ultimate** | KMP IDE | Kotlin Multiplatform plugin (с 2025 — отдельный, не KMM). На Linux/Windows плагин расширяется в 2026 |
| **Mokkery** | Mocking | Compiler-plugin-driven, **используется вместо MockK на iOS-таргетах** (MockK не работает с Kotlin/Native) |
## Testing Stack
| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| **kotlin.test** | matches Kotlin | Базовые ассерты в `commonTest` | Достаточно для unit-тестов модели/репозиториев |
| **Kotest** | **5.9+** | Расширенные ассерты, property-based testing | Полный multi-platform support (JVM/JS/Native/Wasm). Используем как ассертный DSL поверх kotlin.test runner |
| **Turbine** | **1.2+** | Тестирование `Flow` | Стандарт-де-факто для Flow-тестов. Корректное cancellation/timing |
| **kotlinx-coroutines-test** | matches kotlinx.coroutines | `runTest`, `TestDispatcher` | Для coroutine-тестов |
| **Mokkery** | latest | Mocking (KMP-friendly) | **Альтернатива MockK для KMP** — MockK не поддерживает Kotlin/Native. Compiler-plugin, без KSP |
| **Compose UI Test (commonTest)** | bundled с CMP 1.10 | UI-тесты композиций | `runComposeUiTest { ... }` в commonTest. Использовать `testTag` модификаторы вместо текстовых селекторов |
| **Ktor MockEngine** | bundled с Ktor | Mock HTTP-ответов АВЕРС | Подменяет engine; идеально для тестирования парсеров и репозиториев |
## Installation (libs.versions.toml фрагмент)
# Ktor
# Serialization / datetime
# Room KMP
# Navigation3
# Lifecycle ViewModel KMP
# Koin
# Storage / secure
# Biometry / notifications
# Image / logging
# Work / background (androidMain)
# Tests
## Alternatives Considered
| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **Room 2.8.x KMP** | **SQLDelight 2.x** | Если хочется писать «голый» SQL без аннотаций и иметь schema-first подход; SQLDelight генерирует Kotlin из `.sq`-файлов, type-safe. Более «KMP-native» исторически. **Не рекомендую**, потому что Room теперь 1st-class KMP, разработчик уже знаком с ним из Android, и `Flow`-интеграция, миграции, suspend-DAO — менее boilerplate-нагружены |
| **Koin 4.x** | **kotlin-inject** | Если важна compile-time-валидация графа зависимостей (Dagger-стиль) — kotlin-inject это даёт через KSP. Для крупных команд с большим графом. **Не рекомендую**: проект — соло, граф небольшой, build-time важнее compile-time-проверок |
| **Koin 4.x** | **Kodein-DI** | Если архитектура очень сложная и нужна продвинутая конфигурация (multiple containers, complex bindings). Для простых-средних проектов overkill |
| **AndroidX Navigation 3 (alpha на iOS)** | **Decompose** | Если важна decoupled от Compose навигация и shared business logic в `Component`-объектах (хорошо для unit-тестов навигации, для «pure Kotlin VM» подхода). Decompose стабильнее на iOS на сегодня, но тащит свою VM-философию. Стоит выбрать его, если стек ViewModel + Navigation 3 не устроит на iOS-alpha |
| **AndroidX Navigation 3** | **Voyager** | Если нужна простая навигация, тесно связанная с Compose (Screen-объекты, ScreenModel). Подойдёт мелким приложениям. **Меньше инвестиций JetBrains/Google** в долгосрочной перспективе |
| **multiplatform-settings + KVault** | **KSafe** | Более современный конкурент KVault: hardware-backed AES-256-GCM, активная разработка, Flow/StateFlow API. Если KVault выглядит «застывшим» при оценке — переходить на KSafe. KVault выбран как «проверенный временем» |
| **moko-biometry** | Собственный `expect/actual` поверх AndroidX BiometricPrompt + LocalAuthentication.framework | Если требуется тонкий контроль (custom error handling, специфичные `LAContext`-настройки на iOS). Для типового сценария moko-biometry достаточен |
| **Alarmee** | **KMPNotifier** | Если планируется добавить FCM push (потребует backend) — KMPNotifier даёт unified push API. Для on-device-only — Alarmee проще и точнее ложится на use-case |
| **Compose Multiplatform Resources** | **moko-resources** | Если потребуется multi-module ресурсы или мощное plurals/format API — moko исторически богаче. **Но** moko-resources в 2025-26 практически заморожен; CMP-resources быстро догнал базовые потребности. Использовать moko только при явной нехватке |
| **Mokkery** | **Mockative**, **MocKMP** | Все три — KSP-/compiler-plugin моки для KMP. Mokkery лучше всего документирован и идиоматичнее. Mockative — если нужна KSP-only (без Kotlin compiler plugin) |
| **kotlin.test + Kotest assertions** | Полный Kotest runner | Полный Kotest runner мощнее (BDD-стили, property), но усложняет setup в KMP. Для соло-проекта избыточно |
## What NOT to Use
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **MockK на iOS-таргетах** | Работает только на JVM; на Kotlin/Native падает | **Mokkery** (или Mockative/MocKMP) |
| **moko-resources как новый выбор** | Практически заморожен; последний минор > года назад; CMP-resources покрывает 80% сценариев | **Compose Multiplatform Resources** (`compose.components.resources`) |
| **SharedPreferences (raw) для credentials на Android** | Незашифровано, виден root-доступу | **KVault / KSafe / EncryptedSharedPreferences через `expect/actual`** |
| **NSUserDefaults для credentials на iOS** | Не зашифровано, доступно через `defaults read` или backup | **iOS Keychain через KVault / KeychainSettings** |
| **Ktor CIO engine на mobile** | Только HTTP/1.1 в 2026; не поддерживает HTTP/2 | **Darwin (iOS) + OkHttp (Android)** — оба HTTP/2 out-of-box, нативные системные API, корректные таймауты, proper proxy support |
| **Gson / Moshi** | JVM-only; нет KMP-варианта | **kotlinx.serialization** |
| **java.time / Joda-Time** | JVM-only | **kotlinx-datetime** |
| **Hilt** | Android-only (KSP-кодоген на Android) | **Koin / kotlin-inject** |
| **Jetpack Compose Navigation 2.x в commonMain** | Multiplatform-вариант обходился через workarounds; deprecated в пользу Navigation 3 | **Navigation 3** (с принятием alpha-статуса на iOS) или **Decompose** |
| **Realm / Realm Kotlin** | Платная лицензия для production от MongoDB; KMP-поддержка спорадическая после поглощения | **Room KMP** (или SQLDelight) |
| **OkHttp напрямую (без Ktor)** | Android-only; нет iOS-инкапсуляции | **Ktor Client** (использует OkHttp на Android как engine) |
| **AndroidX Security `EncryptedSharedPreferences` как единственный путь** | Сама библиотека `androidx.security-crypto` де-факто заморожена (1.1.0-alpha) | **KVault или KSafe** |
| **WorkManager API напрямую в commonMain** | Android-only | `expect/actual` в `androidMain` (WorkManager) и `iosMain` (BGTaskScheduler), либо обёртка multiplatform-work-manager (молодая, проверять зрелость) |
## Stack Patterns by Variant
- Использовать moko-biometry, KVault, Alarmee — три готовые библиотеки, скрывающие большую часть `expect/actual`-кода
- WorkManager/BGTaskScheduler всё равно потребуют ручной обвязки — это unavoidable
- Все perf-критичные места переносить в commonMain
- UI на 100% в Compose Multiplatform
- Платформенный код только для: Keychain/Keystore (через KVault), background tasks (через WorkManager/BGTaskScheduler), biometry (через moko-biometry)
- Push-уведомления — Alarmee в commonMain, локальные API скрыты
- Ktor Logging plugin (`level = LogLevel.ALL`) с custom `Logger` через Kermit — детальные дампы запросов/ответов
- На этапе reverse-engineering — mitmproxy/Charles на dev-устройстве; проверить наличие CSRF-токенов, sticky cookies, anti-CAPTCHA
- HttpCookies plugin с `AcceptAllCookiesStorage` — простейший рабочий сценарий; для persistence через рестарты — кастомный `CookiesStorage`, читающий/пишущий в KVault или Room
- Room с suspend-DAO + `Flow<T>` для реактивного кэша
- Пагинация через `androidx.paging:paging-common` (KMP-supported с 3.3+)
## Version Compatibility
| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Kotlin 2.2.20 | Compose Multiplatform 1.10.3 | Рекомендуемая JetBrains пара для iOS/Web |
| Kotlin 2.2.20 | KSP 2.2.20-x.x.x | KSP **обязан** совпадать по major.minor с Kotlin — типичный источник build-ошибок |
| Compose Multiplatform 1.10 | androidx.lifecycle-viewmodel-compose 2.10.0 (jetbrains-port) | Не использовать `androidx.*` напрямую — есть `org.jetbrains.androidx.lifecycle:*` для CMP |
| Ktor 3.4.x | kotlinx-serialization 1.9 | Через `ktor-serialization-kotlinx-json`; ниже 1.7 ломает интеграцию |
| Room 2.8.x | KSP с alignment к Kotlin 2.2 | Room compiler работает через KSP2; на Kotlin 2.2 — обязательно |
| kotlinx-datetime 0.8.x | kotlinx.serialization 1.9.0+ | `Instant`-сериализация — поведение изменилось в 0.7→0.8; читай release notes |
| Coil 3 + Ktor | ktor-client engine, который уже подключён | Coil network через `coil-network-ktor3`, разделяет HTTP-engine с приложением (полезно для общих cookies/headers) |
| Navigation 3 + Koin | koin-compose-navigation3 | Отдельный артефакт `io.insert-koin:koin-compose-navigation3` для интеграции |
## Sources
### Stability/versioning (HIGH)
- [Compose Multiplatform 1.8.0 Released — JetBrains Blog (2025-05)](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) — статус iOS = stable
- [Compose Multiplatform 1.10.0 — JetBrains Blog (2026-01)](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/) — Navigation 3, Hot Reload, @Preview
- [Kotlin Multiplatform Aug-2025 Roadmap — JetBrains Blog](https://blog.jetbrains.com/kotlin/2025/08/kmp-roadmap-aug-2025/) — приоритеты JetBrains, Swift Export → 2026
- [Compose Multiplatform Releases (GitHub)](https://github.com/JetBrains/compose-multiplatform/releases) — 1.10.3 (март 2026), 1.11 в beta
- [Compatibility and versions — Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) — Kotlin 2.2.20 рекомендован для iOS/Web
- [Ktor Releases — Kotlin Blog 3.4.0 (2026-01)](https://blog.jetbrains.com/kotlin/2026/01/ktor-3-4-0-is-now-available/) — current stable 3.4.3 (apr 2026)
- [Ktor Releases (GitHub)](https://github.com/ktorio/ktor/releases) — 3.4.3 верификация
### HTTP / Cookies / Engines (HIGH)
- [Ktor Cookies docs](https://ktor.io/docs/client-cookies.html) — HttpCookies plugin, AcceptAllCookiesStorage, persistent CookiesStorage
- [Ktor Client Engines](https://ktor.io/docs/client-engines.html) — Darwin (iOS), OkHttp (Android), HTTP/2
- [Ktor's CIO engine in 2026 — ITNEXT (2026-04)](https://itnext.io/ktors-cio-engine-in-2026-can-it-finally-replace-okhttp-and-darwin-8f7b7e7e1553) — почему OkHttp/Darwin до сих пор default
### Database (HIGH)
- [Set up Room for KMP — Android Developers](https://developer.android.com/kotlin/multiplatform/room) — официальный setup
- [Room 3.0 Modernizing Room — Android Developers Blog (2026-03)](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) — статус Room 2.8.x stable, Room 3.0 alpha
- [Room 3.0 alpha — InfoQ](https://www.infoq.com/news/2026/04/room-3-kotlin-async-sqlite/)
### Navigation (HIGH/MEDIUM)
- [What's new in Compose Multiplatform 1.10.3 — Kotlin docs](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html) — Navigation 3 alpha на iOS
- [Navigation 3 in Compose Multiplatform — Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [androidx.navigation3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3) — 1.0.0-alpha08
### DI (MEDIUM)
- [Koin KMP advanced patterns](https://insert-koin.io/docs/reference/koin-mp/kmp/) — официальный KMP-guide
- [Koin vs Kotlin-inject — Infinum](https://infinum.com/blog/koin-vs-kotlin-inject-dependency-injection/) — сравнение build-time vs compile-validation
### Storage / Secure (MEDIUM)
- [multiplatform-settings (russhwolf, GitHub)](https://github.com/russhwolf/multiplatform-settings) — KeychainSettings, NSUserDefaults
- [KVault (Liftric, GitHub)](https://github.com/Liftric/KVault) — Keychain wrapper iOS + EncryptedSharedPrefs Android
- [KSafe (GitHub)](https://github.com/ioannisa/KSafe) — современная альтернатива, hardware-backed AES-256-GCM
- [Encrypted Key-Value Store in KMP — Touchlab](https://touchlab.co/encrypted-key-value-store-kotlin-multiplatform)
### Biometric / Notifications / Background (MEDIUM)
- [moko-biometry (GitHub)](https://github.com/icerockdev/moko-biometry) — Compose MP support
- [Alarmee (GitHub)](https://github.com/Tweener/alarmee) — KMP local + push notifications
- [KMPNotifier (GitHub)](https://github.com/mirzemehdi/KMPNotifier)
- [Background Sync in KMP: WorkManager + BGTasks — Medium (Ignatiah Xavier)](https://medium.com/@ignatiah.x/background-sync-in-kotlin-multiplatform-workmanager-android-background-tasks-ios-1f92ad56d84b)
- [iOS Background Tasks — OneUptime (2026-02)](https://oneuptime.com/blog/post/2026-02-02-ios-background-tasks/view) — BGAppRefreshTask vs BGProcessingTask
### Lifecycle / ViewModel (HIGH)
- [Set up ViewModel for KMP — Android Developers](https://developer.android.com/kotlin/multiplatform/viewmodel)
- [Common ViewModel — Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) — `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`
- [Touchlab — AndroidX ViewModel for KMP](https://touchlab.co/kmp-viewmodel)
### Testing (HIGH)
- [Testing Compose Multiplatform UI — Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-test.html)
- [Kotest official](https://kotest.io/) — KMP support
- [Mokkery (GitHub)](https://github.com/lupuuss/Mokkery) — KMP mocks
### Logging / Image / Resources (HIGH)
- [Kermit (GitHub, Touchlab)](https://github.com/touchlab/Kermit) — 2.0.4
- [Coil 3 — Cash App Code Blog](https://code.cash.app/multiplatform-image-loading)
- [Coil 3 docs](https://coil-kt.github.io/coil/) — Compose MP support
- [moko-resources status — Medium 2024 review](https://markonovakovic.medium.com/from-android-to-multiplatform-real-100-jetpack-compose-app-part-1-resources-a5db60f1ed73)
### Production-readiness и pitfalls (MEDIUM)
- [Is KMP production ready in 2026? — Volpis](https://volpis.com/blog/is-kotlin-multiplatform-production-ready/)
- [Compose Multiplatform iOS issues — Medium (Vlad Aliev)](https://medium.com/@alievlad/when-android-meets-ios-compose-multiplatform-issues-6ebfed80fde6) — known bugs, perf
- [Building real-time CMP — Medium (Keyvan Norouzi)](https://medium.com/@keyvan.nrz/building-a-real-time-app-with-compose-multiplatform-what-worked-and-what-hurt-5dbb82e3158c)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
