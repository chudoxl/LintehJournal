package io.github.chudoxl.linteh.journal.core.platform

/**
 * Открывает URL в системном браузере.
 *
 * Phase 1: используется кнопкой «Открыть» на Hello LinTech screen для Privacy Policy URL.
 * Phase 4+: реиспользуется для всех внешних ссылок (поддержка, AVERS-сайт, ссылки в сообщениях
 * учителей и т. д.).
 *
 * Security note (Phase 4 TODO): на момент Phase 1 единственный caller — Hello LinTech composable
 * с compile-time const URL из strings.xml. Нет user-input → нет URL-injection vector.
 * Когда появятся caller'ы с user-input URL (например, ссылки внутри сообщений), нужно добавить
 * URL validation + scheme allowlist (https-only).
 */
expect fun openUrl(url: String)
