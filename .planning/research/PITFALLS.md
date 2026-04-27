# Pitfalls Research

**Domain:** Mobile client (Compose Multiplatform iOS+Android) для школьного электронного журнала (ИАС АВЕРС v4.1) на основе reverse-engineered API, on-device, мульти-аккаунт, RU 152-ФЗ
**Researched:** 2026-04-27
**Confidence:** HIGH — основано на актуальных GitHub issues JetBrains/compose-multiplatform, официальной документации Apple/Google/Kotlin, известных ловушках мульти-аккаунт on-device приложений и российском правовом поле; LOW в части закрытого API АВЕРС (домен-специфичные риски сформулированы как гипотезы, верифицируются только на этапе реверса)

> Все pitfalls — домен-специфичные. Общие («пишите тесты», «используйте git») сознательно опущены: предполагается, что разработчик опытный, но домен незнакомый.

## Critical Pitfalls

### Pitfall 1: Сохранение пароля АВЕРС в Keychain с `kSecAttrSynchronizable=true` (iCloud-утечка)

**What goes wrong:**
Чужие учётные данные АВЕРС (логин/пароль ребёнка) синхронизируются через iCloud Keychain на все устройства владельца Apple ID, включая семейные iPad, рабочие Mac и устройства, переданные третьим лицам. В случае компрометации Apple ID пароли утекают. Хуже того — при `kSecAttrAccessible=AfterFirstUnlock` (который многие выбирают по привычке) ключ доступен сразу после первой разблокировки, что делает кражу через jailbreak/forensics проще.

**Why it happens:**
В Compose Multiplatform нет «правильного по умолчанию» secure storage. Разработчики берут `multiplatform-settings-secure` или пишут свой `expect/actual` поверх `SecItemAdd` и копипастят пример из StackOverflow, где `kSecAttrAccessible` либо `AfterFirstUnlock`, либо вовсе пропущен. Synchronizable-флаг по умолчанию `false`, но если разработчик «решит» предложить «удобный sync между iPhone и iPad одного родителя» — он его включит, не понимая последствий.

**How to avoid:**
- Жёстко зафиксировать в `expect/actual` слое для iOS:
  - `kSecAttrAccessible = kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
  - `kSecAttrSynchronizable = kCFBooleanFalse` (явно, не по умолчанию)
  - Use `kSecUseDataProtectionKeychain = true` (отделяет от legacy-keychain)
- На Android: `EncryptedSharedPreferences` или `MasterKey` с `KeyGenParameterSpec.Builder().setUserAuthenticationRequired(true)` + StrongBox если доступен
- Покрыть unit-тестом, что атрибут `kSecAttrSynchronizable` явно `false` в attributes-словаре перед `SecItemAdd`
- Документировать в коде комментарием «не включать sync — пароли чужие, утечка через Apple ID = катастрофа»

**Warning signs:**
- В коде secure-storage есть TODO «добавить iCloud sync для удобства»
- В тестах появляется expectation, что credential доступен после переустановки приложения (это симптом sync или backup)
- iOS-симулятор «помнит» пароль после `xcrun simctl erase all` (косвенный признак, что попало в keychain backup)
- Code review jokes «давайте сделаем как 1Password»

**Phase to address:**
Phase 2 (Auth & Secure Storage) — ДО реализации мульти-аккаунта. Любое решение про мульти-аккаунт должно строиться поверх уже корректно настроенного secure-storage.

---

### Pitfall 2: Хрупкий API-слой против ExtJS-эндпоинтов АВЕРС, который ломается при первом обновлении вендора

**What goes wrong:**
Реверс ExtJS-приложения АВЕРС даёт endpoint'ы вида `/avers/journal/getMarks?studentId=X&_dc=12345`, специфичные для текущего билда (4.1 build 23813). При обновлении сервера школой (вендор обновил продукт ночью) — ломается всё разом: меняются имена параметров, формат ответа (ExtJS любит оборачивать в `{success:true, data:...}` неконсистентно), порядок CSRF-токенов, маршруты роутера. Пользователи видят пустые экраны или красные ошибки одновременно. Хотфикс через App Store идёт 24-48ч, через Google Play 12-24ч.

**Why it happens:**
- Закрытый продукт без публичного API → нет SLA на стабильность интерфейса
- ExtJS-приложения часто оборачивают endpoint'ы в auto-generated proxy с анти-CSRF токенами в каждом запросе, формат которых может поменяться
- Тестовых окружений АВЕРС у нас нет — не на чем проверять регрессию
- Разработчик пишет «обычный» Ktor HttpClient с хардкодом путей в `repositories`

**How to avoid:**
- **API-версионирование на клиенте:** изолировать transport-слой в отдельный модуль `aversApiV4_23813`. Мажорная версия в имени модуля. При новом билде — новый модуль, старый остаётся как fallback.
- **Schema-driven парсинг:** не маппить «всё в data class», а явно проверять наличие ключевых полей (`requireNotNull(json["success"])`). Любая неожиданная структура — типизированная ошибка `AversApiContractMismatch` с полным дампом ответа в crash report (без PII!).
- **Production canary endpoint:** при старте приложения дёргать «контрольный» простой эндпоинт (например, `/api/version` или `/api/whoami`) и сравнивать структуру с эталоном. Если структура изменилась — показать пользователю баннер «Обнаружено обновление АВЕРС. Часть функций может работать некорректно. Обновитесь до версии X.Y приложения», но НЕ блокировать (graceful degradation).
- **Мониторинг в проде:** считать parse-failures по endpoint'у через crash reporter (Sentry/Firebase Crashlytics). Алерт если failure rate > 1% за час.
- **Remote kill-switch / config:** даже без backend — статический JSON на CDN (GitHub Raw / Cloudflare R2) с актуальным «build АВЕРС, который мы поддерживаем» и сообщением для пользователя.
- **mitmproxy snapshot tests:** записать «эталонные» HAR-файлы запросов/ответов АВЕРС в репо, юнит-тесты парсера прогонять против них. При обновлении — обновить HAR.

**Warning signs:**
- В Sentry резкий рост `JsonDecodingException`, `NoSuchElementException` в API-слое
- На support приходят жалобы «вчера работало, сегодня нет» от 3+ пользователей одновременно
- На сайте `journal.school28-kirov.ru` в DevTools видны новые поля или другие пути
- Школьная администрация анонсировала «обновление журнала на каникулах»

**Phase to address:**
Phase 1 (API Reverse-Engineering & Contract). Заложить версионирование сразу, не «потом отрефакторим». Phase «Production Hardening» (предпоследняя) — добавить canary + remote config.

---

### Pitfall 3: Юридический риск со стороны ИИЦ «АВЕРС» / школы / Роскомнадзора (152-ФЗ)

**What goes wrong:**
Три разных вектора:
1. **ИИЦ «АВЕРС»** обнаруживает приложение, шлёт DMCA-аналог в App Store / Google Play (ст. 1280 ГК РФ — реверс-инжиниринг разрешён только для совместимости, и это толкуется узко). App Store снимает приложение в течение 72 часов после жалобы.
2. **Школа №28 / Минобр Кировской области** считает, что приложение угрожает контролю над персональными данными учеников и просит снять или подаёт жалобу в Роскомнадзор.
3. **Роскомнадзор** при обработке ПДн школьников требует регистрацию оператора ПДн (даже если данные хранятся on-device — спорно, но вероятно). С 30 мая 2025 — штрафы за неуведомление; с 1 сентября 2025 — ужесточённые требования к согласию (отдельное, не в EULA).

**Why it happens:**
- Реверс закрытого API без письменного согласия вендора → grey zone в РФ
- Хранение чужих учётных данных школьника = обработка ПДн учителем/родителем, а если есть push с фамилиями/оценками — это спецкатегория ПДн (об успеваемости несовершеннолетних)
- Школа/вендор могут не понимать «оно on-device», увидят только «третье приложение требует пароль»

**How to avoid:**
- **Получить письменное согласие администрации школы №28** ДО публикации в магазины. Это снимает 80% риска. Без согласия — высокий риск отзыва.
- **Связаться с ИИЦ «АВЕРС»** (avers-journal.ru) с предложением «бесплатный клиент для одной школы, не коммерческий». Письменный non-objection хотя бы по email — золото. Если откажут — переоценить риски.
- **Регистрация оператора ПДн в РКН** (форма уведомления через Госуслуги) — необходима, даже если данные on-device, потому что само приложение собирает (запрашивает) ПДн. Бесплатно, занимает 1-2 недели.
- **Политика конфиденциальности** на отдельном URL (GitHub Pages подходит) с явным указанием:
  - данные хранятся ТОЛЬКО на устройстве пользователя
  - разработчик не имеет доступа к данным
  - перечень обрабатываемых ПДн (логин, пароль, ФИО, оценки, домашние задания)
  - согласие на обработку — отдельный экран при первом запуске (не «продолжая, вы соглашаетесь»)
- **Добавить дисклеймер на onboarding-экран:** «Это неофициальный клиент к ИАС АВЕРС. Разработан энтузиастом для школы №28. Не аффилирован с ИИЦ "АВЕРС" и Минобром.»
- **Открытый исходный код** (если возможно) — снижает подозрение, что данные утекают
- **НЕ собирать analytics с PII** — никаких user_id = login, никаких фамилий в crash reports. Sentry/Firebase Analytics с PII = катастрофа.
- **Backup-стратегия:** если приложение снимут с App Store, заранее подготовить TestFlight-link и сборку для AltStore/Sideloadly как fallback для школы

**Warning signs:**
- Юрист школы №28 запросил техническое описание (значит, кто-то уже спросил)
- На сайте ИИЦ «АВЕРС» появилась новость «о неофициальных клиентах»
- В Google Play / App Store пришёл DMCA notice
- Роскомнадзор прислал запрос «о наличии вас в реестре операторов ПДн»

**Phase to address:**
Phase 0 (Legal & Privacy Foundation) — ДО написания кода. Без согласия школы дальнейшая разработка имеет высокий риск выброшенной работы.

---

### Pitfall 4: BGAppRefreshTask на iOS не обеспечивает обещанные пользователю «уведомления о новых оценках»

**What goes wrong:**
Пользователь скачивает приложение, видит «Получайте уведомления о новых оценках». Включает push. Через день получает 0 нотификаций. Через неделю — одну в случайный момент. Apple's BGAppRefreshTask запускается «когда повезёт» (часы, не минуты), не запускается совсем при батарее <20%, в Low Power Mode, для свежеустановленных приложений (нет истории использования), при выключенной Background App Refresh глобально или для приложения. 30 секунд на выполнение — едва хватит на login + один request.

Дополнительно: Apple App Review Guideline 2.5.4 требует, чтобы background-режимы использовались по назначению. «Polling каждые 15 минут к школьному сайту» формально не нарушает, но если ревьюер увидит «opens network connection every 15 minutes» — могут отклонить с просьбой «use silent push instead».

**Why it happens:**
- Разработчики мигрируют ментальную модель Android (где WorkManager «более-менее работает») на iOS
- Документация Apple туманна: `setMinimumBackgroundFetchInterval(15 * 60)` звучит как гарантия, но это hint
- Тестируется в debug-build с `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:...]`, где задача всегда выполняется — у разработчика создаётся ложная уверенность

**How to avoid:**
- **Честный UX:** в onboarding сказать «Уведомления приходят, когда iOS разрешает фоновую активность приложения. Это может быть несколько раз в день, не мгновенно. Для срочных оценок — откройте приложение вручную.» НЕ обещать «realtime».
- **Фолбэк на pull-to-refresh:** считать главным способом получения данных открытие приложения пользователем. Push — бонус.
- **Combination strategy:**
  - `BGAppRefreshTask` каждые ~30 минут (hint)
  - `BGProcessingTask` ночью (когда устройство на зарядке) для тяжёлой синхронизации
  - При получении любого silent push в будущем (если backend появится) — будить нормально
- **Track реальную частоту:** при каждом запуске BGAppRefreshTask логировать timestamp в локальную БД, показывать в debug-меню «последние 50 запусков». Это даст реальную картину.
- **App Store description:** не писать «push-уведомления о новых оценках», писать «фоновая проверка обновлений с уведомлениями (по расписанию iOS)». Это снимает ожидания и претензии Apple Review.
- **App Review:** в App Review Notes явно указать: «App polls school journal in background using BGAppRefreshTask to notify parents about new grades. No advertising, no tracking. Polling frequency limited by iOS scheduler.» — это снижает риск 2.5.4 reject.

**Warning signs:**
- В отзывах App Store: «уведомления не приходят», «работает только когда открываю»
- В метриках (если собираются): среднее время между BGAppRefreshTask runs > 6 часов
- App Review reject «приложение использует background mode не по назначению»

**Phase to address:**
Phase 5 (Push & Background Sync). Critical для пользовательских ожиданий. Перед этой фазой — обязательное решение по UX-копирайту в Phase «UX Foundation».

---

### Pitfall 5: Утечка пароля АВЕРС в crash report / лог / analytics

**What goes wrong:**
Crash в HTTP-слое отправляет в Sentry/Firebase Crashlytics стектрейс, который содержит:
- URL c query-string `?password=...` (если форма авторизации АВЕРС использует GET, что бывает в legacy)
- Тело запроса в `RequestBody.toString()` (POST с form-encoded password)
- Headers с Authorization
- Cookies с session-token (=аналог пароля для атакующего)
- `Throwable.message` содержит «failed to login user=ivanov password=qwerty» если кто-то так залогировал

Один пользователь, один crash → пароль ребёнка в логах SaaS-провайдера. Это утечка ПДн по 152-ФЗ → штраф до 18 миллионов рублей с 2025 года.

**Why it happens:**
- Ktor по умолчанию логирует requests в HttpClient `Logging` plugin на LogLevel.ALL
- Разработчики копируют `install(Logging) { level = LogLevel.ALL }` со StackOverflow и забывают убрать в release-сборке
- Crashlytics автоматически собирает последние N логов перед крашем
- `kotlinx.serialization` exception на пароле может включать значение в message: `expected String at $.password got null`

**How to avoid:**
- **Запретить логирование тел запросов в release:**
  ```kotlin
  install(Logging) {
      level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
      sanitizeHeader { it == HttpHeaders.Authorization || it == HttpHeaders.Cookie }
  }
  ```
- **Свой `HttpRequestRedactor`** — перед каждым логированием/exception маскировать поля `password`, `pwd`, `pass`, `cookie`, `authorization`, `set-cookie`, `token` → `***REDACTED***`. Один центральный redactor, обязательный.
- **Crashlytics/Sentry beforeSend hook:** перебирать breadcrumbs, message, exception, fingerprint и заменять любые совпадения с известными паролями (взять из secure storage) на маркер. Подключение — через `expect/actual`.
- **Отключить network breadcrumbs в Sentry:** `enableAutoBreadcrumbsForHttp = false`, либо вручную фильтровать.
- **Statically banned APIs:** lint/detekt rule запрещающий `Log.d/i/w/e` в `auth/*` пакетах. Только специальный `SecureLogger` с whitelisted полями.
- **Экран «Поделиться логами для поддержки»** — НИКОГДА не отправлять автоматом. Только ручной экспорт в Files, после показа preview пользователю.
- **Тестирование:** подсунуть «канарейный» пароль `kanareyka_PASSWORD_DO_NOT_LEAK_42`, прогнать сценарии (login, refresh, crash в auth-слое, network timeout), грепнуть Sentry/локальные логи на это слово. Если всплыло — баг.

**Warning signs:**
- В debug logs в Logcat/Console.app видно `password=` в URL или body
- В Sentry events есть поле `request.data` с непустым value
- В Crashlytics breadcrumbs есть `Network: POST /login` с body
- Code review с `Log.d("Auth", "logging in $user with $password")`

**Phase to address:**
Phase 2 (Auth & Secure Storage) одновременно с реализацией auth. Обязательный блок «Logging Hygiene» с canary-test перед merge.

---

### Pitfall 6: Утечка данных между аккаунтами через общий HTTP-клиент / общую БД / общий кэш

**What goes wrong:**
Пользователь переключается с аккаунта Иванова на аккаунт Петрова. Видит в расписании уроков остатки данных Иванова: предметы вперемешку, 6А класс пересекается с 8Б, оценки «прыгают». Хуже:
- Cookie от старого Ktor-клиента всё ещё активна → запросы за Петрова уходят с сессией Иванова → сервер возвращает данные Иванова (или вернёт ошибку, но пароль Иванова уже «протёк» в session-cookie на сервере)
- Image cache (Coil/Kamel) показывает аватарку Иванова на экране Петрова
- Push-уведомление «Получена оценка 5» — а к какому ребёнку? Если использовать одну БД с колонкой `account_id`, и где-то забыть фильтр — родитель видит чужие оценки

**Why it happens:**
- Singleton HttpClient с in-memory `AcceptAllCookiesStorage` (по умолчанию в Ktor) живёт всё время процесса
- DI-контейнер (Koin) выдаёт один и тот же экземпляр БД, кэша, клиента всем аккаунтам
- Coil/Kamel кэш ключ — URL без префикса аккаунта
- Разработчик пишет `WHERE student_id = ?` но забывает `AND account_id = ?`

**How to avoid:**
- **Per-account DI-scope в Koin/Kodein:** при выборе аккаунта создаётся новый scope, в нём — свой HttpClient с собственным CookieStorage, свой SQLDelight Driver указывающий на отдельный файл БД, свой кэш с префиксом. При logout / switch — scope.close() гарантирует очистку.
- **Отдельные файлы БД:** `journal_${accountId}.db`. SQLDelight это поддерживает (multiple databases). Полная изоляция, чужие данные физически недоступны.
- **Cookie isolation:** custom `CookiesStorage` инстанцируется per-account, хранит cookies в `journal_${accountId}_cookies.dat`. После logout — удалить файл.
- **Image cache namespacing:** Coil `ImageLoader` per-account c `diskCache(File(cacheDir, "img_$accountId"))`.
- **Push payload включает accountId:** «Иван (8Б): получена оценка 5 по математике». Без accountId — не показывать (или «Получена новая оценка», открыть picker).
- **Property-based test:** запустить N раз сценарий «логин A → запрос → переключение на B → запрос → проверить что в B нет данных A». С разными порядками переключений.

**Warning signs:**
- В коде встречается `object DataBase` или `val httpClient = HttpClient { ... }` на top-level (singleton)
- В тестах `accountSwitchTest` нет
- Image кэш расположен в общей папке без account-префикса
- В аналитике (если есть) видно, что один и тот же `device_id` имеет несколько `student_id` без явного аккаунт-переключения

**Phase to address:**
Phase 4 (Multi-account). До реализации мульти-аккаунта зафиксировать инвариант «никогда не работаем с двумя аккаунтами одновременно» и архитектуру per-account scope. Архитектура важнее, чем UI переключателя.

---

### Pitfall 7: Compose Multiplatform iOS swipe-back gesture отсутствует или ломает кастомные тач-обработчики

**What goes wrong:**
Пользователи iOS привыкли к swipe-from-left-edge для возврата на предыдущий экран. В CMP по умолчанию (если использовать `androidx.navigation.compose`) этот жест **не активен** — потому что навигация компоузная, не UIKit. Пользователь свайпает — ничего не происходит — недовольство. Альтернатива — обернуть всё в `UINavigationController` (в Swift-обёртке iosMain), но тогда:
1. Каждый экран — отдельный `UIViewController`, что ломает компоузный стейт-менеджмент
2. Свайп начинает «съедать» жесты внутри экрана (горизонтальный swipe в `LazyRow`, `HorizontalPager`, кастомный slider)
3. Анимация перехода — UIKit'овая, а контент Compose-овский, что даёт визуальные артефакты

**Why it happens:**
- Compose-навигация (Decompose, Voyager, androidx-nav) не интегрирует с `UINavigationController` по умолчанию
- Discovery-process: разработчик начинает с шаблона, доходит до второго экрана, тестит на iPhone — и обнаруживает проблему
- Решений несколько (Appyx, native nav wrapper, custom predictive back), и все имеют trade-off

**How to avoid:**
- **Решение принять рано** в Phase «Navigation Foundation»: либо
  - (A) **Кастомный gesture в Compose** — `Modifier.pointerInput` со swipe detection, ManagedAnimation назад. Полный контроль, но не идентично iOS-нативной анимации. Подходит если уважаем визуальную единость с Android.
  - (B) **UIKit-wrap** — каждый экран в своём `UIViewController`, использовать `ComposeUIViewController`. Натуральный iOS-feel. Но cross-screen state требует продумать (общая ViewModel живёт в Kotlin-shared, не в VC).
  - (C) **Library** — Appyx 2.0 предоставляет gesture-driven navigation специально для CMP. Удобно, но добавляет ~1MB и зависимость.
- **Тестировать на реальном iPhone, не симуляторе** — симулятор плохо передаёт edge-свайп
- **Документировать в коде**: «iOS back gesture: используется подход X. НЕ использовать Modifier.draggable на горизонтальных детях экрана без учёта priority — конфликтует с system gesture.»

**Warning signs:**
- Во время тестирования на iOS пользователи свайпают и думают «приложение зависло»
- В `HorizontalPager` появляются «застревания» при свайпе с левого края
- App Store reviewer пишет «navigation is non-standard»

**Phase to address:**
Phase 3 (Navigation & Core Screens) — выбор подхода до реализации экранов. Менять потом — переписать половину навигации.

---

### Pitfall 8: LazyColumn внутри HorizontalPager / Sticky Header / nested scroll ломается на iOS

**What goes wrong:**
Расписание уроков естественным образом — это `HorizontalPager` (страница на каждый день недели), внутри каждой страницы — `LazyColumn` со списком уроков, иногда с `stickyHeader` (часть дня — «утро/обед/вечер»). На iOS:
- Овер-скролл `LazyColumn` к низу/верху может «застрять» в подвешенном состоянии (Issue #4279)
- Диагональные жесты неправильно определяются — горизонтальный пейджер начинает скроллиться вместо вертикального списка
- Sticky header иногда промахивается мимо позиции при быстром скролле
- `imePadding` + TextField внутри LazyColumn — оставляет gap или скрывает TextField (Issue #4016, #4902)

Это особенно болезненно для нашего домена: расписание + домашка + оценки — все экраны это nested scroll.

**Why it happens:**
- iOS-имплементация Compose-скроллинга иначе обрабатывает gesture arbitration, чем Android
- Известные открытые issues (#4279, #4016, #4818) — не все исправлены даже в CMP 1.8+
- Skia-рендер интерпретирует overscroll bounce иначе, чем UIScrollView

**How to avoid:**
- **Профилактика структурой:**
  - Не вкладывать `LazyColumn` в `HorizontalPager`, если можно избежать. Вместо этого — один `LazyColumn` со sticky-header'ами для дней недели + горизонтальный TabRow для быстрой навигации.
  - Если pager нужен — каждая страница использует обычный `Column` + `verticalScroll(rememberScrollState())` если данных мало (≤50 уроков). LazyColumn держать только для длинных списков (история оценок).
- **Тестировать на iOS физически** на каждом UI-PR — заводить acceptance test «scroll расписания, переключение pager, ничего не зависло».
- **Workaround для Sticky Header:** часто проще сделать «фейковый» header — не stickyHeader, а отдельный composable наверху, обновляемый по `firstVisibleItemIndex`.
- **TextField + IME:** избегать TextField внутри LazyColumn. Если обязательно (поиск) — выносить в TopBar или BottomSheet.
- **Tracking:** подписаться на CMP issue tracker по тегу `ios scroll`, в каждом релизе проверять changelog на исправления, поднимать версию CMP агрессивно в первые месяцы после стабилизации.

**Warning signs:**
- На iPhone в LazyColumn после быстрого свайпа экран остаётся «приподнятым»
- При горизонтальном свайпе по pager'у внутренний список «прыгает» на пол-экрана
- TextField в форме оказывается за клавиатурой
- Sticky header прыгает между двумя позициями

**Phase to address:**
Phase 3 (Core Screens UI). Архитектурное решение «не вкладывать LazyColumn в Pager» — на этапе проектирования экранов. Issues tracking — ongoing throughout проекта.

---

### Pitfall 9: iOS Privacy Manifest `PrivacyInfo.xcprivacy` отсутствует или неполный → реджект из App Store

**What goes wrong:**
Apple с 1 мая 2024 требует `PrivacyInfo.xcprivacy` в каждой приложении. С 12 февраля 2025 — также во всех «commonly used third-party SDK». Compose Multiplatform iOS-фреймворк — это и есть третий-party SDK с точки зрения вашего iOS приложения. Если он не несёт свой privacy manifest, или ваш собственный manifest не декларирует:
- `NSPrivacyAccessedAPICategoryUserDefaults` (Compose использует UserDefaults внутренне)
- `NSPrivacyAccessedAPICategoryFileTimestamp` (если SQLDelight трогает файлы)
- `NSPrivacyAccessedAPICategorySystemBootTime` (любая diagnostic-библиотека)

— App Store Connect отклоняет загрузку с сообщением «ITMS-91053: Missing API declaration». Реджект может прилететь после загрузки, фикс = пересборка + re-upload + ожидание ревью (2-7 дней).

**Why it happens:**
- Шаблоны CMP старше 2024 года не включают PrivacyInfo.xcprivacy
- Транзитивные зависимости (Ktor, kotlinx-serialization, SQLDelight, Crashlytics) могут использовать required-reason API без декларации
- JetBrains опубликовал официальный manifest для CMP, но его надо подключить

**How to avoid:**
- **С самого начала:** добавить `iosApp/PrivacyInfo.xcprivacy` с минимально необходимыми декларациями:
  - `NSPrivacyAccessedAPICategoryUserDefaults` reason `CA92.1` (доступ к собственным defaults)
  - `NSPrivacyAccessedAPICategoryFileTimestamp` reason `C617.1` (если используется SQLDelight/Room — для управления базой)
  - `NSPrivacyTracking = false` (мы не трекаем)
  - `NSPrivacyCollectedDataTypes` пустой (мы ничего не собираем)
- **JetBrains документация:** https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html — подключить пред-сгенерированный manifest для CMP
- **Аудит зависимостей:** для каждой third-party iOS-зависимости (Firebase, Sentry) проверить, что она поставляется с собственным `*.xcprivacy` или приложить declaration вручную
- **Локальный pre-flight check:** Xcode 15+ в схеме «Validate App» проверяет manifest; запускать перед каждым upload в TestFlight
- **Документировать reasons:** каждый reason code в manifest сопроводить комментарием «зачем именно нам это нужно» (для будущего себя и для App Review)

**Warning signs:**
- Xcode build log: «warning: PrivacyInfo.xcprivacy not found»
- App Store Connect upload error «ITMS-91053» / «ITMS-91054»
- TestFlight build «Invalid Binary»

**Phase to address:**
Phase «App Store Submission» (последняя). Но добавить заглушку PrivacyInfo.xcprivacy уже в Phase 0 (Project Setup), чтобы не забыть.

---

## Moderate Pitfalls

### Pitfall 10: Bundle size iOS-сборки (~25-50 MB только из-за Skia) пугает пользователей при первой установке

**What goes wrong:**
Чистая Compose Multiplatform iOS-сборка добавляет ~9-25 MB к нативному iOS приложению из-за встроенного Skia. App Store показывает размер при загрузке, и если пользователь видит «60 MB» для «приложения дневника» — может закрыть страницу. Особенно критично для родителей со старыми iPhone и ограниченным трафиком.

**How to avoid:**
- **App Thinning** включить в Xcode — App Store сделает variants для разных архитектур (только arm64 в 2026, не fat).
- **strip-debug-symbols в release** в `kotlin.native.binary.bundleId` settings.
- **Удалить неиспользуемые ресурсы** (старые иконки, неиспользуемые шрифты) — их видно в `xcrun --bundle-size`.
- **iconcomposer / sf-symbols** вместо своих PNG — экономия мегабайтов.
- **Не использовать heavy charting libraries** — для оценок достаточно простых Composable с Canvas.
- **На странице App Store:** скриншоты должны явно демонстрировать ценность, чтобы оправдать размер.
- **Целевой бюджет:** Universal IPA ≤ 30 MB после thinning. Это терпимо для родителей.

**Phase to address:** Phase «App Store Submission» — измерить и оптимизировать перед публикацией.

---

### Pitfall 11: Anti-bot защита АВЕРС (CAPTCHA, rate-limit, fingerprinting)

**What goes wrong:**
Сервер АВЕРС может включить:
- CAPTCHA после N неудачных логинов (вероятно)
- Rate-limit на /api/login (вероятно)
- Fingerprinting User-Agent / Accept-Language (типично для ExtJS-сайтов на Java)
- IP-блокировку при слишком частых запросах (если приложение polling-ит каждые 15 минут × 1000 пользователей с одного IP школы — может сработать)

При срабатывании — пользователи не смогут залогиниться, увидят непонятную ошибку.

**How to avoid:**
- **User-Agent mimic**: использовать реалистичный UA мобильного Safari, не Ktor-default. Менять при каждом мажорном обновлении приложения чтобы выглядеть как разные браузеры (если детект слишком агрессивный — использовать UA вебвью школьного сайта).
- **Throttling на клиенте:** не более 1 login attempt в 5 секунд, не более 1 polling request в 15 минут. Exponential backoff на ошибки.
- **CAPTCHA fallback:** если сервер вернул HTML с captcha-страницей — показать в WebView, дать пользователю решить, забрать cookie. WebView с `WKWebView` на iOS, `WebView` на Android. Это компромисс, но единственный вариант.
- **Уважать `Retry-After` header.**
- **Кэшировать агрессивно**: если можно не дёргать сервер (данные не старше 1 часа) — не дёргать.
- **Локализация ошибок:** «Сервер АВЕРС временно ограничил доступ. Попробуйте через 5 минут.» вместо «HTTP 429».

**Phase to address:** Phase 1 (API Reverse) — обнаружить и задокументировать. Phase «Production Hardening» — обработать gracefully.

---

### Pitfall 12: Push-уведомление с фамилией и оценкой ребёнка на lock screen — утечка ПДн при потере телефона

**What goes wrong:**
Push: «Иван Петров получил 2 по математике». Телефон лежит на парте → одноклассник видит. Или родитель потерял телефон → нашедший узнал ФИО, школу, успеваемость. Это утечка спецкатегории ПДн (информация о несовершеннолетнем, его успеваемости).

**How to avoid:**
- **По умолчанию — обезличенный push:** «Получена новая оценка». Без имени, без оценки.
- **Настройка пользователя:** «Показывать детали в уведомлениях» — выкл по умолчанию. Кто хочет — включит, осознанно.
- **Critical для мульти-аккаунта:** даже если включены детали, использовать инициалы «И.П. — оценка 5», не ФИО полностью.
- **iOS:** использовать `UNNotificationContentExtension` с private content, или `mutable-content` / hide on lock screen.
- **Android:** `setVisibility(NotificationCompat.VISIBILITY_SECRET)` для lock screen, полный контент только когда unlocked.
- **Документировать в Privacy Policy.**

**Phase to address:** Phase 5 (Push & Notifications). Default-off для деталей.

---

### Pitfall 13: Кэш расписания / оценок устаревает, пользователь видит старые данные и не замечает

**What goes wrong:**
Offline-first архитектура обещает «работает без сети». Но:
- Пользователь зашёл утром, кэш показал расписание — он не заметил, что данные неделю старые
- Замена урока произошла в прошлую пятницу, в кэше старая версия — ребёнок пришёл на отменённый урок
- Push не сработал (см. Pitfall 4), и кэш не освежился

**How to avoid:**
- **Visible staleness indicator:** на каждом экране — мелкий текст «обновлено 2ч назад» или «обновлено вчера». Если данные старше 24 часов — жёлтая плашка «данные могут быть неактуальными, потяните для обновления». Старше 72 часов — красная.
- **Pull-to-refresh** на каждом экране данных. Обязательная UI-привычка.
- **Stale-while-revalidate:** при открытии экрана сразу показать кэш + параллельно запустить background refresh, по завершении — анимированно подменить.
- **Network state aware:** если сеть есть и кэш > 30 минут — освежить при открытии экрана. Если сети нет — показать кэш как есть.
- **TTL per-entity:** оценки могут жить в кэше 1 час, расписание текущей недели — 30 минут, расписание прошлых недель — 7 дней (всё равно не меняется).

**Phase to address:** Phase «Offline & Cache Strategy» (между API и UI).

---

### Pitfall 14: Kotlin/Native interop с Swift — `Kotlin` префикс в именах, нет sealed-классов как enum, неудобный nullable

**What goes wrong:**
Если приложение использует Swift-обёртки (для нативной навигации, BGAppRefreshTask, push handling), Swift-разработчик (или вы в роли Swift-разработчика) обнаруживает:
- `KotlinThrowable`, `KotlinByteArray` — нельзя кастить к нативным Swift-типам
- `sealed class Result<T>` приходит как `class` без exhaustive switch
- `data class` без custom Codable
- Suspend-функции — async через completion handler, не Swift `async/await` (до недавних версий) или с лимитами
- `value class` (inline) теряется, превращается в boxed value

В нашем случае — push-handler в `AppDelegate` пишется на Swift, и ему надо вызывать Kotlin code для обновления БД. Боль на стыке.

**How to avoid:**
- **Минимизировать Swift→Kotlin вызовы:** один-два «фасадных» класса (`AppEntryPoint`, `PushHandler`) с простыми primitive-параметрами и колбэками, не sealed-классами.
- **Suspend → completion handler wrapper** — обернуть suspend-функции в `Job`-возвращающие функции с явным callback.
- **Use SKIE** (https://skie.touchlab.co/) — open-source compiler plugin, который улучшает Swift-interop: sealed classes → Swift enums, suspend → async/await, nullable handling. Сильно повышает DX. Поддерживается Touchlab, активно развивается.
- **Документировать boundary:** в `iosApp/swift/` README со списком «вот эти Kotlin-классы, как они выглядят в Swift, как их использовать».
- **Тестировать на Xcode 15.4+** — interop часто ломается на новых версиях Xcode, держать связь с CMP версией.

**Phase to address:** Phase «iOS Native Integration» (push, deep links). До этого момента можно жить только в Compose.

---

### Pitfall 15: SwiftPM vs CocoaPods — выбор устаревший / неработающий в 2026

**What goes wrong:**
Большинство туториалов 2023-2024 описывают `cocoapods {}` блок в build.gradle.kts. CocoaPods объявлен deprecated, trunk станет read-only 2 декабря 2026. JetBrains рекомендует SwiftPM для нового кода. Если проект начать с CocoaPods — через год придётся мигрировать.

**How to avoid:**
- **SPM с самого начала.** Использовать `swiftPMDependencies {}` блок (KMP 2.0+) или плагин `spmForKmp` / `spm4Kmp`.
- **Минимум CocoaPods зависимостей:** если какая-то iOS-библиотека ТОЛЬКО на CocoaPods (Firebase раньше так был, сейчас уже SPM) — выбрать альтернативу или встроить вручную.
- **Документировать решение** в `ARCHITECTURE.md` — почему SPM, как добавлять новые зависимости.

**Phase to address:** Phase 0 (Project Setup). Конфигурация на старте.

---

### Pitfall 16: Библиотека без iOS-таргета (Android-only зависимость, попавшая в commonMain)

**What goes wrong:**
Разработчик добавляет Android-only library (например, какую-то androidx-work-помощалку) в `commonMain` по ошибке. Сборка Android ОК. Сборка iOS ломается с непонятной ошибкой «unresolved reference» в Kotlin/Native компиляторе. Часы потеряны.

**How to avoid:**
- **Жёстко контролировать `commonMain` deps:** только мультиплатформенные (Ktor, kotlinx.*, SQLDelight, Koin, Coil 3).
- **Платформо-специфичные** — в `androidMain`/`iosMain`, через `expect/actual`.
- **CI:** в pipeline собирать ОБЕ платформы (`assembleDebug` + `iosX64Test`). Без матрицы CI iOS-проблемы обнаруживаются поздно.
- **Detekt rule** `kotlin-multiplatform-deps-check` (или вручную review каждой новой dep).
- **Список «approved libraries»** в `ARCHITECTURE.md`, новая dep — через ADR.

**Phase to address:** Phase 0 (Project Setup) — настройка CI с iOS build. Важно с первой строчки кода.

---

## Minor Pitfalls

### Pitfall 17: Биометрия (Touch ID / Face ID) на симуляторе работает, на реальном устройстве — нет

**What goes wrong:**
В iOS Simulator биометрия эмулируется через Features → Touch ID/Face ID → Matching Face. Тесты проходят. На реальном устройстве — `LAError.biometryNotEnrolled` (если у пользователя не настроена биометрия) или `LAError.biometryLockout` (после 5 неудач — нужен пароль) или просто iPhone SE без Face ID.

**How to avoid:**
- **Fallback на password** обязателен: если биометрия недоступна — спросить пароль приложения (PIN) или системный пароль через `LAPolicy.deviceOwnerAuthentication`.
- **Тестировать на реальных устройствах:** iPhone SE (Touch ID), iPhone 11+ (Face ID), Android разных производителей (Samsung Knox, Pixel, Xiaomi с разной реализацией BiometricPrompt).
- **Graceful messaging:** «Войти с Face ID» / «Войти с Touch ID» / «Войти с отпечатком» — динамически в зависимости от доступного метода. На Android — `BiometricManager.canAuthenticate()` для определения.

**Phase to address:** Phase 2 (Auth). Биометрия — поверх secure storage.

---

### Pitfall 18: Универсальные deep links (Universal Links / App Links) требуют файлы на сервере, которым мы не владеем

**What goes wrong:**
Хочется, чтобы пользователь нажал ссылку `https://journal.school28-kirov.ru/marks/123` в Telegram → открылось наше приложение. Это требует размещения `.well-known/apple-app-site-association` и `.well-known/assetlinks.json` на `journal.school28-kirov.ru`. Но сервер АВЕРС не наш — мы не можем туда положить файл.

**How to avoid:**
- **Custom URL scheme:** `lintehjournal://marks/123` — работает, но требует «знания» от пользователя.
- **Свой домен** на GitHub Pages: `lintehjournal.ru/marks/123` redirect или universal link, в `apple-app-site-association` указать наше приложение.
- **QR-код в приложении** для шаринга — обходит проблему deep link.
- **Не обещать в роадмапе** «откройте ссылку АВЕРС в нашем приложении» если не контролируете домен.

**Phase to address:** Phase 5 (Sharing & Deep Links).

---

### Pitfall 19: Школьная сеть с self-signed certificate / прокси / SSL pinning

**What goes wrong:**
Школьный Wi-Fi может иметь свой сертификат (родительский контроль провайдера), MITM-проксирующий журнал. Наш HTTP-клиент с дефолтной валидацией HTTPS отклонит соединение. Пользователь видит «нет сети», думает что приложение сломано.

**How to avoid:**
- **Уважать system trust store** — на iOS/Android по умолчанию доверяют установленным CA сертификатам, в том числе родительским.
- **НЕ делать SSL pinning** — мы пинаем сертификат сервера школы, который вне нашего контроля; вендор обновит сертификат — приложение перестанет работать у всех.
- **Сообщать пользователю** если ошибка SSL: «Не удалось безопасно подключиться. Возможно, ваша сеть использует прокси (например, школьный Wi-Fi). Попробуйте мобильный интернет.»
- **Логировать** SSL ошибки отдельно от network ошибок.

**Phase to address:** Phase 1 (API & Network Layer).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Хардкод базового URL `journal.school28-kirov.ru` повсюду | Ускоряет старт; меньше абстракций | При v2 «другие школы» — переписать половину репо; даже для тестов нет moc'а | Только в v1. Но изолировать в одном `Config.kt` обязательно. |
| Ktor `HttpClient` с `LogLevel.ALL` для отладки | Легко дебажить | Утечка пароля в продакшен (см. Pitfall 5) | Никогда в release. Только debug-вариант с условной компиляцией. |
| Один SQLDelight database на все аккаунты с колонкой `account_id` | Проще миграции, один путь к данным | Утечка данных через забытый WHERE; нельзя удалить аккаунт целиком атомарно | Не приемлемо для нашего домена (ПДн). Использовать БД-per-account. |
| Логин через WebView вместо разобранного API | Реверс-сложность сводится к нулю; выглядит как «настоящий сайт» | Невозможно мульти-аккаунт без явных переключений; пароль вводится каждый раз; offline невозможен; UX неприемлемый | Только как fallback при срабатывании CAPTCHA. |
| Скачивание ВСЕГО расписания/оценок при каждом обновлении | Простая логика инвалидации | Трафик; rate-limit от АВЕРС; долгий cold start | Только в первый sync. Дальше — incremental по timestamps если API даёт. |
| Использование `runBlocking` в iOS-фасаде вместо async/completion | Меньше boilerplate в Swift | Деадлоки на main thread в iOS push handler | Никогда в hot path. Только при инициализации (где блокировка не критична). |
| Хранить пароль в `UserDefaults` / `SharedPreferences` (не secure) для отладки | Быстрее тестировать | Если попадёт в master — утечка ПДн всех тестеров | Никогда. Использовать debug Keychain entry с тестовым паролем. |
| Игнорировать iOS warnings про unsupported `value class` в interop | Сборка зелёная | На Xcode 16+ ломается interop | Принять как известный долг, мигрировать на простые data class на boundary. |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **АВЕРС API (HTTP)** | Использовать User-Agent `Ktor/X.Y` — выглядит как бот | Mimic Mobile Safari UA, `Accept: application/json`, `X-Requested-With: XMLHttpRequest` (как ExtJS) |
| **АВЕРС session** | Хранить cookies in-memory — теряются при перезапуске, нужен повторный логин | Persistent CookieStorage в файле per-account, encrypted |
| **АВЕРС CSRF** | Игнорировать CSRF-токен — сервер начнёт реджектить через N запросов | Парсить CSRF из первого ответа (часто в meta-tag или cookie), включать в каждый POST |
| **iOS Keychain** | `kSecAttrAccessibleAlways` (deprecated, но копипастят) | `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` |
| **Android Keystore** | Хранить ключ в SharedPreferences, шифровать им же | `EncryptedSharedPreferences` через androidx.security или `MasterKey` через AndroidKeyStore (StrongBox если есть) |
| **APNs (когда появится backend)** | Хранить device token в plain text на бэке | Token — не secret, но связь token↔userId — да; encrypt at rest |
| **WorkManager periodic** | Ожидать выполнение каждые 15 минут точно | Понимать как «не чаще 15 минут, может реже». Использовать `setRequiresBatteryNotLow(true)` для UX. |
| **BGAppRefreshTask** | `setMinimumBackgroundFetchInterval(15 * 60)` и думать что 15 минут | Это hint. Реальная частота 1-6 часов. Дублировать логикой при foreground. |
| **Crashlytics** | Auto-send всё | Отфильтровать PII в `beforeSend`, отключить network breadcrumbs |
| **Sentry KMP** | `sentry-kotlin-multiplatform` нестабилен в iosX64 build | Сначала проверить актуальность поддержки в их changelog; fallback на платформо-специфичные SDK через expect/actual |
| **kotlinx.serialization** | `decodeFromString` с `ignoreUnknownKeys=false` | Включить `ignoreUnknownKeys = true` для устойчивости к расширениям API АВЕРС |
| **Deep Link → Compose Navigation** | Не обрабатывать ситуацию «приложение в фоне, deep link приходит» — открывается не тот экран | В iOS — `scene(_:openURLContexts:)` + `application(_:continue:restorationHandler:)`. Все пути включить. На Android — `Intent.ACTION_VIEW` + parse в MainActivity onNewIntent. |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Загрузка всех оценок всех предметов в одну `LazyColumn` без пагинации | Медленный first paint при старте, jank на старых iPhone | Группировать по четвертям, lazy-загружать по запросу. SQLDelight `Query.asFlow()` с `LIMIT/OFFSET`. | На 1-3 году обучения (2000+ оценок) |
| Synchronous Keychain reads на каждое HTTP request | Высокое latency 50-200ms на каждый запрос на iOS | Кэшировать пароль в memory только на время сессии. Биометрия — раз в 5 минут, не на каждом запросе. | Сразу при первом релизе |
| Coil/Kamel image cache без size-limit | Терабайтный кэш через год — место на устройстве | `diskCache(maxSizeBytes = 50.MB)`, `memoryCache(maxSizePercent = 0.10)` | Через 6-12 месяцев активного использования |
| Recomposition всей расписания на изменении одного урока | Скролл лагает | `LazyColumn(items, key = { it.id })`, `derivedStateOf`, `remember` с правильными ключами. Проверить через Layout Inspector / Recomposition counts. | На медленных устройствах сразу |
| BGAppRefreshTask делает full sync при каждом запуске (30 сек hard limit) | Task не успевает, iOS reduces priority → ещё реже запускается | Diff-based sync: дёргать `/api/changes-since?ts=last_sync`. Если такого нет — приоритет «новые оценки + сегодняшнее расписание» в 30 сек, остальное при foreground. | Сразу при production |
| SQLDelight Query без `Flow` — UI не реагирует на изменения | Пользователь делает pull-to-refresh, видит старые данные пока не вернётся в экран | Каждый Repository возвращает `Flow<List<X>>` через `Query.asFlow().mapToList()` | Сразу при production |
| Все HTTP запросы на `Dispatchers.Main` | UI freezes на медленной сети | Все network в `Dispatchers.IO` (или `Dispatchers.Default` на Native), результат — withContext(Dispatchers.Main) | Сразу при production |
| Чтение/запись `multiplatform-settings` (UserDefaults/SharedPrefs) на main thread в hot path | Frame drops на iOS | Кэшировать настройки в memory при старте, async write | На больших Settings-объектах |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Включить iCloud sync для Keychain паролей АВЕРС | Утечка паролей детей при компрометации Apple ID родителя | `kSecAttrSynchronizable = false` явно, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` |
| Логировать тело HTTP запросов в release | Пароль в Sentry/Crashlytics → утечка ПДн → штраф 152-ФЗ до 18M ₽ | `LogLevel.NONE` в release, `sanitizeHeader`, redactor для bodies |
| Включить crash reporting auto-attach screenshots | Скриншот с открытой формой логина → пароль | Отключить screenshot collection или blacklist auth screens |
| Showing полный пароль в push-уведомлении (нонсенс, но видел) | Пароль на lock screen | Никогда не включать credentials в notification content |
| Bundle pinned cert для журнал.school28 | Школа меняет cert → все приложения сломаны → Apple-update-cycle = 1 неделя | Не делать SSL pinning для не-нашего домена |
| Хранить cookies в `MMKV` / `SharedPreferences` plain | Cookie = сессия = пароль для атакующего; root/jailbroken устройство = утечка | Encrypt at rest даже cookies, или хранить session-token в Keychain тоже |
| `WebView.setJavaScriptEnabled(true)` для отображения АВЕРС | XSS из чужого контента → exfiltrate cookies | Если совсем нужен WebView (CAPTCHA fallback) — отдельный изолированный instance с минимальными правами; cleanup cookies после |
| Использовать `BiometricPrompt` без `setUserAuthenticationValidityDuration(0)` | Биометрия один раз → ключ доступен 30 сек / forever без re-auth | `setUserAuthenticationRequired(true)` + `validityDuration` или `Authenticators.BIOMETRIC_STRONG` без duration |
| Backup всех данных приложения через iCloud / Auto Backup | Backup-файлы могут утекать; Apple/Google scan для CSAM может видеть файлы | iOS: `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (исключает из backup). Android: `android:allowBackup="false"` в manifest, `android:dataExtractionRules` configured |
| Принять любой self-signed cert при HTTPS-ошибке | MITM в школьном Wi-Fi → пароль перехвачен | НЕ переопределять `TrustManager`. Уважать system trust. Сообщать пользователю об ошибке. |
| Вход через `intent.getStringExtra("password")` (deep link с паролем) | Логи Android, share-sheet, Logcat → утечка | Никогда не принимать пароль через intent / URL. Только пользовательский ввод. |
| Передавать пароль АВЕРС в analytics events ради «анализа поведения» | Это уже преступление по 152-ФЗ | Lint rule на `analytics.*` методы — запрет передавать поля из `auth/*` пакета |
| Отображать «забыли пароль? напишите ${learning_email}» с PII школы | Утечка корп.email | Все строки — через resources, ревью при локализации |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| «Push-уведомления о новых оценках» в App Store description | Ожидание realtime → разочарование → 1-star review | Честно: «Уведомления при фоновой проверке (по расписанию iOS/Android)» |
| Один экран «Главная» с агрегатом — оценки, расписание, ДЗ перемешаны | Cognitive overload, особенно у детей младших классов | Tab-based: Сегодня / Оценки / Расписание / ДЗ. Каждый — focused. |
| Сложная иерархия мульти-аккаунта (3+ тапа для переключения) | Родитель с двумя детьми — переключается 5+ раз в день | Top-bar dropdown с быстрым переключением. На второе нажатие — переключение. Pinned avatar в углу. |
| Показ "Sync failed" красным алертом при каждой временной ошибке сети | Тревожность; пользователь думает «сломалось» | Тихий retry с exp.backoff. Только при стабильной ошибке — небольшая плашка снизу. |
| Hardcoded расписание звонков из 2020 года | Не совпадает с реальным | Брать из АВЕРС если возвращается; иначе не показывать время начала/окончания, только последовательность |
| Блокирующий loader на весь экран при pull-to-refresh | UI заморожен | SwipeRefreshIndicator, остальной экран остаётся interactive с старыми данными |
| Нативная iOS клавиатура с английской раскладкой при логине (если login = email) | Пользователь не знает где переключить язык | `keyboardType = KeyboardType.Email` + autofill hint + кнопка «вставить из Keychain» |
| Анимация перехода между экранами длиннее 300ms | На iOS воспринимается как лаг | Стандартные iOS-анимации (~250ms). Compose default — окей. Не делать кастомные slow anims. |
| Нет «empty state» иллюстраций для «нет оценок», «нет ДЗ» | Пустой экран → «приложение сломалось» | Дружелюбные empty states с подсказкой («Когда учитель выставит первую оценку, она появится здесь») |
| Ошибки на английском («Network Error», «Unauthorized») | Родители не айтишники | Все user-facing strings на русском; technical detail только в expandable раздел «детали ошибки» |

---

## "Looks Done But Isn't" Checklist

- [ ] **Авторизация:** работает в debug — но проверено ли при выключенной сети? при первом логине после переустановки? после смены пароля на сайте АВЕРС? при истёкшей сессии в фоне?
- [ ] **Мульти-аккаунт:** переключается — но проверено ли что данные в кэше второго аккаунта не показывают первого? что push приходит на правильный аккаунт? что биометрия не открывает «не тот» аккаунт?
- [ ] **Push-уведомления:** «работают» в debug-симуляторе — но проверено ли на реальном iPhone в течение недели с реальной батареей и реальным сценарием не-открытия приложения?
- [ ] **Offline:** показывает кэш без сети — но обновляется ли индикатор «обновлено N часов назад»? Восстанавливается ли при возврате сети? Не пишет ли в БД при попытке write?
- [ ] **iOS swipe-back:** swipe работает — но проверено ли в HorizontalPager? в LazyRow? на экране с TextField и открытой клавиатурой?
- [ ] **Privacy Manifest:** файл присутствует — но включает ли все required reasons? проходит ли App Store Connect upload?
- [ ] **Безопасность хранения:** Keychain используется — но проверено ли что `kSecAttrSynchronizable=false`? `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`?
- [ ] **Логи:** debug-логи присутствуют — но проверено ли что в release `LogLevel.NONE`? Ни в одном breadcrumb нет `password=`?
- [ ] **Деавторизация:** logout кнопка работает — но удаляет ли она пароль из Keychain? cookies? кэш изображений? БД? push-токен?
- [ ] **Удаление аккаунта:** UI есть — но удаляет ли все файлы (БД, image cache, cookies, secure storage entries) с устройства?
- [ ] **Локализация ошибок:** сообщения есть — но все ли на русском? Без HTTP-кодов в user-facing тексте?
- [ ] **Биометрия fallback:** Touch ID работает — но что если у пользователя его нет? после 5 неверных попыток? после iOS update сбросившего enrollment?
- [ ] **Privacy Policy:** ссылка в about — но соответствует ли текст реальному поведению? Регистрация в РКН пройдена?
- [ ] **App Store метаданные:** title и description — но declared all data types in Data Safety / App Privacy? privacy URL рабочий?
- [ ] **API контракт:** все нужные endpoint'ы реверсированы — но обработаны ли граничные случаи: пустое расписание (новый ученик), отчисленный ребёнок, временный аккаунт, перевод в другую школу в середине года?
- [ ] **Dark mode:** работает — но все экраны? Splash screen? иконки уведомлений?
- [ ] **Большие шрифты (Accessibility):** UI масштабируется — но не ломаются ли таблицы оценок? не обрезаются ли длинные предметы?
- [ ] **VoiceOver / TalkBack:** screen reader читает — но не пропускает ли важное? кнопки имеют contentDescription?
- [ ] **Push deep-link:** тап на нотификацию открывает приложение — но открывает ЭКРАН релевантной оценки/ДЗ, не главный экран?
- [ ] **Reset to factory:** «удалить все данные» в настройках — но удаляет ли push-токен с APNs/FCM сервера? snapshot iOS background?

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| API АВЕРС изменился, приложение сломано у всех | HIGH (1-2 недели) | (1) Активировать remote-config kill-switch с сообщением «обновите приложение». (2) Реверс новой версии. (3) Hotfix release. (4) Параллельно — чинить старую версию через server-side fallback если возможно. (5) Post-mortem: добавить canary check в новую версию. |
| Утечка паролей в Sentry/Crashlytics | CRITICAL (юр. + репутационный) | (1) Немедленно отключить crash reporting в production через remote-config. (2) Удалить все события из Sentry за период. (3) Обязать пользователей сменить пароль АВЕРС через notification + email school admin. (4) Уведомить РКН в течение 24 часов согласно 152-ФЗ. (5) Hotfix с исправленным redactor'ом. |
| App Store reject за PrivacyInfo / 2.5.4 | MEDIUM (3-7 дней) | (1) Прочитать reject-сообщение внимательно — Apple даёт конкретику. (2) Написать в App Review Notes объяснение use case. (3) Обновить PrivacyInfo.xcprivacy с правильными reasons. (4) Resubmit. (5) Если повторно reject — Schedule Call с App Review Team. |
| iCloud sync случайно включён → пароли утекли | CRITICAL | (1) Hotfix release с `kSecAttrSynchronizable=false`. (2) Force re-login для всех пользователей (invalidate все session-tokens). (3) Уведомить пользователей и РКН. (4) Возможно: удалить Keychain entries во всём пуле iCloud-синхронизированных устройств (требует пользовательского действия). |
| BGAppRefreshTask не работает у >50% пользователей | MEDIUM (UX issue) | (1) Принять реальность iOS. (2) Обновить App Store description убрав «уведомления». (3) Усилить foreground refresh (pull-to-refresh, при открытии). (4) В onboarding честно объяснить ограничения. (5) Долгосрочно: рассмотреть minimal backend для silent push (revisit Key Decisions). |
| Юридический запрет от ИИЦ АВЕРС / школы | HIGH (потенциально проект) | (1) Снять с App Store / Google Play немедленно (если требуется). (2) Связаться с истцом, найти компромисс (бесплатно для школы, white-label для ИИЦ?). (3) Альтернатива: TestFlight / sideload для существующих пользователей. (4) Если переговоры невозможны — закрыть проект по-доброму. |
| LazyColumn на iOS лагает на расписании | LOW-MEDIUM | (1) Перепроектировать структуру: убрать nested scroll. (2) Меньше items в LazyColumn (≤200). (3) Если CMP issue — апгрейд CMP при выходе фикса. (4) В крайнем случае — `UICollectionView` через UIKitView для проблемных экранов. |
| Cookie от старого аккаунта попадает в новый | MEDIUM | (1) Hotfix с per-account scope для HttpClient. (2) Force всех пользователей пере-залогиниться. (3) Audit: написать integration test, имитирующий 100 переключений в случайном порядке. |
| Не зарегистрировались в РКН как оператор ПДн → штраф | MEDIUM (300k–500k ₽) | (1) Срочно подать уведомление через Госуслуги. (2) Если уже пришёл штраф — обжаловать в течение 10 дней. (3) Привлечь юриста по 152-ФЗ. (4) Update Privacy Policy. |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| #3 Юридический риск (ИИЦ АВЕРС, школа, РКН) | **Phase 0: Legal & Privacy Foundation** | Письменное согласие школы; уведомление в РКН подано; Privacy Policy опубликована |
| #15 SwiftPM vs CocoaPods | Phase 0: Project Setup | `swiftPMDependencies {}` блок в build.gradle.kts; нет `cocoapods {}` |
| #16 Кросс-платформенные deps | Phase 0: Project Setup | CI собирает iOS+Android; `commonMain` содержит только KMP-deps |
| #9 PrivacyInfo.xcprivacy | Phase 0 (skeleton) + Phase «Submission» (final) | Файл присутствует; Xcode validate проходит |
| #2 API contract fragility | **Phase 1: API Reverse-Engineering** | Versioned `aversApiV4_*` модуль; HAR-snapshot тесты; canary endpoint |
| #11 Anti-bot / CAPTCHA | Phase 1: API Reverse | UA mimic; rate-limit на клиенте; WebView fallback готов |
| #19 SSL / прокси сети | Phase 1: API Layer | Тест в школьной Wi-Fi сети; не используется pinning |
| #1 Keychain iCloud sync | **Phase 2: Auth & Secure Storage** | Unit test на `kSecAttrSynchronizable=false` |
| #5 Утечка пароля в логи | Phase 2: Auth | Canary-test «kanareyka_PASSWORD»; redactor в HttpClient |
| #17 Биометрия fallback | Phase 2: Auth | Тестирование на iPhone SE (Touch ID); fallback to PIN/password |
| #7 iOS swipe-back gesture | **Phase 3: Navigation & Screens** | Решение про подход (custom/UIKit-wrap/Appyx) задокументировано в ADR |
| #8 LazyColumn / Pager scroll | Phase 3: UI | Acceptance test «scroll расписания» на iPhone |
| #14 Kotlin/Swift interop | Phase 3 (если нужны Swift handlers) | Использовать SKIE; minimal boundary |
| #6 Multi-account isolation | **Phase 4: Multi-account** | Per-account DI scope; БД-per-account; property test «100 переключений» |
| #4 BGAppRefreshTask честный UX | **Phase 5: Push & Background** | Onboarding-копирайт честный; tracking реальной частоты в debug-меню |
| #12 Push с PII на lock screen | Phase 5: Push | Default-off для деталей; инициалы только |
| #18 Universal Links | Phase 5: Sharing | Решение использовать custom scheme или свой домен; задокументировано |
| #13 Кэш staleness | Phase «Offline & Cache» (между API и UI) | Visible "обновлено N часов назад"; pull-to-refresh; жёлтая/красная плашка |
| #10 Bundle size | **Phase «Submission»** | Universal IPA ≤ 30 MB после thinning |

---

## Sources

### Compose Multiplatform iOS issues (HIGH confidence — official issue tracker)
- [JetBrains/compose-multiplatform Issue #4818 — UIKitView scroll inside LazyColumn](https://github.com/JetBrains/compose-multiplatform/issues/4818)
- [Issue #4279 — LazyColumn inside HorizontalPager wrong scroll](https://github.com/JetBrains/compose-multiplatform/issues/4279)
- [Issue #4016 — LazyColumn imePadding TextField keyboard](https://github.com/JetBrains/compose-multiplatform/issues/4016)
- [Issue #4902 — Scaffold scrolls improperly when keyboard opened on iOS](https://github.com/JetBrains/compose-multiplatform/issues/4902)
- [Issue #5088 — iOS Keyboard Broken with readOnly](https://github.com/JetBrains/compose-multiplatform/issues/5088)
- [Issue #3856 — iOS keyboard loose focus when text field changes](https://github.com/JetBrains/compose-multiplatform/issues/3856)
- [Issue #4855 — Reduce ios app size](https://github.com/JetBrains/compose-multiplatform/issues/4855)
- [Issue #3632 — Reduce overhead of iOS binary size compared to KMP application](https://github.com/JetBrains/compose-multiplatform/issues/3632)
- [Issue #3046 — UIKit API for navigation between Compose screens](https://github.com/JetBrains/compose-multiplatform/issues/3046)

### iOS Privacy & Security (HIGH — Apple official)
- [Apple — Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)
- [Apple — Adding privacy manifest to app or third-party SDK](https://developer.apple.com/documentation/bundleresources/adding-a-privacy-manifest-to-your-app-or-third-party-sdk)
- [Apple — Describing use of required reason API](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api)
- [Apple — kSecAttrSynchronizable](https://developer.apple.com/documentation/security/ksecattrsynchronizable)
- [Apple — iCloud Keychain security overview](https://support.apple.com/guide/security/icloud-keychain-security-overview-sec1c89c6f3b/web)
- [Apple — App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Apple — BGAppRefreshTask documentation](https://developer.apple.com/documentation/backgroundtasks/bgapprefreshtask)
- [Apple Developer Forums — iOS Background Execution Limits](https://developer.apple.com/forums/thread/685525)

### Kotlin Multiplatform (HIGH — JetBrains official)
- [Kotlin — Privacy manifest for iOS apps](https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html)
- [Kotlin — Native memory management](https://kotlinlang.org/docs/native-memory-manager.html)
- [Kotlin — iOS integration methods (SPM/CocoaPods)](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html)
- [Kotlin — Adding Swift packages to KMP modules](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Kotlin — Compose navigation deep links](https://kotlinlang.org/docs/multiplatform/compose-navigation-deep-links.html)
- [JetBrains Blog — Compose Multiplatform 1.8.0 iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- [JetBrains Blog — KMP roadmap August 2025](https://blog.jetbrains.com/kotlin/2025/08/kmp-roadmap-aug-2025/)

### Android Background Work (HIGH — Google official)
- [Android Developers — App Standby Buckets](https://developer.android.com/topic/performance/appstandby)
- [Android Developers — Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android Developers — Power management resource limits](https://developer.android.com/topic/performance/power/power-details)
- [Android Developers Blog — Modern background execution in Android](https://android-developers.googleblog.com/2018/10/modern-background-execution-in-android.html)
- [Google Play — Data Safety section requirements](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play — Policy announcement April 2026](https://support.google.com/googleplay/android-developer/answer/16926792)

### 152-ФЗ (HIGH — official Russian legal sources)
- [Федеральный закон 152-ФЗ — редакция 24.06.2025 (Контур)](https://normativ.kontur.ru/document?moduleId=1&documentId=501173)
- [152-ФЗ Ст. 18 — Обязанности оператора при сборе ПДн](https://legalacts.ru/doc/152_FZ-o-personalnyh-dannyh/glava-4/statja-18/)
- [Tochka — Закон 152-ФЗ новые требования и штрафы 2025](https://tochka.com/knowledge/buhgalteriya/zakon-o-personalnyh-dannyh-152-fz-komu-neobhodimo-podavat-uvedomlenie-v-rkn/)

### Community / Tooling (MEDIUM — well-regarded resources)
- [Touchlab SKIE — Swift interop improvement](https://skie.touchlab.co/)
- [SPM for KMP plugin (frankois944/spm4Kmp)](https://github.com/frankois944/spm4Kmp)
- [SQLDelight — Multiple databases discussion (Issue #4559)](https://github.com/sqldelight/sqldelight/issues/4559)
- [Ktor — Client cookies documentation](https://ktor.io/docs/client-cookies.html)
- [Appyx 2.0 — Gesture-driven navigation for CMP](https://medium.com/bumble-tech/appyx-2-0-gesture-driven-navigation-for-compose-multiplatform-a9a55af5a315)
- [KMPNotifier — Kotlin Multiplatform Push Notification](https://github.com/mirzemehdi/KMPNotifier)

### Background tasks (MEDIUM — community analyses)
- [WWDC 2025 — iOS 26 Background APIs explained](https://dev.to/arshtechpro/wwdc-2025-ios-26-background-apis-explained-bgcontinuedprocessingtask-changes-everything-9b5)
- [Best practice: iOS background processing (Uy Nguyen)](https://uynguyen.github.io/2020/09/26/Best-practice-iOS-background-processing-Background-App-Refresh-Task/)
- [WorkManager periodicity (Pietro Maggi, Android Developers Medium)](https://medium.com/androiddevelopers/workmanager-periodicity-ff35185ff006)

---

*Pitfalls research for: ЛИнТех Дневник — Compose Multiplatform клиент к ИАС АВЕРС*
*Researched: 2026-04-27*
