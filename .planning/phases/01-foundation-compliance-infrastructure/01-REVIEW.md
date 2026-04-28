---
phase: 01-foundation-compliance-infrastructure
reviewed: 2026-04-28T00:00:00Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - build.gradle.kts
  - build-logic/convention/build.gradle.kts
  - build-logic/convention/src/main/kotlin/ext/AndroidExt.kt
  - build-logic/convention/src/main/kotlin/ext/KotlinExt.kt
  - build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt
  - build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt
  - build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt
  - build-logic/settings.gradle.kts
  - CLAUDE.md
  - composeApp/build.gradle.kts
  - composeApp/PrivacyInfo.xcprivacy
  - composeApp/src/androidMain/AndroidManifest.xml
  - composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt
  - composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt
  - composeApp/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/AppTestAndroid.kt
  - composeApp/src/androidUnitTest/resources/robolectric.properties
  - composeApp/src/commonMain/composeResources/values/strings.xml
  - composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt
  - composeApp/src/debug/AndroidManifest.xml
  - composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt
  - composeApp/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt
  - core/network/build.gradle.kts
  - core/platform/build.gradle.kts
  - core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt
  - core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt
  - core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt
  - core/ui/build.gradle.kts
  - docs/index.html
  - docs/privacy/index.html
  - .github/workflows/ci.yml
  - .github/workflows/pages.yml
  - .gitignore
  - gradle/libs.versions.toml
  - gradle.properties
  - README.md
  - settings.gradle.kts
findings:
  blocker: 3
  warning: 9
  info: 8
  total: 20
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-04-28
**Depth:** standard
**Files Reviewed:** 36
**Status:** issues_found

## Summary

Adversarial review of Phase 1 foundation infrastructure: Gradle multi-module + convention plugins, Compose Multiplatform setup, expect/actual abstractions (`openUrl`), CI workflows, Privacy Policy и iOS Privacy Manifest.

Качество inertstructure overall — solid: convention plugins корректно вырезают boilerplate, libs.versions.toml единый source of truth, version pinning aligned (Kotlin 2.2.20 ↔ KSP 2.2.20-2.0.4 ↔ Compose 1.10.3), Privacy Manifest reason codes (`CA92.1`, `C617.1`) корректны, CI lint регрессий-protect добавлен. KMP source-set hierarchy через `applyDefaultHierarchyTemplate` и `androidSourceSetLayoutVersion=2` идиоматичны для Kotlin 2.x.

Однако обнаружены дефекты, которые либо приводят к incorrect runtime behavior (BuildKonfig `IS_DEBUG` всегда `true` даже в release), либо ослабляют CI-гарантии (Xcode-select fallback маскирует «не та версия Xcode»), либо создают forward-leaning false claims в опубликованной Privacy Policy (обещания «зашифрованной БД» и «кнопки Выйти» — фичи которых нет). Самое тревожное — `androidApplicationContext` объявлен `public lateinit var` без synchronization и thread-safety; в Phase 1 race-window не реализуется на практике, но контракт оставлен открытым для downstream callers.

## Blocker Issues

### BL-01: BuildKonfig `IS_DEBUG = "true"` hard-coded — release builds will report `IS_DEBUG = true`

**File:** `composeApp/build.gradle.kts:121-125`

**Issue:** В `buildkonfig.defaultConfigs` поле `IS_DEBUG` захардкожено как `"true"` без variant override (нет `release { ... }` или `defaultConfigs("release") { ... }`). BuildKonfig поддерживает per-variant overrides через `defaultConfigs(flavor = "...")`. Сейчас же любой консумер `BuildKonfig.IS_DEBUG` в Kotlin-коде увидит `true` и в debug, и в release сборке. Это приведёт к incorrect логированию (Kermit включит debug-вывод в production), incorrect feature gating (debug-only UI окажется в release), и — критичнее — может «зажечь» неконсистентность с iOS, где `IS_DEBUG` будет тем же значением.

Если Phase 1 строго debug-only (TestFlight/Internal track считается debug), это «работает» случайно. Но как только phase 4-5 добавит TestFlight production-similar build, флаг засветится. Лучше починить сейчас, пока surface маленькая.

**Fix:**
```kotlin
buildkonfig {
    packageName = "io.github.chudoxl.linteh.journal"
    objectName = "BuildKonfig"

    defaultConfigs {
        buildConfigField(STRING, "VERSION_NAME", providers.gradleProperty("versionName").get())
        buildConfigField(STRING, "VERSION_CODE", computeVersionCode())
        buildConfigField(BOOLEAN, "IS_DEBUG", "true")
    }
    defaultConfigs("release") {
        buildConfigField(BOOLEAN, "IS_DEBUG", "false")
    }
}
```
Альтернативно — derive `IS_DEBUG` из Android `BuildConfig.DEBUG` через `expect/actual` в `:core:platform`, чтобы single source of truth. Но variant-override проще для Phase 1.

---

### BL-02: `androidApplicationContext` is `public lateinit var` (top-level mutable global) — reassignment + uninitialized access not guarded

**File:** `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt:20`

**Issue:** Объявление `lateinit var androidApplicationContext: Context` на top-level со неявным `public` visibility создаёт три проблемы:

1. **Mutable**: любой downstream-код в `composeApp` или `:core:*` может **переприсвоить** `androidApplicationContext = otherContext`. Это не просто гипотетика — `lateinit var` без `private set` или `internal set` (которые на top-level не применимы) не имеет защиты. Если будущая фича Phase 4 использует Hilt/Koin и инициализирует context в другом месте, race на init будет тихим.

2. **Unchecked access**: если `openUrl()` вызывается до `MainApplication.onCreate()` (нестандартный path: ContentProvider в `<application>`-блоке инициализируется ДО `Application.onCreate()`; если кто-то добавит ContentProvider, который вызывает `openUrl()` в init, будет `UninitializedPropertyAccessException`).

3. **Nullability surface**: Phase 4 plan обещает миграцию на CompositionLocal, но до тех пор любой instrumented-test или library-context-injection вынужден mutate global state.

Strict thread-safety не нарушается на практике (Android гарантирует `Application.onCreate()` ДО первого Activity), но контракт здесь ослаблен сверх необходимого.

**Fix:** Сделать read-once через wrapper-функцию + private backing:
```kotlin
private var _ctx: Context? = null

fun initApplicationContext(context: Context) {
    check(_ctx == null) { "androidApplicationContext already initialized" }
    _ctx = context.applicationContext
}

private val androidApplicationContext: Context
    get() = _ctx ?: error("Call initApplicationContext() in Application.onCreate() first")

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    androidApplicationContext.startActivity(intent)
}
```
Затем в `MainApplication.onCreate()`: `initApplicationContext(this)`. Это: (a) предотвращает reassignment, (b) даёт ясный error при misuse, (c) `private` getter инкапсулирует поле, оставляя только controlled init API публично.

---

### BL-03: `Verify Xcode version` CI step is informational only — doesn't fail when `xcode-select` fallback chose wrong Xcode

**File:** `.github/workflows/ci.yml:63-71`

**Issue:** Цепочка `Select Xcode 16` использует `||` fallbacks с финальным `|| echo "Using default Xcode"`. Если ни `Xcode_16.app` ни `Xcode_16.0.app` не найдены, step «успешно» завершится с echo, не failing CI. Следующий step `Verify Xcode version` запускает `xcodebuild -version | head -1` — но `head` всегда returns exit 0; output печатается в логи, но **никакой grep / assertion** на нужную major version не выполняется. CI спокойно соберётся под Xcode 15 (если он default на macos-15 runner), и Privacy Manifest plugin может тихо зафейлиться или дать subtly different output.

Это явный pitfall #4-mitigation, который сам себя не закрывает.

**Fix:**
```yaml
- name: Verify Xcode version
  run: |
    set -euo pipefail
    XCODE_VERSION=$(xcodebuild -version | head -1)
    echo "Active: $XCODE_VERSION"
    echo "$XCODE_VERSION" | grep -qE '^Xcode 16\.' \
      || (echo "ERROR: expected Xcode 16.x, got '$XCODE_VERSION'"; exit 1)
```
Это превращает «информационный» step в реальный gate.

---

## Warnings

### WR-01: `openUrl()` Android implementation passes user-string-shaped URL to `Intent.ACTION_VIEW` without scheme allowlist or validation

**File:** `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt:22-27`

**Issue:** `Uri.parse(url)` принимает любую строку, включая `intent://`, `content://`, `file://`, `javascript:` (хотя последний обычно не handle-ится Activity), `tel:`, `geo:`, `market://`. На Phase 1 единственный caller — const URL из strings.xml, что снимает immediate risk. Но:

1. `expect fun openUrl(url: String)` — публичный API в `:core:platform`, доступен из всего проекта.
2. iOS-actual имеет defensive `?: return` (NSURL.URLWithString returns null for malformed). Android-actual — нет.
3. В `:core:platform` source comment упоминает «Phase 4 TODO: scheme allowlist», но такой комментарий не enforce-ит API контракт.
4. Если `Uri.parse()` succeeds но нет Activity handle-ит intent (`tel:` на устройстве без dialer), `startActivity()` бросает `ActivityNotFoundException` — uncaught crash.

Эта функция — natural target для Phase 4 misuse: при добавлении teacher-message или AVERS-deep-link rendering любой `openUrl(messageText)` со строкой типа `intent://com.attacker/...` мгновенно становится IPC-injection vector.

**Fix:** Добавить минимальный guard уже сейчас, не ждать Phase 4:
```kotlin
actual fun openUrl(url: String) {
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (parsed.scheme?.lowercase() !in setOf("https", "http")) return
    val intent = Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { androidApplicationContext.startActivity(intent) }
}
```
Симметрично iOS-actual'у (который уже defensive). Документировать allowlist в commonMain doc-комментарии.

---

### WR-02: Privacy Policy claims features not implemented in Phase 1 — «зашифрованная локальная база» и «Кнопка Выйти»

**File:** `docs/privacy/index.html:55-58, 73-75`

**Issue:** Опубликованная Privacy Policy декларирует:
- «Кэш оценок, расписания, домашних заданий — в зашифрованной локальной базе» — БД ещё не существует (Phase 3+); никакой шифрации at-rest в Phase 1 нет.
- «Cookies сессии АВЕРС — также локально» — cookies не существуют (no auth, no Ktor; Phase 2+).
- «Кнопка "Выйти" в настройках приложения удаляет все локальные данные...» — никакого Settings экрана и no logout flow в Phase 1.

Это forward-promises на пользовательские фичи, которые ещё не реализованы. Поскольку Privacy Policy уже задеплоена на GitHub Pages (CI `pages.yml` запустится при первом push), внешние пользователи (TestFlight, Internal track) увидят эти claims. Технически — мисрепрезентация состояния продукта.

При personal/семейном использовании в v1 risk низкий, но: (а) если Apple App Review посмотрит Privacy Policy в v2, mismatch с фактическим состоянием приложения может вызвать вопросы; (б) даже в семейном кругу честность важна.

**Fix:** Либо переформулировать в future tense («когда появится возможность входа в АВЕРС, ваш пароль будет храниться в Keychain/Keystore...»), либо явно секционировать «Текущая версия 0.1.0 не содержит логин-flow и БД; этот документ описывает целевое поведение v1.». Минимально:
```html
<p><em>Замечание для версии 0.1.0:</em> текущая сборка является технической foundation-версией
без логин-flow и БД. Раздел ниже описывает целевую модель данных v1; по мере подключения функций
этот документ будет обновляться (история — git).</p>
```

---

### WR-03: Privacy Policy `<footer>` placeholder `{{LAST_MODIFIED}}` visible if user opens raw HTML (без Pages substitution)

**File:** `docs/privacy/index.html:87`

**Issue:** Источник политики содержит литерал `{{LAST_MODIFIED}}`. Substitution выполняется ТОЛЬКО в `pages.yml` workflow через `sed -i`. Если разработчик / пользователь / reviewer открывает файл напрямую в браузере (`file:///` локально или GitHub raw view), он видит `{{LAST_MODIFIED}}` в строке «Последнее обновление». Это:

1. Confuse-ит preview-просмотр.
2. Если `pages.yml` упадёт (например, fetch-depth=0 fix не сработает на каком-то edge case), Pages-published version тоже unsubstituted.
3. Нет protection: `Stamp Last-Modified date` step имеет `if [[ -z "${LAST_MODIFIED}" ]]` guard, но не fallback к чему-то осмысленному (commit SHA, build date).

**Fix:** В source-HTML использовать sentinel-defaults, читаемый «как есть»:
```html
<p>Последнее обновление: <span data-stamp="last-modified">текущая разработка (см. git history)</span><br>
```
Pages step заменяет content внутри `<span data-stamp="last-modified">`:
```bash
sed -i "s|<span data-stamp=\"last-modified\">[^<]*</span>|<span data-stamp=\"last-modified\">${LAST_MODIFIED}</span>|" docs/privacy/index.html
```
Тогда raw view даёт читаемый текст, deployed view — конкретную дату.

---

### WR-04: `versionCode` / `BuildKonfig.VERSION_CODE` invokes `git rev-list` at configure-time on every Gradle invocation

**File:** `composeApp/build.gradle.kts:84-89, 113-119`

**Issue:** Триггер chain:
```kotlin
versionCode = (
    System.getenv("GITHUB_RUN_NUMBER")
        ?: providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
            .standardOutput.asText.get().trim().ifBlank { "1" }
).toInt()
```

`providers.exec { ... }.standardOutput.asText.get()` — `.get()` форсирует synchronous execution. Это означает: каждый `./gradlew help`, `./gradlew tasks`, IDE-sync, Studio-import шеллит `git rev-list --count HEAD`. На свежем repository — миллисекунды, на больших — секунды. Configuration cache disabled (`org.gradle.configuration-cache=false` в gradle.properties), значит результат не кэшируется.

Дополнительно:
1. `.toInt()` может бросить `NumberFormatException`, если `GITHUB_RUN_NUMBER` set вне CI к чему-то non-numeric (theoretical).
2. Block duplicated **identically** в двух местах (versionCode + BuildKonfig.VERSION_CODE) — code duplication; если кто-то поменяет logic в одном месте, другое drift-нет.
3. На shallow clone (`fetch-depth: 1` в Android job CI), `git rev-list --count HEAD` возвращает `1`, а не реальный count. Но `GITHUB_RUN_NUMBER` set, так что в CI fallback не триггерится. Локально, при `git clone --depth 1`, versionCode=1 всегда.

**Fix:** Извлечь в helper-Provider, кэшируемый между потребителями:
```kotlin
val versionCodeProvider = providers.environmentVariable("GITHUB_RUN_NUMBER")
    .orElse(
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().ifBlank { "1" } }
    )
    .map { it.toIntOrNull() ?: 1 }

android {
    defaultConfig {
        versionCode = versionCodeProvider.get()
    }
}
buildkonfig {
    defaultConfigs {
        buildConfigField(STRING, "VERSION_CODE", versionCodeProvider.get().toString())
    }
}
```
Это устраняет duplication, делает `toInt` крушение-safe, и Provider-API лучше совместим с потенциальным включением configuration cache в Phase 4.

---

### WR-05: `compose.uiTest` declared in `commonTest` will pull dependencies into Android JVM unit tests, where it conflicts with Robolectric runtime

**File:** `composeApp/build.gradle.kts:37-46`

**Issue:** Inline-комментарий объясняет почему `compose.uiTest` живёт здесь, а не в convention plugin. Но факт остаётся: `commonTest` deps пропагируются во ВСЕ test source sets, включая `androidUnitTest`. Это означает, что `androidUnitTest`'s classpath содержит и Robolectric, и compose-ui-test artifacts. Источник прежнего BLOCKER 1 (NPE в `RobolectricIdlingStrategy.getHasRobolectricFingerprint`) не устранён архитектурно — обходится через split AppTest на `iosTest/AppTest.kt` + `androidUnitTest/AppTestAndroid.kt`. Это работает, но:

1. **Test code duplication**: оба файла имеют одинаковую логику (4 testTag-проверки). При добавлении нового testTag в App.kt оба теста надо обновлять; легко drift-нуть.
2. **Compose UI Test artifact** в Android JVM classpath раздувает test classpath, медленнее grow-up Robolectric инициализация.
3. Inline-комментарий «LOAD-BEARING: Plan 05 Task 2 (PrivacyManifest plugin) должен использовать targeted Edit, НЕ full rewrite» — load-bearing comment в build.gradle.kts indicates fragile state. Future maintainers не увидят это до regression.

**Fix:** Долгосрочно — `expect/actual` test fixture в `:composeApp` или вынос Hello LinTech composable в `:core:ui` где testing проще (без Application/Robolectric). Краткосрочно — extract тест-body в shared helper (в `commonTest` source set) и оба `AppTest*` файла вызывают его:
```kotlin
// commonTest helper
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertHelloLintechRendered() {
    setContent { App() }
    listOf("app_title", "app_version", "privacy_url", "open_privacy_button").forEach {
        onNodeWithTag(it).assertIsDisplayed()
    }
}

// iosTest/AppTest.kt
class AppTest { @Test fun render() = runComposeUiTest { assertHelloLintechRendered() } }

// androidUnitTest/AppTestAndroid.kt
@RunWith(RobolectricTestRunner::class) @Config(sdk = [33])
class AppTestAndroid { @Test fun render() = runComposeUiTest { assertHelloLintechRendered() } }
```
Это устраняет drift-risk и делает rationale явным.

---

### WR-06: `Lint PrivacyInfo.xcprivacy` Step 5 array-check regex is brittle — depends on plutil output formatting

**File:** `.github/workflows/ci.yml:115-118`

**Issue:** Step 5 запускает:
```bash
COLLECTED=$(plutil -extract NSPrivacyCollectedDataTypes xml1 "$PLIST" -o -)
echo "$COLLECTED" | grep -qE '<array(/| )'
```

Regex `<array(/| )` matches:
- `<array/>` (self-closing empty array — текущий case)
- `<array ` (space, e.g. `<array ID="...">` — unlikely)

Не matches:
- `<array>` (no whitespace, no slash)
- `<array\n` (newline before content)

Если Apple `plutil` версия в будущем macos runner-image поменяет formatting (например, выдаст `<array>\n</array>` для empty), check молча упадёт с false-positive ERROR. Brittle CI gate.

**Fix:** Использовать `plutil -convert json` + jq, что куда более robust:
```bash
COLLECTED_TYPE=$(plutil -convert json -o - "$PLIST" | jq -r '.NSPrivacyCollectedDataTypes | type')
test "$COLLECTED_TYPE" = "array" \
  || (echo "ERROR: NSPrivacyCollectedDataTypes must be JSON array (got: $COLLECTED_TYPE)" && exit 1)
COLLECTED_LEN=$(plutil -convert json -o - "$PLIST" | jq -r '.NSPrivacyCollectedDataTypes | length')
test "$COLLECTED_LEN" = "0" \
  || (echo "ERROR: NSPrivacyCollectedDataTypes must be empty array in Phase 1 (got len=$COLLECTED_LEN)" && exit 1)
```
`jq` есть на macos-15 runner по умолчанию.

---

### WR-07: `composeApp` `commonTest` mixes `compose.uiTest` (commonMain Compose Multiplatform) with `androidUnitTest`'s `compose.ui:ui-test-junit4` (transitively from `runComposeUiTest`) — version drift potential

**File:** `composeApp/build.gradle.kts:37-56`

**Issue:** `commonTest` использует `compose.uiTest` из `org.jetbrains.compose:components` (CMP 1.10.3). `androidUnitTest` через Robolectric+`runComposeUiTest` подтягивает Android Compose UI test artifact (из CMP). При расхождении CMP version и Android Compose UI version (в случае manual override) test runner не запустится с cryptic ClassDef errors. Текущий setup использует только CMP-managed deps, так что версии aligned. Но если кто-то добавит `androidx.compose.ui:ui-test-junit4` напрямую в androidUnitTest (что соблазнительно для Espresso-flow), drift возможен.

**Fix:** Закомментировать explicit policy в `composeApp/build.gradle.kts`:
```kotlin
// POLICY: НЕ добавлять androidx.compose.ui:ui-test-* напрямую — все Compose-test deps
// должны идти через compose.uiTest (CMP-managed) для version-alignment между common/iOS/Android.
```
И добавить версионную проверку в CI или README.

---

### WR-08: `MainApplication` doesn't initialize logging or crash reporting; silent failure of `openUrl` on iOS

**File:** `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt`, `core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt:7`

**Issue:** Two related quality issues:

1. **iOS `openUrl` silently fails on invalid URL**: `NSURL.URLWithString(url) ?: return` — если URL malformed, функция тихо завершается без log-сообщения. Пользователь нажимает «Открыть» и ничего не происходит. Без логирования диагностика невозможна.
2. **No Kermit / OS-level logger initialized**: CLAUDE.md рекомендует Kermit как стандарт. `MainApplication.onCreate()` идеальное место для setup, но текущая реализация только устанавливает `androidApplicationContext`. iOS-side `MainViewController` тоже без logger setup. Результат: даже добавив `Logger.e()` сейчас, output не пойдёт в OSLog/Logcat correctly.

Это не блокер для Phase 1 (нет error-cases которые требовали бы log), но Phase 2+ (Ktor reverse-engineering) **критически** требует verbose HTTP-логи. Setup делается раз, лучше сейчас.

**Fix:** Добавить Kermit init в `MainApplication.onCreate()` и `MainViewController()`. iOS-side `openUrl` должен логировать silent-skip:
```kotlin
// ios
actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: run {
        Logger.w("UrlOpener") { "Invalid URL, skipping: $url" }
        return
    }
    UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
}
```
Это можно отложить, но добавить TODO-маркер. На сейчас flagging как WARNING — silent failures противоречат принципу «fail loud» и затруднят dev в Phase 2.

---

### WR-09: GitHub Actions versions pinned by major-tag, not by commit SHA — supply-chain attack surface

**File:** `.github/workflows/ci.yml:22, 25, 31`, `.github/workflows/pages.yml:27, 32, 51, 57`

**Issue:** Все actions pinned по major: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`, `actions/deploy-pages@v4`. Если maintainer (или скомпрометированный maintainer) one of these репозиториев push-нет malicious tag-update (tag re-pointing на vulnerable commit), все CI runs автоматически их подхватят. Mitre, GitHub security team рекомендуют SHA-pinning для high-trust workflows — особенно для pages-deploy с `id-token: write`.

Major-pinning — accepted practice для GitHub-owned actions (`actions/*`), но `gradle/actions/*` принадлежит Gradle Inc, что слегка увеличивает surface. В Phase 1 personal-use risk низкий, но как только TestFlight build начнёт подписываться через CI и появится `secrets.APPLE_API_KEY`, supply-chain risk материализуется.

**Fix:** Использовать [`pin-github-action`](https://github.com/mheap/pin-github-action) или Dependabot для auto-SHA-pin:
```yaml
- name: Checkout
  uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11  # v4.1.1
```
Не блокирует Phase 1, но добавить в Phase 5 (security hardening) backlog.

---

## Info

### IN-01: `-Xexpect-actual-classes` comment misleading

**File:** `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt:13`

**Issue:** Комментарий «freeCompilerArgs — strict expect/actual matching (Kotlin 2.0+)». Флаг `-Xexpect-actual-classes` на самом деле permits использование expect/actual **классов** (которые в Kotlin 2.x всё ещё в Beta), а не «strict matching». Strict matching включён по умолчанию.

**Fix:** Уточнить:
```kotlin
// Permits expect/actual classes (Beta in Kotlin 2.x; suppresses warning for Phase 4+ expect class needs).
// Не нужен для expect funs — они stable. Можно удалить пока не появятся expect classes.
freeCompilerArgs.add("-Xexpect-actual-classes")
```
Или удалить флаг до Phase 4, если в Phase 1-3 нет expect classes.

---

### IN-02: `LintechKmpConventionPlugin` applies `com.android.library` unconditionally — `:composeApp` (Application) has to NOT use this plugin

**File:** `build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt:8`

**Issue:** Convention plugin `lintech-kmp` всегда apply-ит `com.android.library`. `:composeApp` использует `com.android.application`, поэтому НЕ может consume `lintech-kmp` — что объясняет почему `composeApp/build.gradle.kts` повторяет `kotlin { androidTarget() ... }` руками без convention plugin. Это работает, но: (a) duplicates 12 строк target setup, (b) пропускает `-Xexpect-actual-classes` flag, (c) пропускает `commonTest { kotlin("test") }` setup. Сейчас composeApp получает kotlin-test через `lintech-test` так что этот gap покрыт, но (a) и (b) остались.

**Fix:** Split на два convention plugin'а: `lintech-kmp-library` (текущий) + `lintech-kmp-base` (только Kotlin Multiplatform setup без AGP-specific). composeApp consume-ит `lintech-kmp-base` + manual `com.android.application`.

Низкий-priority refactor; Phase 1 работает.

---

### IN-03: `LintechComposeConventionPlugin` использует `ComposePlugin.Dependencies(target)` constructor — Compose internal API

**File:** `build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt:14`

**Issue:** `val compose = ComposePlugin.Dependencies(target)` instantiates internal Compose Gradle plugin class напрямую. Если JetBrains меняет signature `Dependencies(...)` (что они делали между CMP releases), convention plugin сломается с cryptic NoClassDefFoundError. Idiomatic путь — extension-property: `the<ComposeExtension>().dependencies` или через `extensions.getByType<ComposeExtension>()`.

**Fix:** Переписать на extension-API:
```kotlin
extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.named("commonMain") {
        dependencies {
            val compose = extensions.getByType<org.jetbrains.compose.ComposeExtension>().dependencies
            implementation(compose.runtime)
            // ...
        }
    }
}
```
Может потребовать `pluginManager.withPlugin("org.jetbrains.compose") { ... }` обёртку для timing.

---

### IN-04: Hardcoded SDK 33 в Robolectric — disconnect от `compileSdk = 35`

**File:** `composeApp/src/androidUnitTest/resources/robolectric.properties:1`, `composeApp/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/AppTestAndroid.kt:13`

**Issue:** `robolectric.properties` set'ит `sdk=33`, но `compileSdk = 35` / `targetSdk = 35`. Robolectric 4.14.1 поддерживает SDK 34 и 35 (с заметкой о preview limitations). Тестируя на SDK 33, можно пропустить behaviour changes в SDK 34+ (предачу теневых поведений, новые permission models). Также annotation `@Config(sdk = [33])` дублирует properties — annotation overrides, но source of truth разнесён.

**Fix:** Если SDK 35 instrumentation working — поднять и в `robolectric.properties`, и убрать `@Config(sdk = ...)` (оставить properties). Если есть конкретный bug на SDK 35 (Robolectric image не download-ится) — задокументировать в комментарии, иначе оставить упоминание о намеренном backport.

---

### IN-05: `lintech-test` convention plugin imports kotlin("test") дважды

**File:** `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt:24-28`, `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt:15-23`

**Issue:** `configureKotlinMultiplatform()` (KotlinExt.kt) добавляет `implementation(kotlin("test"))` в `commonTest`. `LintechTestConventionPlugin.apply()` тоже добавляет `implementation(kotlin("test"))` в `commonTest`. Gradle dedup-ит идентичные deps, так что это работает, но maintenance-wise это duplication — если завтра `lintech-test` нужен только для select-модулей (а `lintech-kmp` нет), unclear где single source of truth.

**Fix:** Удалить из `KotlinExt.kt`:
```kotlin
// In KotlinExt.kt — remove:
sourceSets.named("commonTest") {
    dependencies {
        implementation(kotlin("test"))
    }
}
// Single source of truth — LintechTestConventionPlugin.
```

---

### IN-06: `lintech-test` plugin использует `libs.findLibrary(...).get()` — silent failure если library не зарегистрирована

**File:** `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt:19-21`

**Issue:** `libs.findLibrary("kotest-assertions").get()` возвращает Optional; `.get()` бросает `NoSuchElementException` если не найдено. Сообщение: «No value present» — без указания что отсутствует. На Phase 1 это OK (libs зарегистрированы), но как только появится опечатка (`kotest-assertion` без `s`), error будет cryptic.

**Fix:**
```kotlin
fun VersionCatalog.requireLibrary(alias: String) =
    findLibrary(alias).orElseThrow { GradleException("Missing library alias '$alias' in libs.versions.toml") }

implementation(libs.requireLibrary("kotest-assertions"))
```

---

### IN-07: `gradle.properties` `org.gradle.configuration-cache=false` — explicit disable, no rationale

**File:** `gradle.properties:14`

**Issue:** Configuration cache explicitly disabled. Comment marker `D-16` указывает на decision rationale в planning docs, но в самом gradle.properties нет inline-explanation почему. Configuration cache — major perf win (5-10x faster Gradle config) и обычно требует только `providers.exec`-rewrites (что вы уже частично делаете). Если disable обусловлен `git rev-list` exec выше — это closeable issue.

**Fix:** Добавить inline-комментарий с конкретной причиной:
```properties
# Disabled: providers.exec в composeApp/build.gradle.kts versionCode resolution
# не cacheable до WR-04 fix. Re-enable когда git-rev-list переедет в Provider-API.
org.gradle.configuration-cache=false
```

---

### IN-08: `Run iOS X64 tests` step, `Link iOS X64 debug framework` step — потенциальная double-build framework artifact

**File:** `.github/workflows/ci.yml:78-89`

**Issue:** `:composeApp:iosX64Test` зависит от `linkDebugTestFrameworkIosX64`, что компилирует test-framework. `:composeApp:linkDebugFrameworkIosX64` компилирует main-framework. Это два разных Gradle task, оба триггерят K/N compilation, который не быстрый (KLib build, link). На текущем CI это ~5-10 мин на каждый, итого 10-20 мин. Если оба нужны — fine. Но порядок: test step запускается ПЕРВЫМ; Privacy Manifest plugin embed-ит manifest в **main**-framework, не test. Lint-step проверяет именно main-framework path: `composeApp/build/bin/iosX64/debugFramework/composeApp.framework/PrivacyInfo.xcprivacy`. Это OK; но `linkDebugFrameworkIosX64` step существует **только** ради Lint. Можно объединить: запустить Lint после `iosX64Test` и `linkDebugFrameworkIosX64` параллельно через `./gradlew :composeApp:iosX64Test :composeApp:linkDebugFrameworkIosX64` в одну команду — Gradle parallelize-ит.

**Fix:**
```yaml
- name: Build iOS X64 (tests + main framework)
  run: ./gradlew :composeApp:iosX64Test :composeApp:linkDebugFrameworkIosX64
```
Удаляет step «Link iOS X64 debug framework», ускоряет CI на 1-2 мин.

---

---

_Reviewed: 2026-04-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
