package com.andrew.proxyapp.data

/**
 * Last-resort subscription compatibility parser.
 * Some subscription providers wrap proxy URIs inside JSON, HTML, or another
 * response format. We only extract recognized proxy URI schemes and let the
 * existing, audited UriParser validate each URI.
 */
object EmbeddedUriParser {
    private val uriPattern = Regex(
        "(?i)(?:vless|vmess|trojan|ss|socks|socks5|http|hysteria2|hysteria|tuic)://[^\\s\\\"'<>\\]}]+"
    )

    fun parse(content: String, subscriptionId: String): SubscriptionParseResult {
        val normalized = content.replace("\\/", "/")
        val matches = uriPattern.findAll(normalized).map { it.value.trimEnd(',', ';', '.') }.toList()
        if (matches.isEmpty()) {
            return SubscriptionParseResult(
                SubscriptionFormat.UNKNOWN,
                emptyList(),
                listOf(ParseIssue(-1, "", "embedded_uri_missing", "no recognized proxy URI found in response", true))
            )
        }

        val nodes = mutableListOf<ProxyConfig>()
        val issues = mutableListOf<ParseIssue>()
        matches.forEachIndexed { index, uri ->
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
}
