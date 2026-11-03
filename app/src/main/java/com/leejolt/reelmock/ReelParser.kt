package com.leejolt.reelmock

import java.net.URI

/** Pure Kotlin (no Android classes) so it can be unit-tested on the JVM. */
object ReelParser {

    sealed class Result {
        data class Valid(
            val originalUrl: String,   // exactly as received (incl. ?igsh=... etc.)
            val reelId: String,
            val canonicalUrl: String
        ) : Result()

        data class Invalid(val reason: String) : Result()
    }

    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val ID_REGEX = Regex("^[A-Za-z0-9_-]{5,40}$")
    private val ALLOWED_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    private val REEL_SEGMENTS = setOf("reel", "reels")
    private const val MAX_INPUT = 4000

    /** Parses shared text, which may contain a caption plus the link. */
    fun parse(text: String?): Result {
        if (text.isNullOrBlank()) return Result.Invalid("Nothing was shared.")
        if (text.length > MAX_INPUT) return Result.Invalid("Shared text is too long.")

        val candidates = URL_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ';', '!', '?') }
            .toList()
        if (candidates.isEmpty()) return Result.Invalid("No link found in the shared text.")

        var firstReason: String? = null
        for (candidate in candidates) {
            when (val r = parseUrl(candidate)) {
                is Result.Valid -> return r
                is Result.Invalid -> if (firstReason == null) firstReason = r.reason
            }
        }
        return Result.Invalid(firstReason ?: "Not a valid Reel link.")
    }

    fun parseUrl(url: String): Result {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return Result.Invalid("Malformed URL.")
        }
        val host = uri.host?.lowercase() ?: return Result.Invalid("Malformed URL.")
        if (host !in ALLOWED_HOSTS) return Result.Invalid("Not an instagram.com link ($host).")

        val segments = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
        val id = when {
            segments.size >= 2 && segments[0].lowercase() in REEL_SEGMENTS -> segments[1]
            segments.size >= 3 && segments[1].lowercase() == "reel" -> segments[2]   // /<user>/reel/<id>/
            else -> return Result.Invalid("Link is not a Reel (expected /reel/<id>/).")
        }
        if (id.lowercase() == "audio" || !ID_REGEX.matches(id)) {
            return Result.Invalid("Reel ID looks invalid.")
        }
        return Result.Valid(url, id, "https://www.instagram.com/reel/$id/")
    }
}
