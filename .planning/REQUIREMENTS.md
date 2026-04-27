# Requirements: ЛИнТех Дневник

**Defined:** 2026-04-27
**Core Value:** Удобный, быстрый и оффлайн‑доступный мобильный доступ к оценкам, расписанию и домашним заданиям с поддержкой нескольких учеников в одном приложении.

## v1 Requirements

Требования первой версии. Каждое будет смаплено на одну фазу роадмапа.

### Authentication (AUTH)

- [ ] **AUTH-01**: Пользователь может войти в свой АВЕРС‑аккаунт, введя логин и пароль
- [ ] **AUTH-02**: Учётные данные сохраняются в платформенном защищённом хранилище (iOS Keychain c `kSecAttrSynchronizable=false` / Android EncryptedSharedPreferences через Keystore)
- [ ] **AUTH-03**: Пользователь может выйти из аккаунта; при выходе все локальные данные этого аккаунта удаляются

### Multi-Account (ACCT)

- [ ] **ACCT-01**: Пользователь может добавить несколько аккаунтов АВЕРС в одно приложение
- [ ] **ACCT-02**: Пользователь может переключиться на другой аккаунт за ≤2 тапа из любого экрана
- [ ] **ACCT-03**: Данные между аккаунтами полностью изолированы — отдельный файл БД (`journal_${accountId}.db`) и отдельный HTTP‑клиент на каждый аккаунт
- [ ] **ACCT-04**: Пользователь может удалить аккаунт со всеми его данными (файл БД, кэш изображений, cookies, KVault‑записи)

### Grades (GRAD)

- [ ] **GRAD-01**: Пользователь может просматривать текущие оценки за актуальный учебный период
- [ ] **GRAD-02**: Пользователь может просматривать итоговые оценки за все периоды учебного года
- [ ] **GRAD-03**: Приложение вычисляет средний балл локально (на устройстве), не доверяя значениям сервера
- [ ] **GRAD-04**: Пользователь может просматривать историю оценок по предыдущим периодам (четверти / триместры)
- [ ] **GRAD-05**: Новые оценки помечаются smart‑индикатором «новое»; оценки имеют цветовое кодирование

### Schedule (SCHED)

- [ ] **SCHED-01**: Пользователь может просматривать расписание уроков на день (предмет, время, кабинет, учитель если есть)
- [ ] **SCHED-02**: Пользователь может просматривать расписание на неделю с навигацией по дням
- [ ] **SCHED-03**: Замены уроков визуально маркируются (отдельный цвет/значок), если АВЕРС отдаёт соответствующий флаг
- [ ] **SCHED-04**: На главном экране отображается обратный отсчёт до конца текущего / начала следующего урока (расписание звонков)

### Homework (HW)

- [ ] **HW-01**: Пользователь может просматривать список домашних заданий по предметам

### Attendance (ATT)

- [ ] **ATT-01**: Пользователь может просматривать дневную посещаемость (отметки Н/Б по урокам)
- [ ] **ATT-02**: Пользователь может просматривать итоги пропусков по периодам

### Messages (MSG)

- [ ] **MSG-01**: Пользователь может просматривать сообщения от учителей в read‑only режиме

### Notifications (NOTIF)

- [ ] **NOTIF-01**: Приложение проверяет наличие новых данных в фоновом режиме — на Android через `WorkManager` (periodic), на iOS через `BGAppRefreshTask` + `BGProcessingTask`
- [ ] **NOTIF-02**: Пользователь получает локальное уведомление при появлении новой оценки
- [ ] **NOTIF-03**: Пользователь получает локальное уведомление при появлении нового домашнего задания
- [ ] **NOTIF-04**: Пользователь получает локальное уведомление при изменении расписания (замены)
- [ ] **NOTIF-05**: Пользователь может включать/отключать отдельные типы уведомлений в настройках

### Offline (OFFL)

- [ ] **OFFL-01**: Все основные экраны (оценки, расписание, ДЗ, посещаемость, сообщения) открываются и показывают данные без сети из локального SQLite‑кэша (Room KMP)
- [ ] **OFFL-02**: На каждом экране отображается возраст данных в человеко‑читаемом виде («обновлено 2 ч назад»); >24ч и >72ч помечаются явным предупреждением
- [ ] **OFFL-03**: При отсутствии сетевого соединения отображается явный OfflineBanner
- [ ] **OFFL-04**: Pull‑to‑refresh на каждом экране запускает явное обновление с сервера АВЕРС с визуальной индикацией прогресса

### UI / UX (UI)

- [ ] **UI-01**: Приложение поддерживает тёмную и светлую темы, автоматически следуя системной настройке
- [ ] **UI-02**: Весь интерфейс локализован на русский язык
- [ ] **UI-03**: Все интерактивные элементы имеют accessibility‑метки для VoiceOver (iOS) и TalkBack (Android); поддерживается Dynamic Type на iOS
- [ ] **UI-04**: Пользователь может включить альтернативный шрифт для дислексиков (кириллический OpenDyslexic‑аналог) в настройках

### Analytics (ANALYT)

- [ ] **ANALYT-01**: Приложение показывает прогноз итоговой оценки за период на основе текущих оценок (с дисклеймером, если веса оценок недоступны от АВЕРС)
- [ ] **ANALYT-02**: Приложение показывает график динамики среднего балла по периодам

### Compliance & Infrastructure (COMP)

- [ ] **COMP-01**: Политика конфиденциальности опубликована на отдельном URL (например, GitHub Pages) и доступна из приложения
- [ ] **COMP-02**: iOS‑сборка содержит `PrivacyInfo.xcprivacy` с декларацией required‑reason API (`NSPrivacyAccessedAPICategoryUserDefaults`, `NSPrivacyAccessedAPICategoryFileTimestamp`), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]`

## v2 Requirements

Признанные, но отложенные на следующую версию. Не входят в текущий роадмап.

### Authentication

- **AUTH-V2-01**: Биометрическая разблокировка приложения (Face ID / Touch ID / fingerprint) с fallback на системный пароль
- **AUTH-V2-02**: Auto‑logout через N минут неактивности (настраиваемый интервал)

### Homework

- **HW-V2-01**: Группировка ДЗ по срокам — «сегодня / завтра / далее»
- **HW-V2-02**: Загрузка прикреплённых файлов к ДЗ с offline‑доступом (Coil 3 + size‑limited disk cache)
- **HW-V2-03**: Локальная пометка «сделано» на задании (хранится в БД, не отправляется в АВЕРС)

### Notifications

- **NOTIF-V2-01**: Скрытие персональных данных в уведомлениях на lock screen с настройкой «показывать детали»

### Distribution & Compliance (DIST)

- **DIST-V2-01**: Submission в Apple App Store
- **DIST-V2-02**: Submission в Google Play (открытый трек)
- **DIST-V2-03**: Получение письменного согласия школы №28 на использование приложения учениками/родителями
- **DIST-V2-04**: Регистрация в Роскомнадзоре как оператор персональных данных по 152‑ФЗ
- **DIST-V2-05**: Заполнение App Privacy / Data Safety деклараций в App Store Connect и Google Play Console

### Widgets (WIDG)

- **WIDG-V2-01**: iOS Home Screen / Lock Screen виджеты («следующий урок», «ДЗ на завтра», «средний балл») через WidgetKit + App Groups к shared Room
- **WIDG-V2-02**: Android App Widget через Glance к тому же shared‑хранилищу

### Calendar Integration (CAL)

- **CAL-V2-01**: Экспорт расписания и ДЗ в системный календарь устройства (.ics / EventKit / CalendarContract)

## Out of Scope

Намеренно исключено. Документировано, чтобы не возвращалось как scope creep.

| Feature | Reason |
|---------|--------|
| Собственный backend / прокси‑сервер | On‑device архитектура принята осознанно в PROJECT.md; принят компромисс по push |
| Раздельные интерфейсы для ученика и родителя | Различия только в данных от сервера АВЕРС; UI единый |
| Поддержка других школ или других АВЕРС‑инсталляций | Фокус v1/v2 — `journal.school28-kirov.ru`; архитектура должна допускать расширение, но активной работы не ведём |
| Поддержка других ЭЖ (ЭлЖур, Дневник.ру, Сетевой Город) | Кардинальная смена позиционирования — не v1, не v2 |
| Мгновенный push (как у мессенджеров) | Физически невозможно без backend, который держит сессию и пушит через FCM/APNs |
| Отправка сообщений / комментирование оценок | Клиент read‑only для коммуникации; учительские POST‑эндпоинты АВЕРС не используем |
| Учительский интерфейс (ввод оценок, выставление ДЗ) | Приложение для учеников/родителей, не для педагогов |
| Реклама любого вида | Anti‑feature; усиливает privacy‑позиционирование как differentiator |
| Сторонние аналитики (Firebase Analytics, Yandex.Metrica, Amplitude) | Anti‑feature; усиливает privacy‑позиционирование |
| In‑app purchases / paid PRO | Anti‑feature; не коммерческий проект |
| Геолокация ребёнка | Privacy‑катастрофа + жёсткое требование явного согласия по 152‑ФЗ |
| Геймификация (баллы, бейджи, рейтинги) | Не соответствует утилитарному характеру продукта |
| OAuth / Госуслуги | АВЕРС не поддерживает; единственный путь — login/password |
| Apple Watch / Wear OS companion apps | Сложность UX, requires отдельный таргет; не v1, не v2 |

## Traceability

Какие фазы покрывают какие требования.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 3 | Pending |
| AUTH-02 | Phase 3 | Pending |
| AUTH-03 | Phase 3 | Pending |
| ACCT-01 | Phase 5 | Pending |
| ACCT-02 | Phase 5 | Pending |
| ACCT-03 | Phase 5 | Pending |
| ACCT-04 | Phase 5 | Pending |
| GRAD-01 | Phase 4 | Pending |
| GRAD-02 | Phase 4 | Pending |
| GRAD-03 | Phase 4 | Pending |
| GRAD-04 | Phase 4 | Pending |
| GRAD-05 | Phase 4 | Pending |
| SCHED-01 | Phase 5 | Pending |
| SCHED-02 | Phase 5 | Pending |
| SCHED-03 | Phase 5 | Pending |
| SCHED-04 | Phase 5 | Pending |
| HW-01 | Phase 5 | Pending |
| ATT-01 | Phase 5 | Pending |
| ATT-02 | Phase 5 | Pending |
| MSG-01 | Phase 5 | Pending |
| NOTIF-01 | Phase 6 | Pending |
| NOTIF-02 | Phase 6 | Pending |
| NOTIF-03 | Phase 6 | Pending |
| NOTIF-04 | Phase 6 | Pending |
| NOTIF-05 | Phase 6 | Pending |
| OFFL-01 | Phase 4 | Pending |
| OFFL-02 | Phase 4 | Pending |
| OFFL-03 | Phase 4 | Pending |
| OFFL-04 | Phase 4 | Pending |
| UI-01 | Phase 4 | Pending |
| UI-02 | Phase 4 | Pending |
| UI-03 | Phase 4 | Pending |
| UI-04 | Phase 4 | Pending |
| ANALYT-01 | Phase 4 | Pending |
| ANALYT-02 | Phase 4 | Pending |
| COMP-01 | Phase 1 | Pending |
| COMP-02 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 37 total
- Mapped to phases: 37 ✓
- Unmapped: 0 ✓

**Distribution:**
- Phase 1 (Foundation & Compliance): 2 requirements (COMP-01, COMP-02)
- Phase 2 (API Reverse-Engineering): 0 requirements (infrastructure — enables Phase 3+)
- Phase 3 (Auth & Secure Storage): 3 requirements (AUTH-01..03)
- Phase 4 (UI Shell + Grades + Offline): 15 requirements (GRAD-01..05, OFFL-01..04, UI-01..04, ANALYT-01..02)
- Phase 5 (Multi-Account + Verticals): 12 requirements (ACCT-01..04, SCHED-01..04, HW-01, ATT-01..02, MSG-01)
- Phase 6 (Background Sync + Notifications): 5 requirements (NOTIF-01..05)

---
*Requirements defined: 2026-04-27*
*Last updated: 2026-04-27 — traceability filled (37/37 mapped to 6 phases) after ROADMAP.md creation*
