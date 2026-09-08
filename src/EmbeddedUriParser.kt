package com.andrew.proxyapp.data

import android.util.Base64
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Compatibility parser for subscriptions wrapped in JSON/text/URL encoding/Base64.
 * Native parsers run first; this parser also handles concatenated V2Ray/Xray JSON
 * client configurations, which are commonly returned one after another.
 */
object EmbeddedUriParser {
    private val uriPattern = Regex(
        "(?i)(?:vless|vmess|trojan|ss|socks|socks5|hysteria2|hysteria|tuic|anytls)://[^\\s\\\"'<>\\]}]+"
    )
    private val base64Alphabet = Regex("^[A-Za-z0-9+/=_-]+$")

    fun parse(content: String, subscriptionId: String): SubscriptionParseResult {
        val candidates = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(normalize(content))
        repeat(8) {
            val current = queue.removeFirstOrNull() ?: return@repeat
            addTextVariants(current, candidates, queue)
        }

        // First-class support for one or many V2Ray/Xray JSON objects concatenated
        // together (the format used by the test data). Each object is delegated to
        // the existing audited V2RayJsonParser so stream/TLS fields are preserved.
        val jsonNodes = mutableListOf<ProxyConfig>()
        val jsonIssues = mutableListOf<ParseIssue>()
        candidates.forEach { text ->
            extractJsonObjects(text).forEachIndexed { index, json ->
                val parsed = runCatching { V2RayJsonParser.parse(json, subscriptionId) }.getOrNull()
                if (parsed != null && parsed.nodes.isNotEmpty()) {
                    jsonNodes += parsed.nodes
                } else if (parsed != null && parsed.issues.isNotEmpty()) {
                    jsonIssues += parsed.issues.map { issue ->
                        issue.copy(line = index)
                    }
                }
            }
        }
        if (jsonNodes.isNotEmpty()) {
            val normalized = jsonNodes
                .map { StandardNodeMapper.normalize(it, subscriptionId, SubscriptionFormat.V2RAY_JSON) }
                .distinctBy { "${it.protocol}:${it.server}:${it.port}:${it.name}" }
            return StandardNodeMapper.result(normalized, SubscriptionFormat.V2RAY_JSON)
                .copy(issues = jsonIssues)
        }

        val matches = candidates.flatMap { extractUris(it) }.distinct()
        if (matches.isEmpty()) {
            return SubscriptionParseResult(
                SubscriptionFormat.UNKNOWN,
                emptyList(),
                listOf(ParseIssue(-1, "", "embedded_uri_missing", "no recognized proxy configuration found in response", true))
            )
        }

        val nodes = mutableListOf<ProxyConfig>()
        val issues = mutableListOf<ParseIssue>()
        matches.forEachIndexed { index, rawUri ->
            val uri = cleanupUri(rawUri)
            val config = runCatching { UriParser.parse(uri) }.getOrNull()
            if (config != null) {
                config.subscriptionId = subscriptionId
                nodes += config
            } else {
                issues += ParseIssue(index, uri.take(48), "uri_invalid", "recognized proxy URI could not be parsed")
            }
        }
        val normalizedNodes = nodes.map {
            StandardNodeMapper.normalize(it, subscriptionId, SubscriptionFormat.URI)
        }
        return StandardNodeMapper.result(normalizedNodes, SubscriptionFormat.URI)
            .copy(issues = issues)
    }

    private fun addTextVariants(input: String, seen: MutableSet<String>, queue: ArrayDeque<String>) {
        val normalized = normalize(input)
        if (normalized.isBlank() || !seen.add(normalized)) return

        runCatching { URLDecoder.decode(normalized, StandardCharsets.UTF_8.name()) }
            .getOrNull()?.let { decoded ->
                val n = normalize(decoded)
                if (n != normalized && seen.add(n)) queue.addLast(n)
            }
        decodeBase64(normalized)?.let { decoded ->
            val n = normalize(decoded)
            if (n != normalized && seen.add(n)) queue.addLast(n)
        }
    }

    private fun extractUris(text: String): List<String> =
        uriPattern.findAll(normalize(text)).map { cleanupUri(it.value) }.filter { it.length > 12 }.toList()

    /** Extract balanced top-level JSON objects without requiring the whole response to be valid JSON. */
    private fun extractJsonObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = -1
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEachIndexed { i, ch ->
            if (quoted) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') quoted = false
                return@forEachIndexed
            }
            if (ch == '"') {
                quoted = true
                return@forEachIndexed
            }
            if (ch == '{') {
                if (depth == 0) start = i
                depth++
            } else if (ch == '}' && depth > 0) {
                depth--
                if (depth == 0 && start >= 0) {
                    result += text.substring(start, i + 1)
                    start = -1
                }
            }
        }
        return result
    }

    private fun cleanupUri(value: String): String = value
        .replace("\\/", "/")
        .replace("&amp;", "&", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003d", "=", ignoreCase = true)
        .trimEnd(',', ';', '.', ')', ']', '}', '`')

    private fun normalize(value: String): String = value
        .removePrefix("\uFEFF")
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u003A", ":", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003D", "=", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .trim()

    private fun decodeBase64(value: String): String? {
        val compact = value.replace(Regex("\\s+"), "")
        if (compact.length < 16 || !base64Alphabet.matches(compact)) return null
        val padded = compact.padEnd((compact.length + 3) / 4 * 4, '=')
        val decoded = listOf(
            runCatching { Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP) }.getOrNull(),
            runCatching { Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()
        )
        for (bytes in decoded) {
            if (bytes == null || bytes.isEmpty()) continue
            val utf8 = String(bytes, StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
            if (looksUseful(utf8)) return utf8
            val utf16 = runCatching { String(bytes, Charsets.UTF_16LE).trim() }.getOrNull()
            if (utf16 != null && looksUseful(utf16)) return utf16
        }
        return null
    }

    private fun looksUseful(text: String): Boolean =
        text.contains("://") || text.contains("vless", true) || text.contains("vmess", true) ||
            text.contains("trojan", true) || text.contains("hysteria", true) ||
            text.contains("tuic", true) || text.trimStart().startsWith("{") || text.trimStart().startsWith("[")
}
