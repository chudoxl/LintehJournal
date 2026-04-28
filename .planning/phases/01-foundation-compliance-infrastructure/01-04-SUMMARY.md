---
phase: 01-foundation-compliance-infrastructure
plan: 04
subsystem: compliance/privacy
tags: [privacy-policy, github-pages, deploy, on-device, comp-01]
status: code-complete-deferred-uat
requires:
  - 01-02-hello-linteh-SUMMARY  # strings.xml privacy_url already points here
  - 01-03-ci-workflows-SUMMARY  # README.md links here; manual GitHub setup deferred
provides:
  - "Privacy Policy HTML (RU custom-written, on-device semantics)"
  - "GitHub Pages auto-deploy workflow (actions/deploy-pages@v4)"
  - "Last-Modified date auto-stamping via git history"
affects:
  - "Closes COMP-01 success criterion #3 (Privacy Policy опубликована, линк виден) — pending live URL after manual GitHub Pages enable"
tech-stack:
  added: []  # pure HTML/YAML — no runtime libs
  patterns:
    - "GitHub Pages: actions/deploy-pages@v4 (NOT legacy gh-pages branch)"
    - "Source: docs/ folder via actions/upload-pages-artifact@v3"
    - "Last-Modified versioning: {{LAST_MODIFIED}} placeholder + sed substitution from `git log -1 --format=%cs -- file` (D-27)"
    - "Trigger discipline: paths: ['docs/**', '.github/workflows/pages.yml'] — workflow does not block main code CI"
key-files:
  created:
    - docs/privacy/index.html        # 91-line RU custom Privacy Policy
    - docs/index.html                # root landing with link to /privacy/
    - .github/workflows/pages.yml    # auto-deploy workflow
  modified:
    - .planning/phases/01-foundation-compliance-infrastructure/01-HUMAN-UAT.md  # added items 4-6 for Plan 04 verification
decisions:
  - "RU-only policy (D-22): single-locale matches target audience (school №28, Kirov); добавление en-US отложено в v2 при public release."
  - "Custom-written content (D-23, NOT Termly/iubenda): cookie-cutter generators не отражают on-device-only архитектуру и дают ложные claims про data retention/sharing; reviewable RU text aligns с PROJECT.md Constraints."
  - "Inline CSS, no external assets: один-файл self-contained policy, no CDN deps, no JS, no analytics — соответствует privacy-first позиционированию (не загружаем сторонние ресурсы на странице о приватности)."
  - "Root docs/index.html — minimal landing: предотвращает 404 если кто-то посетит https://chudoxl.github.io/LintehJournal/ без /privacy/ суффикса; даёт обнаружение policy."
  - "Last-Modified via sed-substitution + fetch-depth=0 (BLOCKER 3 iter 2 fix): автоматическое обновление footer date без manual edit; trade-off — ~5s extra CI time на full git history (negligible для small repo)."
  - "Plan 04 Task 3 (live URL verify) deferred per orchestrator: Plan 03 Task 3 step 1 (Pages enable) — manual GitHub UI step also deferred → first deploy will fail until owner enables Pages. Verification combined в 01-HUMAN-UAT.md (items 4–6)."
metrics:
  started: 2026-04-28T05:25:00Z
  completed: 2026-04-28T05:31:00Z
  duration_minutes: 6
  tasks_total: 3
  tasks_completed_in_code: 2
  tasks_deferred: 1  # Task 3 = checkpoint:human-verify (live URL — needs Pages enabled)
  files_created: 3
  files_modified: 1
  commits: 2
---

# Phase 1 Plan 4: Privacy Policy + GitHub Pages Deploy Summary

**Custom RU Privacy Policy HTML reflecting on-device-only architecture, with GitHub Pages auto-deploy workflow using actions/deploy-pages@v4 and automatic Last-Modified stamping from git history.**

## What Was Built

Two HTML files готовы к deploy на GitHub Pages, плюс workflow для автоматического deploy с авто-датированием footer.

### docs/privacy/index.html (91 lines, RU custom)

Custom-written RU Privacy Policy с inline CSS, отражающая on-device-only архитектуру проекта:

- **Что мы НЕ делаем**: эксплицитно перечисляет — нет передачи данных третьим лицам, нет рекламы, нет трекеров, нет аналитики, нет собственного сервера → соответствует Out of Scope из PROJECT.md
- **Что хранится на устройстве**: логин/пароль АВЕРС в iOS Keychain / Android Keystore; кэш в зашифрованной локальной БД; cookies сессии локально
- **С какими сервисами общаемся**: только `journal.school28-kirov.ru` (HTTPS обязателен, certificate pinning делегирован системе)
- **Перечень ПДн**: логин (email/телефон), пароль, ФИО ученика, оценки/расписание/ДЗ/посещаемость/сообщения
- **Удаление данных**: кнопка «Выйти» удаляет всё локально → no leftover state
- **Связь с разработчиком**: `chxevdev@gmail.com` (single PII inclusion — public dev contact, intentional, T-01-01 mitigation)
- **Изменения в политике**: ссылка на git history публичного репо
- **Footer placeholder**: `Последнее обновление: {{LAST_MODIFIED}}` — заменяется sed-step в pages.yml на actual git-derived date (D-27)

### docs/index.html (28 lines, root landing)

Минимальная landing-страница для https://chudoxl.github.io/LintehJournal/ — содержит link на `./privacy/` и на репозиторий. Prededucates 404 visibility если кто-то заходит на root без `/privacy/`.

### .github/workflows/pages.yml (57 lines)

Auto-deploy workflow:

| Aspect | Details |
|--------|---------|
| Trigger | `push` to `main` with `paths: ['docs/**', '.github/workflows/pages.yml']` — узкий filter, не блокирует main CI |
| Concurrency | `group: "pages"`, `cancel-in-progress: false` (не отменяем running deploy → no half-baked deploys) |
| Permissions | `contents: read`, `pages: write`, `id-token: write` (OIDC требования для `actions/deploy-pages@v4`) |
| Steps | 1) `actions/checkout@v4` с **`fetch-depth: 0`** (BLOCKER 3 iter 2 fix) → 2) `actions/configure-pages@v5` → 3) `Stamp Last-Modified date` (sed substitution from `git log -1 --format=%cs -- docs/privacy/index.html`) → 4) `actions/upload-pages-artifact@v3` (path: 'docs') → 5) `actions/deploy-pages@v4` |
| Environment | `name: github-pages`, exposes `${{ steps.deployment.outputs.page_url }}` |

## Files Created/Modified

| Path                                                                                     | Action  | Purpose                                                                                         |
| ---------------------------------------------------------------------------------------- | ------- | ----------------------------------------------------------------------------------------------- |
| docs/privacy/index.html                                                                  | created | RU custom Privacy Policy HTML, on-device semantics                                              |
| docs/index.html                                                                          | created | Root landing page with link to `/privacy/`                                                      |
| .github/workflows/pages.yml                                                              | created | GitHub Pages auto-deploy workflow with Last-Modified stamping                                   |
| .planning/phases/01-foundation-compliance-infrastructure/01-HUMAN-UAT.md                 | modified| Added items 4–6 for Plan 04 verification (combined with Plan 03 Task 3)                          |

## Commits

| Hash      | Type   | Description                                                                              |
| --------- | ------ | ---------------------------------------------------------------------------------------- |
| `d10c890` | feat   | feat(01-04): add custom RU Privacy Policy HTML reflecting on-device-only architecture    |
| `905d112` | feat   | feat(01-04): add GitHub Pages auto-deploy workflow with Last-Modified stamping           |

(Final SUMMARY commit will be created separately.)

## Verification Performed (Code-Level)

### Task 1 — `docs/privacy/index.html` + `docs/index.html`

All acceptance criteria passed via grep/wc:

```
OK: privacy exists
OK: index exists
OK: title "Политика конфиденциальности — ЛИнТех Дневник"
OK: AVERS host "journal.school28-kirov.ru"
OK: "iOS Keychain, Android Keystore"
OK: {{LAST_MODIFIED}} placeholder
OK: href="./privacy/" in root index
OK: <html lang="ru">
OK: <h1>Политика конфиденциальности</h1>
OK: "Мы не отправляем ваши данные третьим лицам"
OK: "Мы не используем рекламу и трекеры"
OK: "Мы не собираем аналитику использования"
OK: github.com/chudoxl/LintehJournal repo link
lines=91 (>=40 required)
OK: no Termly/iubenda boilerplate
```

### Task 2 — `.github/workflows/pages.yml`

YAML valid via `python3 -c "import yaml; yaml.safe_load(...)"`. All structural checks passed:

```
OK: actions/deploy-pages@v4
OK: actions/upload-pages-artifact@v3 + path: 'docs'
OK: actions/configure-pages@v5
OK: branches: [main]
OK: paths: ['docs/**', '.github/workflows/pages.yml']
OK: permissions contents:read, pages:write, id-token:write
OK: Stamp Last-Modified date step (line 29)
OK: {{LAST_MODIFIED}} sed substitution
OK: actions/checkout@v4 with fetch-depth: 0 (BLOCKER 3 iter 2 fix)
OK: environment name: github-pages
OK: concurrency cancel-in-progress: false
OK: Stamp step (line 29) BEFORE Upload step (line 50)
```

### Task 3 — Live URL verification — DEFERRED

Per orchestrator override: cannot verify https://chudoxl.github.io/LintehJournal/privacy/ until Plan 03 Task 3 step 1 (Pages enable in GitHub UI) is completed manually. Verification items 4–6 added to `01-HUMAN-UAT.md` for combined manual UAT.

## Manual Checkpoint Outcome

**Status:** Deferred to combined HUMAN-UAT (`01-HUMAN-UAT.md`).

**Rationale:**

- Plan 03 Task 3 step 1 (Pages enable in `Settings → Pages → Source = GitHub Actions`) was deferred per user choice 2026-04-28
- Without Pages enabled, `pages.yml` first run will fail at the `Setup Pages` step ("Pages site not enabled")
- Therefore curl-checks for HTTP 200 + content greps cannot succeed yet
- Visual review in browser (HTTPS lock, cyrillic readability, Last-Modified stamp) — also pending Pages enable

**Combined manual UAT items added to `01-HUMAN-UAT.md`:**

- Item 4: Pages deploy workflow green (all 5 steps pass after Pages enabled)
- Item 5: 5 curl checks (HTTP 200 + content greps for "Политика конфиденциальности", "journal.school28-kirov.ru", "iOS Keychain, Android Keystore", + stamped date pattern `Последнее обновление: YYYY-MM-DD`)
- Item 6: visual review (sections, HTTPS lock, cyrillic OK, no `{{LAST_MODIFIED}}` literal, root → /privacy/ link)

**Resume signals** are documented in `01-HUMAN-UAT.md` Notes:
- `approved` — all 6 items pass
- `approved-pages-issue` — deploy succeeded but visual issue (mojibake, missing link, etc.)
- `blocked-pages-not-enabled` — workflow failed at Setup Pages → return to UAT item 1
- `blocked-shallow-checkout` — workflow failed at Stamp Last-Modified date → verify fetch-depth: 0
- `blocked-ci-failure` — first CI run red → debug

## Deviations from Plan

None — plan executed exactly as written. Task 3 deferred per orchestrator override (not a deviation, an explicit instruction).

## Auto-fixed Issues

None.

## COMP-01 Status

**Partially closed** (code-complete; live URL pending manual Pages enable):

| COMP-01 success criterion                                                                                              | Plan 04 closes? |
| ---------------------------------------------------------------------------------------------------------------------- | --------------- |
| Privacy Policy опубликована на отдельном URL (https://chudoxl.github.io/LintehJournal/privacy/)                        | ✅ code, ⏳ deploy |
| Линк на Privacy Policy виден из проекта (README.md, strings.xml)                                                        | ✅ (Plan 02 + Plan 03 already wired this URL)  |
| Privacy Policy reflects on-device-only architecture (no analytics/ads/trackers claim)                                  | ✅               |
| Last-Modified versioning (D-27)                                                                                         | ✅ (auto-stamped via git log + sed)            |

After manual UAT items 4–6 pass, COMP-01 is fully closed.

## Notes for Subsequent Plans

### Plan 05 (PrivacyInfo.xcprivacy — iOS Privacy Manifest)

- Privacy Policy URL `https://chudoxl.github.io/LintehJournal/privacy/` is now the canonical reference for the policy text. PrivacyInfo.xcprivacy is technical-only (App Store Connect format); textual policy lives at the URL.
- `NSPrivacyTrackingDomains` in PrivacyInfo.xcprivacy should remain an empty array (`<array/>`) — confirms "no third-party tracking" claim from policy.
- No NSPrivacyAccessedAPI types are used currently (no analytics SDK, no fingerprinting APIs); manifest will declare an empty `NSPrivacyAccessedAPITypes` array. If future plans add Coil/Ktor disk-cache touching `UserDefaults`/`fileTimestamp` reason codes, those need addition then — not a Plan 05 concern.

### Plan 06 (CLAUDE.md update / docs hygiene)

- Add to project §Project block: "Privacy Policy: https://chudoxl.github.io/LintehJournal/privacy/ (canonical)"
- Add to §Constraints: "All policy text changes flow through `docs/privacy/index.html` → auto-deployed via `.github/workflows/pages.yml`. Last-Modified date auto-stamped from git history (no manual touch)."
- Mention `01-HUMAN-UAT.md` as the combined manual UAT register for Phase 1 (Plan 03 Task 3 + Plan 04 Task 3).

### Future plans (post-Phase-1)

- If full localization (en-US Privacy Policy) becomes a v2 requirement (public App Store release), structure: `docs/privacy/index.html` (RU default) + `docs/privacy/en/index.html` + `<link rel="alternate" hreflang="...">` in both. Don't restructure now — D-22 stands for Phase 1.
- If Last-Modified date logic ever shows literal `{{LAST_MODIFIED}}` in production, the most likely cause is `fetch-depth` regression in pages.yml — flag as P0 and re-add `fetch-depth: 0` to checkout.

## Threat Model Compliance

All STRIDE register dispositions from PLAN.md `<threat_model>` honoured:

| Threat ID       | Disposition | Mitigation as implemented                                                                                                |
| --------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------ |
| T-01-01         | mitigate    | docs/privacy/index.html — only `chxevdev@gmail.com` as PII (intentional public dev contact); no other PII third parties. |
| T-01-pages-01   | accept      | OIDC via `id-token: write`; `concurrency: cancel-in-progress: false`; branch protection (Plan 03 Task 3 deferred manual). |
| T-01-pages-02   | accept      | GitHub Pages enforces HTTPS by default (T-01-pages-02 mitigation passive); UAT item 6 visually confirms HTTPS lock.       |
| T-01-pages-03   | accept      | Only official GitHub-published actions used (`deploy-pages@v4`, `upload-pages-artifact@v3`, `configure-pages@v5`).        |

## Self-Check: PASSED

Verified post-write:

- `docs/privacy/index.html` exists (91 lines, all required content) ✅
- `docs/index.html` exists (link to `./privacy/`) ✅
- `.github/workflows/pages.yml` exists (YAML valid, all required actions/steps) ✅
- `01-HUMAN-UAT.md` updated with items 4–6 ✅
- Commit `d10c890` (feat(01-04): Privacy Policy HTML) — present in `git log` ✅
- Commit `905d112` (feat(01-04): pages.yml workflow) — present in `git log` ✅
