package com.andrew.proxyapp.data

import android.util.Base64
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Last-resort compatibility parser for providers that wrap proxy URIs in
 * JSON, HTML, escaped text, URL encoding, or one/more layers of Base64.
 * Native Pulse parsers remain the first choice; this parser only extracts
 * recognized proxy URI schemes and validates each URI with UriParser.
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

        // Providers occasionally base64-wrap an already encoded subscription.
        // Try several bounded decoding passes, but never treat arbitrary text as Base64.
        repeat(4) {
            val current = queue.removeFirstOrNull() ?: return@repeat
            addTextVariants(current, candidates, queue)
        }

        val matches = candidates
            .flatMap { extractUris(it) }
            .distinct()

        if (matches.isEmpty()) {
            return SubscriptionParseResult(
                SubscriptionFormat.UNKNOWN,
                emptyList(),
                listOf(ParseIssue(-1, "", "embedded_uri_missing", "no recognized proxy URI found in response", true))
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
        val result = StandardNodeMapper.result(normalizedNodes, SubscriptionFormat.URI)
        return result.copy(issues = issues + result.issues)
    }

    private fun addTextVariants(input: String, seen: MutableSet<String>, queue: ArrayDeque<String>) {
        val normalized = normalize(input)
        if (normalized.isBlank()) return
        if (seen.add(normalized)) {
            // URL-decoding is useful for JSON/query wrappers and harmless here.
            runCatching { URLDecoder.decode(normalized, StandardCharsets.UTF_8.name()) }
                .getOrNull()
                ?.let { decoded ->
                    val n = normalize(decoded)
                    if (n != normalized && seen.add(n)) queue.addLast(n)
                }

            decodeBase64(normalized)?.let { decoded ->
                val n = normalize(decoded)
                if (n != normalized && seen.add(n)) queue.addLast(n)
            }
        }
    }

    private fun extractUris(text: String): List<String> =
        uriPattern.findAll(normalize(text))
            .map { cleanupUri(it.value) }
            .filter { it.length > 12 }
            .toList()

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
        val byteArrays = listOf(
            runCatching { Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP) }.getOrNull(),
            runCatching { Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()
        )
        for (bytes in byteArrays) {
            if (bytes == null || bytes.isEmpty()) continue
            val utf8 = bytes.toString(StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
            if (looksLikeUsefulDecodedText(utf8)) return utf8
            // A few legacy generators emit UTF-16 text.
            val utf16 = runCatching { String(bytes, Charsets.UTF_16LE).trim() }.getOrNull()
            if (utf16 != null && looksLikeUsefulDecodedText(utf16)) return utf16
        }
        return null
    }

    private fun looksLikeUsefulDecodedText(text: String): Boolean {
        if (text.isBlank()) return false
        return text.contains("://") ||
            text.contains("vless", true) ||
            text.contains("vmess", true) ||
            text.contains("trojan", true) ||
            text.contains("hysteria", true) ||
            text.contains("tuic", true) ||
            text.trimStart().startsWith("{") ||
            text.trimStart().startsWith("[")
    }
}
