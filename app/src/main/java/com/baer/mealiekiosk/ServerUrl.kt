package com.baer.mealiekiosk

import java.net.URI

/** The Mealie server address the owner types in once. Plain JVM code, so it is unit-tested. */
object ServerUrl {
    /** Returns the http(s) URL to load, adding https:// when no scheme was typed, or null. */
    fun parse(input: String): String? {
        val text = input.trim()
        val url = if ("://" in text) text else "https://$text"
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) return null
        return url
    }

    /** True when [url] is on the server's host; everything else is blocked in the WebView. */
    fun sameHost(url: String, base: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        return host.equals(runCatching { URI(base).host }.getOrNull(), ignoreCase = true)
    }
}
