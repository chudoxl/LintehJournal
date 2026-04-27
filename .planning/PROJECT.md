# ЛИнТех Дневник

## What This Is

Кроссплатформенный мобильный клиент (iOS + Android) для электронного дневника **ИАС «АВЕРС: Электронный Классный Журнал» v4.1** (`journal.school28-kirov.ru`), предназначенный для учеников и родителей школы №28 г. Кирова. Приложение работает напрямую с сервером АВЕРС без собственного backend; все пользовательские данные и кэш живут на устройстве. Реализуется на Compose Multiplatform — общий Kotlin-код и UI на iOS и Android.

## Core Value

**Удобный, быстрый и оффлайн-доступный мобильный доступ к оценкам, расписанию и домашним заданиям с поддержкой нескольких учеников в одном приложении** — то, чего не даёт ExtJS-сайт АВЕРС в мобильном браузере. Если из всех функций выживет только это — приложение всё равно решает проблему.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. Building toward these. Все пункты — гипотезы, пока не выпущены и не подтверждены пользователями. -->

- [ ] Просмотр оценок (текущие, итоговые, средний балл, история)
- [ ] Расписание уроков (день/неделя, кабинеты, замены)
- [ ] Домашние задания (список по предметам, сроки, прикреплённые файлы)
- [ ] Push-уведомления о новых оценках, ДЗ и заменах в расписании (через фоновый polling + локальные нотификации)
- [ ] Мульти-аккаунт: сохранение нескольких пар логин/пароль с быстрым переключением между учениками
- [ ] Безопасное хранение учётных данных (iOS Keychain + Android Keystore через expect/actual в Compose Multiplatform)
- [ ] Полноценный оффлайн-режим: все основные экраны открываются без сети из локального кэша
- [ ] Авторизация через login/password АВЕРС (без OAuth — у вендора такого нет)
- [ ] Публикация в App Store и Google Play (включая Privacy Manifest, App Privacy / Data Safety декларации, политику конфиденциальности)

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- **Собственный backend / прокси-сервер** — Решено держать архитектуру on-device для простоты и стоимости; принят компромисс по push-уведомлениям (см. Key Decisions)
- **Раздельные интерфейсы для ученика и родителя** — Различия только на уровне данных от сервера АВЕРС; UI единый, чтобы не дублировать экраны
- **Поддержка других школ / других АВЕРС-инсталляций в v1** — Сначала фиксируемся на `journal.school28-kirov.ru`; обобщение в потенциальный v2
- **Поддержка других ЭЖ (ЭлЖур, Дневник.ру, Сетевой Город)** — Архитектура должна допускать расширение, но интеграция с другими движками — не v1
- **Мгновенный push (как у мессенджеров)** — Невозможно без backend, который держит сессию и пушит через FCM/APNs; принимаем «push когда ОС разрешит»
- **Отправка сообщений / комментирование оценок / связь с учителем** — Если такой функционал есть в АВЕРС, добавим в v2 после оценки спроса
- **Учительский интерфейс (ввод оценок, выставление ДЗ)** — Приложение клиентское, не для педагогов

## Context

**Целевая платформа-источник:**
ИАС «АВЕРС: Электронный Классный Журнал» v4.1 (build 23813) — продукт ИИЦ «АВЕРС» (iicavers.ru, avers-journal.ru). Веб-фронтенд построен на ExtJS 4.x, что предполагает SPA с JSON-API под капотом. На этапе исследования предстоит выяснить:
- Существует ли документированный публичный API АВЕРС
- Какие endpoint'ы использует сам сайт (через DevTools / mitmproxy)
- Как устроена аутентификация и сохранение сессии (cookie / token)
- Какие ограничения rate-limiting / CSRF / CAPTCHA

**Технологический выбор:**
Compose Multiplatform (Kotlin Multiplatform Project, JetBrains) — общий код *и* общий UI для iOS и Android. Платформо-специфичные части (Keychain/Keystore, push, notifications, biometrics, background tasks) изолируются через `expect/actual`. Ожидаемые библиотеки: Ktor (HTTP), kotlinx.serialization, SQLDelight или Room/SQLite (локальный кэш), `multiplatform-settings` или собственная обёртка для secure storage, Koin для DI.

**Пользовательский профиль:**
Опытный разработчик, владеет Kotlin/Compose. Стиль работы — глубокая техническая проработка, без необходимости разжёвывать базовые концепции KMP/Compose. Долгосрочный проект с эволюцией версиями (v1 → v2 → ...).

**Аудитория:**
Учащиеся школы №28 г. Кирова и их родители. Для v1 — единственная инсталляция АВЕРС. Распространение через App Store и Google Play (что подразумевает privacy policy, app review, Apple Developer Program $99/год, Google Play Console $25 разово).

## Constraints

- **Tech Stack**: Compose Multiplatform (Kotlin 2.x + Compose Multiplatform 1.7+) — Принято заранее как осознанный технический выбор; обоснование: единый код и UI для iOS+Android, JetBrains-стек, перспектива зрелой платформы.
- **Architecture**: On-device, без собственного backend — Снижает операционные расходы, упрощает развёртывание, исключает классы ошибок (downtime сервера, миграции БД); цена — ограниченные push-уведомления.
- **Backend Source**: Сервер АВЕРС — единственный источник данных; недоступен для модификации с нашей стороны. API не публичный — придётся реверсить SPA-запросы.
- **Distribution**: Apple App Store + Google Play — требует privacy policy, Apple Developer ($99/год) + Google Play Console ($25), App Privacy / Data Safety декларации, iOS Privacy Manifest.
- **Security**: Хранение чужих учётных данных (АВЕРС login/password) — обязателен iOS Keychain / Android Keystore, опционально biometric prompt; шифрование at-rest и in-transit; HTTPS-only.
- **Offline**: Все основные экраны (оценки, расписание, ДЗ) должны открываться без сети — необходим персистентный локальный кэш с инвалидацией.
- **Push**: Без backend — мгновенный push невозможен; используется фоновый polling (Android `WorkManager`, iOS `BGAppRefreshTask`) + локальные нотификации; задержки до нескольких часов на iOS приняты.
- **Legal/Privacy**: 152-ФЗ (РФ) — обработка ПДн требует политики конфиденциальности; ToS АВЕРС — необходимо проверить на запрет сторонних клиентов; желательно согласие администрации школы №28.

## Key Decisions

<!-- Decisions that constrain future work. Add throughout project lifecycle. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Compose Multiplatform вместо нативной разработки или React Native/Flutter | Один Kotlin-кодбейс с общим UI; зрелость стека JetBrains; знакомый язык; меньше дублирования кода | — Pending |
| Без собственного backend, on-device архитектура | Минимум операционных расходов; нет downtime; нет миграций; принят компромисс по push | — Pending |
| Один интерфейс для ученика и родителя (без разделения ролей) | Различия только в данных от сервера АВЕРС; UI идентичный, не дублируем экраны | — Pending |
| Push через фоновый polling + локальные уведомления | Без backend нет других вариантов; iOS-задержки приняты осознанно | ⚠️ Revisit (если push окажется неприемлемым по UX, вернуться к минимальному backend) |
| Мульти-аккаунт через защищённое хранение нескольких пар логин/пароль | Ключевой пользовательский запрос (несколько детей в семье); безопасность через Keychain/Keystore + biometrics | — Pending |
| Старт с одной инсталляции АВЕРС (`school28-kirov.ru`) | Снижает scope v1; архитектура должна допускать расширение через конфигурируемый base URL | — Pending |
| Kotlin 2.x + Compose Multiplatform stable (≥1.7) | Стабильные релизы для production; iOS-таргет в стабильном статусе с 2024 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-27 after initialization*
