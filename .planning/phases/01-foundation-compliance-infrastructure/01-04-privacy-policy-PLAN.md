---
phase: 01-foundation-compliance-infrastructure
plan: 04
type: execute
wave: 3
depends_on:
  - 01-02-hello-linteh-PLAN
  - 01-03-ci-workflows-PLAN
files_modified:
  - docs/index.html
  - docs/privacy/index.html
  - .github/workflows/pages.yml
autonomous: false
requirements:
  - COMP-01
must_haves:
  truths:
    - "Privacy Policy опубликована по URL https://chudoxl.github.io/LintehJournal/privacy/ и возвращает HTTP 200"
    - "Privacy Policy содержит RU custom-text политику (НЕ Termly/iubenda generator) — отражает on-device-only архитектуру"
    - "docs/privacy/index.html включает Last-Modified в footer (D-27 versioning через git history + footer date)"
    - "Pages workflow (.github/workflows/pages.yml) запускается на push в main с paths: docs/** + .github/workflows/pages.yml"
    - "Privacy Policy URL в strings.xml уже указывал на этот URL (Plan 02) — после Plan 04 deploy URL разрешается"
    - "README.md ссылка на Privacy Policy URL из Plan 03 теперь работает (HTTP 200 при curl)"
  artifacts:
    - path: "docs/privacy/index.html"
      provides: "RU custom-written Privacy Policy HTML"
      contains: "Политика конфиденциальности"
      min_lines: 40
    - path: "docs/index.html"
      provides: "Root index of GitHub Pages site (links to /privacy/)"
      contains: "Политика конфиденциальности"
    - path: ".github/workflows/pages.yml"
      provides: "GitHub Pages auto-deploy workflow"
      contains: "actions/deploy-pages"
  key_links:
    - from: ".github/workflows/pages.yml"
      to: "docs/"
      via: "actions/upload-pages-artifact path"
      pattern: "path:\\s*'docs'"
    - from: "docs/privacy/index.html"
      to: "on-device architecture (PROJECT.md core constraint)"
      via: "policy text describing local storage"
      pattern: "(локально|on-device|Keychain|Keystore)"
---

<objective>
Опубликовать Privacy Policy на GitHub Pages по URL `https://chudoxl.github.io/LintehJournal/privacy/`. Создать `docs/privacy/index.html` (RU custom-written, отражающий on-device-only архитектуру), `docs/index.html` (root index с link на privacy), `.github/workflows/pages.yml` (auto-deploy через `actions/deploy-pages@v4`).

Purpose: Закрывает COMP-01 success criterion — «Privacy Policy опубликована на отдельном URL и линк виден из проекта». Plan 02 уже закрепил URL в `strings.xml` (compile-time const) и Plan 03 закрепил линк в README.md — после Plan 04 deploy эти линки разрешаются в реальную страницу. Закрывает частично pitfall #3 (Privacy Policy опубликована — снимает privacy-ветку юр. риска для personal-use scope).

Output: HTTP 200 на `https://chudoxl.github.io/LintehJournal/privacy/`, страница содержит RU policy text reflecting on-device-only architecture.
</objective>

<execution_context>
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/workflows/execute-plan.md
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md
@.planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md
@.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
@.planning/phases/01-foundation-compliance-infrastructure/01-02-SUMMARY.md
@.planning/phases/01-foundation-compliance-infrastructure/01-03-SUMMARY.md
@CLAUDE.md
</context>

## Tasks

<tasks>

<task type="auto">
  <name>Task 1: Create Privacy Policy HTML files (docs/privacy/index.html + docs/index.html)</name>
  <files>
    docs/privacy/index.html,
    docs/index.html
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "Privacy Policy GitHub Pages → docs/privacy/index.html" — verbatim HTML; section "Privacy Policy GitHub Pages → Source choice" rationale)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-21 GitHub Pages, D-22 RU only, D-23 custom-written content reflecting on-device, D-27 Last-Modified footer)
    - .planning/PROJECT.md (Constraints — on-device + privacy-first позиционирование, Out of Scope — нет analytics/ads/трекеров)
  </read_first>
  <action>
    **Принятый design (WARNING 3 iter 2 fix):** Plan 03 Task 3 GitHub Pages enable — manual gate, не автоматизирован.
    Plan 04 Task 1 НЕ блокирует execution на этом checkpoint; pages.yml deploy упадёт с
    clear error если Pages не enabled (что surfaces missing setup). Plan 04 Task 3 manual
    verification — точка где разработчик подтверждает что Pages reachable. Acceptable
    trade-off для personal-use distribution в Phase 1. Pre-condition Plan 03 Task 3 manual approval
    проверяется только informally через 01-03-SUMMARY.md "Manual checkpoint outcome" поле.

    1. **Создать `docs/privacy/index.html`** (verbatim per RESEARCH.md "docs/privacy/index.html (RU only, custom)" — full HTML с inline CSS, custom RU policy reflecting on-device семантику). **BLOCKER 4 mitigation:** footer содержит `{{LAST_MODIFIED}}` placeholder вместо hardcoded date — actual date stamping выполняется в pages.yml workflow через sed-step (см. Task 2). Это даёт автоматическое обновление footer при каждом edit `docs/privacy/index.html` без manual edit:
    ```html
    <!DOCTYPE html>
    <html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Политика конфиденциальности — ЛИнТех Дневник</title>
        <style>
            body {
                max-width: 720px;
                margin: 2rem auto;
                padding: 1rem;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                line-height: 1.6;
                color: #222;
            }
            h1, h2 { color: #1a1a1a; }
            h1 { border-bottom: 2px solid #ccc; padding-bottom: 0.5rem; }
            h2 { margin-top: 2rem; }
            ul { padding-left: 1.5rem; }
            li { margin-bottom: 0.5rem; }
            code {
                background: #f4f4f4;
                padding: 0.1rem 0.3rem;
                border-radius: 3px;
                font-family: ui-monospace, "Cascadia Code", Menlo, monospace;
                font-size: 0.95em;
            }
            footer {
                margin-top: 3rem;
                padding-top: 1rem;
                border-top: 1px solid #eee;
                font-size: 0.9rem;
                color: #666;
            }
            a { color: #0366d6; }
            a:hover { text-decoration: none; }
        </style>
    </head>
    <body>
        <h1>Политика конфиденциальности</h1>
        <p><strong>«ЛИнТех Дневник»</strong> — неофициальный мобильный клиент для электронного дневника
        ИАС «АВЕРС: Электронный Классный Журнал» школы №28 г. Кирова.</p>

        <h2>Что мы НЕ делаем</h2>
        <ul>
            <li>Мы не отправляем ваши данные третьим лицам.</li>
            <li>Мы не используем рекламу и трекеры.</li>
            <li>Мы не собираем аналитику использования.</li>
            <li>Мы не имеем доступа к вашим учётным данным или оценкам — у нас нет собственного сервера.</li>
        </ul>

        <h2>Что хранится на вашем устройстве</h2>
        <p>Все данные приложения хранятся локально на устройстве:</p>
        <ul>
            <li>Логин и пароль АВЕРС — в защищённом хранилище ОС (iOS Keychain, Android Keystore)</li>
            <li>Кэш оценок, расписания, домашних заданий — в зашифрованной локальной базе</li>
            <li>Cookies сессии АВЕРС — также локально</li>
        </ul>

        <h2>С какими сервисами мы общаемся</h2>
        <p>Приложение обменивается данными только с одним адресом:
        <code>journal.school28-kirov.ru</code> — официальный сайт ИАС АВЕРС школы №28.</p>
        <p>HTTPS-соединение обязательно. Сертификат проверяется системой устройства.</p>

        <h2>Перечень обрабатываемых ПДн</h2>
        <ul>
            <li>Логин (email или телефон) — для входа в АВЕРС</li>
            <li>Пароль — для входа в АВЕРС, хранится локально, не покидает устройство кроме запроса к АВЕРС</li>
            <li>ФИО ученика — отдаётся сервером АВЕРС в ответ на ваш запрос</li>
            <li>Оценки, расписание, домашние задания, посещаемость, сообщения от учителей — данные, которые вы получаете из АВЕРС</li>
        </ul>

        <h2>Удаление данных</h2>
        <p>Кнопка «Выйти» в настройках приложения удаляет все локальные данные аккаунта (логин/пароль из Keychain/Keystore,
        кэш базы, cookies). После выхода никаких данных приложения на устройстве не остаётся.</p>

        <h2>Связь с разработчиком</h2>
        <p>Приложение разработано <a href="mailto:chxevdev@gmail.com">независимым разработчиком</a>
        для семейного использования. Не аффилировано с ИИЦ «АВЕРС», школой №28 или Минобром Кировской области.</p>

        <h2>Изменения в политике</h2>
        <p>История изменений политики ведётся в публичном репозитории
        <a href="https://github.com/chudoxl/LintehJournal">github.com/chudoxl/LintehJournal</a>
        (файл <code>docs/privacy/index.html</code>, история git).</p>

        <footer>
            <p>Последнее обновление: {{LAST_MODIFIED}}<br>
            Версия приложения, на момент публикации политики: 0.1.0</p>
        </footer>
    </body>
    </html>
    ```
    Note: Privacy Policy единственное место, где появляется personal email `chxevdev@gmail.com` (T-01-01 mitigation: email — публичный контакт-канал, не PII третьих лиц).

    2. **Создать `docs/index.html`** — root landing page с links (минимальный, на случай если кто-то посетит `https://chudoxl.github.io/LintehJournal/` без `/privacy/` суффикса):
    ```html
    <!DOCTYPE html>
    <html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>ЛИнТех Дневник</title>
        <style>
            body {
                max-width: 720px;
                margin: 2rem auto;
                padding: 1rem;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                line-height: 1.6;
                color: #222;
            }
            h1 { color: #1a1a1a; }
            a { color: #0366d6; }
        </style>
    </head>
    <body>
        <h1>ЛИнТех Дневник</h1>
        <p>Кроссплатформенный мобильный клиент для электронного дневника
        ИАС «АВЕРС» школы №28 г. Кирова.</p>
        <h2>Документы</h2>
        <ul>
            <li><a href="./privacy/">Политика конфиденциальности</a></li>
        </ul>
        <h2>Репозиторий</h2>
        <p><a href="https://github.com/chudoxl/LintehJournal">github.com/chudoxl/LintehJournal</a></p>
    </body>
    </html>
    ```

    3. Verify HTML well-formed: `python3 -c "from xml.etree import ElementTree as ET; ET.fromstring(open('docs/privacy/index.html').read().replace('&', '&amp;'))" 2>&1 || echo OK_HTML5_NOT_XML`. Note: HTML5 is not XML-strict — этот test может упасть на `&nbsp;` или `<br>` без self-close — это acceptable для HTML. Альтернатива — просто `grep` для критичных тегов.
  </action>
  <verify>
    <automated>test -f docs/privacy/index.html && test -f docs/index.html && grep -q '<title>Политика конфиденциальности — ЛИнТех Дневник</title>' docs/privacy/index.html && grep -q 'journal.school28-kirov.ru' docs/privacy/index.html && grep -q 'iOS Keychain, Android Keystore' docs/privacy/index.html && grep -q '{{LAST_MODIFIED}}' docs/privacy/index.html && grep -q 'href="./privacy/"' docs/index.html && wc -l docs/privacy/index.html | awk '{ exit ($1 < 40) ? 1 : 0 }'</automated>
  </verify>
  <acceptance_criteria>
    - **Принятый design (WARNING 3 iter 2 fix):** Plan 03 Task 3 GitHub Pages enable — manual gate, не автоматизирован. Plan 04 Task 1 НЕ блокирует execution на этом checkpoint; pages.yml deploy упадёт с clear error если Pages не enabled (что surfaces missing setup). Plan 04 Task 3 manual verification — точка где разработчик подтверждает что Pages reachable. Acceptable trade-off для personal-use distribution в Phase 1. Pre-condition `01-03-SUMMARY.md` "Manual checkpoint outcome" поле — informational reference, не enforcement gate.
    - File `docs/privacy/index.html` exists, ≥40 lines (RESEARCH min_lines requirement, ensures full content)
    - File `docs/privacy/index.html` contains `<html lang="ru">` (D-22 RU only)
    - File `docs/privacy/index.html` contains `<title>Политика конфиденциальности — ЛИнТех Дневник</title>`
    - File `docs/privacy/index.html` contains heading `<h1>Политика конфиденциальности</h1>`
    - File `docs/privacy/index.html` contains exact phrase `journal.school28-kirov.ru` (D-23 — единственный сервер с которым общается приложение)
    - File `docs/privacy/index.html` contains `iOS Keychain, Android Keystore` (D-23 — on-device storage семантика из PROJECT.md)
    - File `docs/privacy/index.html` contains explicit "Мы не отправляем ваши данные третьим лицам" + "Мы не используем рекламу и трекеры" + "Мы не собираем аналитику использования" (PROJECT.md Out of Scope reflected)
    - File `docs/privacy/index.html` footer contains `{{LAST_MODIFIED}}` placeholder (BLOCKER 4 fix — actual date stamped by pages.yml workflow via sed; D-27 versioning approach automated)
    - File `docs/privacy/index.html` links to `https://github.com/chudoxl/LintehJournal` (changelog reference)
    - File `docs/index.html` exists and contains link `href="./privacy/"`
    - File `docs/privacy/index.html` does NOT contain Termly/iubenda boilerplate strings (custom-written verification — D-23 forbids generators)
  </acceptance_criteria>
  <done>
    Privacy Policy HTML файлы готовы для deploy. Содержание соответствует on-device семантике PROJECT.md и CONTEXT D-23 — custom RU policy без analytics/ads claims.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create .github/workflows/pages.yml — GitHub Pages auto-deploy workflow with full git history checkout</name>
  <files>
    .github/workflows/pages.yml
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "GitHub Actions Workflows → pages.yml" — verbatim YAML; section "Privacy Policy GitHub Pages → Source choice" rationale на actions/deploy-pages over Jekyll)
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-21 GitHub Pages с auto-deploy)
    - .github/workflows/ci.yml (создан в Plan 03 — для понимания existing workflow structure)
    - docs/privacy/index.html (создан в Task 1)
  </read_first>
  <action>
    Создать `.github/workflows/pages.yml` (per RESEARCH.md "GitHub Actions Workflows → pages.yml" — `actions/deploy-pages@v4` workflow, НЕ legacy gh-pages branch).

    **BLOCKER 3 iter 2 mitigation:** `actions/checkout@v4` defaults to `fetch-depth: 1`. `git log -- docs/privacy/index.html` на shallow checkout возвращает empty → `Stamp Last-Modified date` step упадёт на пустую переменную (или fails-with-error per `set -euo pipefail` + explicit empty-check). Установить `fetch-depth: 0` для full git history.

    ```yaml
    name: Deploy GitHub Pages

    on:
      push:
        branches: [main]
        paths:
          - 'docs/**'
          - '.github/workflows/pages.yml'

    permissions:
      contents: read
      pages: write
      id-token: write

    concurrency:
      group: "pages"
      cancel-in-progress: false

    jobs:
      deploy:
        environment:
          name: github-pages
          url: ${{ steps.deployment.outputs.page_url }}
        runs-on: ubuntu-latest
        steps:
          - name: Checkout
            uses: actions/checkout@v4
            with:
              fetch-depth: 0  # BLOCKER 3 iter 2 fix: full git history требуется для `git log -- file` substitution в "Stamp Last-Modified date" step

          - name: Setup Pages
            uses: actions/configure-pages@v5

          - name: Stamp Last-Modified date
            # BLOCKER 4 mitigation: substitute {{LAST_MODIFIED}} placeholder in HTML footer
            # с actual date последнего git commit к docs/privacy/index.html.
            # Дата автоматически обновляется при каждом edit policy без manual touch.
            # BLOCKER 3 iter 2: requires fetch-depth: 0 above (otherwise `git log -- file` returns empty on shallow checkout).
            run: |
              set -euo pipefail
              LAST_MODIFIED=$(git log -1 --format=%cs -- docs/privacy/index.html)
              if [[ -z "${LAST_MODIFIED}" ]]; then
                echo "ERROR: failed to derive Last-Modified date from git history"
                echo "Hint: actions/checkout@v4 must use fetch-depth: 0 (default 1 = shallow → empty git log)"
                exit 1
              fi
              echo "Stamping Last-Modified=${LAST_MODIFIED}"
              sed -i "s|{{LAST_MODIFIED}}|${LAST_MODIFIED}|g" docs/privacy/index.html

          - name: Upload artifact
            uses: actions/upload-pages-artifact@v3
            with:
              path: 'docs'

          - name: Deploy to GitHub Pages
            id: deployment
            uses: actions/deploy-pages@v4
    ```

    Notes:
    - `paths: docs/**` + `.github/workflows/pages.yml` — workflow триггерится только при изменениях в docs/ или самого workflow file → НЕ блокирует CI основного кода.
    - `concurrency: group: "pages" cancel-in-progress: false` — НЕ отменяем running deploy (важно: incomplete deploy → broken site).
    - `permissions: pages: write + id-token: write` — обязательны для `actions/deploy-pages@v4` (OIDC).
    - **`fetch-depth: 0`** — необходим для full git history в "Stamp Last-Modified date" step (`git log -- file` на shallow checkout returns empty → step fails). Trade-off: больше CI time на download history; для small repo (Phase 1) — negligible (<5s).
    - Pitfall #8 (RESEARCH "Common Pitfalls #8") — Pages должен быть enabled в Settings UI до first run. Это manual step из Plan 03 Task 3.

    Verify YAML syntax: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pages.yml'))"`.
  </action>
  <verify>
    <automated>test -f .github/workflows/pages.yml && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pages.yml'))" && grep -q "actions/deploy-pages@v4" .github/workflows/pages.yml && grep -q "actions/upload-pages-artifact@v3" .github/workflows/pages.yml && grep -q "actions/configure-pages@v5" .github/workflows/pages.yml && grep -q "path: 'docs'" .github/workflows/pages.yml && grep -q "branches: \[main\]" .github/workflows/pages.yml && grep -q "paths:" .github/workflows/pages.yml && grep -q "id-token: write" .github/workflows/pages.yml && grep -q "Stamp Last-Modified date" .github/workflows/pages.yml && grep -q "{{LAST_MODIFIED}}" .github/workflows/pages.yml && grep -A2 "uses: actions/checkout" .github/workflows/pages.yml | grep -q "fetch-depth: 0"</automated>
  </verify>
  <acceptance_criteria>
    - File `.github/workflows/pages.yml` exists and is well-formed YAML
    - File `.github/workflows/pages.yml` triggers on `push` к `main` with `paths: ['docs/**', '.github/workflows/pages.yml']` (avoids unnecessary triggers)
    - File `.github/workflows/pages.yml` uses `actions/deploy-pages@v4` (RESEARCH "Don't Hand-Roll" — НЕ custom shell-script с git push gh-pages)
    - File `.github/workflows/pages.yml` uses `actions/upload-pages-artifact@v3` with `path: 'docs'` (D-21 — `docs/` folder source)
    - File `.github/workflows/pages.yml` uses `actions/configure-pages@v5`
    - File `.github/workflows/pages.yml` declares permissions `contents: read`, `pages: write`, `id-token: write` (OIDC requirements)
    - File `.github/workflows/pages.yml` includes `environment: name: github-pages` block (standard pattern)
    - File `.github/workflows/pages.yml` includes `concurrency: group: "pages" cancel-in-progress: false` (do not cancel in-progress deploys)
    - File `.github/workflows/pages.yml` includes step `Stamp Last-Modified date` BEFORE `Upload artifact` (BLOCKER 4 fix — sed-substitutes `{{LAST_MODIFIED}}` placeholder с git log derived date)
    - **BLOCKER 3 iter 2 fix:** Checkout step имеет `fetch-depth: 0` для full git history (требуется для `git log -- file` substitution в "Stamp Last-Modified date" step; default fetch-depth=1 = shallow checkout = empty git log → step fails)
    - After merge of Plan 04 PR — workflow триггерится; first run может fail если Plan 03 Task 3 manual setup (Pages enable) not completed → manual prerequisite checked в Plan 03 SUMMARY (informational, не enforcement gate per WARNING 3 iter 2)
  </acceptance_criteria>
  <done>
    Pages workflow готов к merge с правильным fetch-depth=0 для git log substitution. После merge — first deploy triggered (зависит от Plan 03 Task 3 manual Pages enable). Privacy Policy URL `https://chudoxl.github.io/LintehJournal/privacy/` будет available, footer покажет actual git-derived date.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Verify Privacy Policy is live on GitHub Pages — visual + curl smoke check</name>
  <what-built>
    Plan 04 Tasks 1+2 создали `docs/privacy/index.html` (RU custom Privacy Policy reflecting on-device-only architecture) + `docs/index.html` (root landing) + `.github/workflows/pages.yml` (auto-deploy via `actions/deploy-pages@v4` с fetch-depth=0 для git log).

    После merge Plan 04 PR — pages.yml workflow триггерится, deploy уходит в `https://chudoxl.github.io/LintehJournal/`. Этот checkpoint валидирует, что deploy succeeded и URL отдаёт expected content.
  </what-built>
  <how-to-verify>
    **Шаг 1 — Verify GitHub Actions deploy succeeded:**
    1. Открыть https://github.com/chudoxl/LintehJournal/actions
    2. Найти workflow run с именем `Deploy GitHub Pages` (триггер — push в main с изменениями в docs/)
    3. Все steps зелёные:
       - `Checkout` ✅ (с fetch-depth=0)
       - `Setup Pages` ✅
       - `Stamp Last-Modified date` ✅ (sed подставил actual date)
       - `Upload artifact` ✅
       - `Deploy to GitHub Pages` ✅ (с output `page_url`)

    Если step `Setup Pages` падает с ошибкой "Pages site not enabled" — Plan 03 Task 3 Шаг 1 был skipped. Решение: вернуться в Plan 03 Task 3, выполнить Pages enable, retrigger workflow (push любое no-op изменение в docs/).

    Если step `Stamp Last-Modified date` падает с "failed to derive Last-Modified date from git history" — verify в pages.yml `actions/checkout@v4` имеет `with: fetch-depth: 0` (BLOCKER 3 iter 2 fix). Если отсутствует — добавить и retrigger.

    **Шаг 2 — Curl smoke test URL:**
    ```bash
    curl -I https://chudoxl.github.io/LintehJournal/privacy/
    ```
    Expected:
    - HTTP/2 200 (или 301 redirect → 200)
    - `content-type: text/html; charset=utf-8`

    ```bash
    curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -q "Политика конфиденциальности"
    echo $?
    ```
    Expected: 0 (grep found the heading).

    ```bash
    curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -q "journal.school28-kirov.ru"
    echo $?
    ```
    Expected: 0 (grep found the AVERS hostname).

    ```bash
    curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -q "iOS Keychain, Android Keystore"
    echo $?
    ```
    Expected: 0 (on-device storage семантика confirmed).

    ```bash
    # BLOCKER 4 verification: убедиться что pages.yml workflow заменил {{LAST_MODIFIED}} placeholder
    # на actual date из git log. После deploy в footer должно быть `Последнее обновление: YYYY-MM-DD`.
    curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -E "Последнее обновление: 20[0-9]{2}-[0-9]{2}-[0-9]{2}"
    echo $?
    ```
    Expected: 0 (matches stamped date pattern). Если в выводе видно literal `{{LAST_MODIFIED}}` — sed-step pages.yml не сработал; проверить логи workflow run, особенно `Stamp Last-Modified date` step (наиболее вероятно: fetch-depth не установлен — BLOCKER 3 iter 2).

    **Шаг 3 — Visual review в браузере:**
    1. Открыть https://chudoxl.github.io/LintehJournal/privacy/ в браузере
    2. Verify:
       - [ ] Заголовок «Политика конфиденциальности»
       - [ ] Секции: «Что мы НЕ делаем», «Что хранится на вашем устройстве», «С какими сервисами мы общаемся», «Перечень обрабатываемых ПДн», «Удаление данных», «Связь с разработчиком», «Изменения в политике»
       - [ ] HTTPS lock-icon в адресной строке (GitHub Pages enforces HTTPS — T-01-skel-01 mitigation)
       - [ ] Footer: `Последнее обновление: YYYY-MM-DD` (actual date stamped by pages.yml; БЛОКЕР 4 fix — placeholder `{{LAST_MODIFIED}}` НЕ должен виднеться)
       - [ ] Cyrillic читается без mojibake (encoding UTF-8 corrected)
    3. Открыть https://chudoxl.github.io/LintehJournal/ → должен показать root index с link `Политика конфиденциальности`.

    **Шаг 4 — Verify приложение link works (опционально):**
    1. На Android-устройстве разработчика запустить `:composeApp:installDebug` (если build делался)
    2. Открыть приложение → видна Hello LinTech
    3. Тапнуть кнопку «Открыть» → системный браузер открывает `https://chudoxl.github.io/LintehJournal/privacy/`
    4. URL отдаёт expected content (та же страница что и в Шаг 3)

    Этот шаг 4 опционален — основной выход Plan 04 это HTTP 200 на URL + correct content. End-to-end UI integration — это COMP-01 success criterion partial (полностью закрывается когда первый APK билдится в release-mode в Phase 6).

    **Шаг 5 — Документировать выполнение:**
    Подтвердить в SUMMARY:
    - Pages deploy run URL (github.com/.../actions/runs/...)
    - URL HTTP 200 confirmed (`curl -I` output)
    - Content grep'ы прошли (Политика конфиденциальности, journal.school28-kirov.ru, iOS Keychain)
    - Visual: cyrillic OK, HTTPS lock yes
    - Last-Modified date stamped correctly (no `{{LAST_MODIFIED}}` literal)
  </how-to-verify>
  <resume-signal>
    После всех проверок ответить:
    - "approved" — URL HTTP 200, content correct, visual OK
    - "approved-pages-issue" — deploy succeeded но что-то выглядит не так (cyrillic mojibake, missing link, etc.) — описать
    - "blocked-pages-not-enabled" — workflow упал на configure-pages → нужно вернуться в Plan 03 Task 3 Шаг 1
    - "blocked-shallow-checkout" — workflow упал на Stamp Last-Modified date → проверить fetch-depth: 0 в checkout step
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Public Internet → GitHub Pages | Privacy Policy URL serving HTTPS to anyone — public, no auth, no PII (T-01-01 mitigation: only public email contact) |
| Repo content → docs/ → Pages site | Static HTML; no client-side JS, no API calls, no analytics |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-01 | I (Information disclosure) — PII в Policy | docs/privacy/index.html commit history | mitigate | HTML review pre-commit: единственный personal data — `chxevdev@gmail.com` (developer contact, intentional, public). НЕТ PII третьих лиц (учеников, родителей). НЕ включает имена, school IDs, real users. Git-history reveal acceptable per D-27. |
| T-01-pages-01 | T (Tampering) — site content modified by attacker | GitHub Pages deploy via id-token | accept | OIDC (`id-token: write`) handles auth; `concurrency: cancel-in-progress: false` prevents partial deploy. Branch protection (Plan 03 Task 3 Шаг 2) blocks direct push to main. |
| T-01-pages-02 | I (Information disclosure) — HTTP downgrade attack | GitHub Pages → user browser | accept | GitHub Pages enforces HTTPS by default (verified в Settings → Pages → "Enforce HTTPS" ON автоматически). RESEARCH security domain confirmed. |
| T-01-pages-03 | T (Tampering) — Pages workflow supply-chain | actions/deploy-pages@v4 + actions/upload-pages-artifact@v3 + actions/configure-pages@v5 | accept | Official GitHub-published actions; pinned to majors. Acceptable risk для personal-use Phase 1. |
</threat_model>

<verification>
- File `docs/privacy/index.html` exists, ≥40 lines, contains all required sections
- File `docs/index.html` exists with link `./privacy/`
- File `.github/workflows/pages.yml` is well-formed YAML, uses `actions/deploy-pages@v4`, имеет `fetch-depth: 0` в checkout step
- After merge: Pages workflow triggers, deploy succeeds (Plan 04 Task 3 manual checkpoint)
- `curl -I https://chudoxl.github.io/LintehJournal/privacy/` returns HTTP 200
- `curl -sf <URL> | grep "Политика конфиденциальности"` succeeds
- Footer of deployed page contains stamped date (no `{{LAST_MODIFIED}}` literal)
- VALIDATION.md Per-Task Verification Map rows `01-04-01`, `01-04-02` updated to ✅ green после deploy
- COMP-01 success criterion #3 (Privacy Policy опубликована, линк виден) — закрыт после Plan 04
</verification>

<success_criteria>
1. **Privacy Policy URL live и returns HTTP 200** — `https://chudoxl.github.io/LintehJournal/privacy/` отдаёт RU policy page reflecting on-device-only architecture (D-23).
2. **`actions/deploy-pages@v4` workflow рабочий** — auto-deploy на push в main для `docs/**` changes; fetch-depth=0 обеспечивает correct git log substitution.
3. **HTTPS lock в браузере** — GitHub Pages enforces HTTPS (T-01-pages-02 mitigation confirmed).
4. **README.md линк работает** — manual click test через раздел "Privacy Policy" в README → URL HTTP 200.
5. **Hello LinTech кнопка "Открыть" разрешает URL** — после Plan 04 deploy expect/actual openUrl(privacyUrl) реально открывает live страницу (verifiable manual-only — Hello LinTech на Android-устройстве разработчика).
6. **D-27 versioning через footer + git history** — Last-Modified в footer = git-derived date (автоматически, через sed substitution в pages.yml); будущие изменения трекаются git blame.
7. **COMP-01 закрыт** — Privacy Policy опубликована (success criterion #3 ROADMAP).
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-compliance-infrastructure/01-04-SUMMARY.md` with:
- What was built (Privacy Policy HTML + pages.yml workflow + auto-deploy с fetch-depth=0)
- Files created (docs/privacy/index.html, docs/index.html, .github/workflows/pages.yml)
- Manual checkpoint outcome from Task 3 (Pages deploy URL, curl results, visual review, stamped date confirmation)
- Key decisions taken from `Claude's Discretion` (точный wording RU policy, HTML inline-CSS vs external, root docs/index.html structure, fetch-depth=0 trade-off)
- COMP-01 status: ✅ closed (Privacy Policy URL live + линк в README + линк в strings.xml)
- Anything Plan 05 should know (Privacy Policy URL canonical reference resolved — Plan 05 PrivacyInfo.xcprivacy ссылается на тот же URL опционально через `NSPrivacyTrackingDomains` empty array)
- Anything Plan 06 should know (CLAUDE.md Update должен включить URL как canonical reference в §Project)
</output>
</content>
</invoke>