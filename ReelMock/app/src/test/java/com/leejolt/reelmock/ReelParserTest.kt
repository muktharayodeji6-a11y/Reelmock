package com.leejolt.reelmock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelParserTest {

    private fun valid(text: String?) = ReelParser.parse(text) as ReelParser.Result.Valid
    private fun assertInvalid(text: String?) =
        assertTrue("expected Invalid for: $text", ReelParser.parse(text) is ReelParser.Result.Invalid)

    @Test fun basicReelUrl() {
        val r = valid("https://www.instagram.com/reel/ABC123/")
        assertEquals("ABC123", r.reelId)
        assertEquals("https://www.instagram.com/reel/ABC123/", r.originalUrl)
    }

    @Test fun originalUrlPreservedWithQuery() {
        val url = "https://www.instagram.com/reel/C8xYz_9-AbC/?igsh=MWx0eXk"
        val r = valid(url)
        assertEquals("C8xYz_9-AbC", r.reelId)
        assertEquals(url, r.originalUrl)
        assertEquals("https://www.instagram.com/reel/C8xYz_9-AbC/", r.canonicalUrl)
    }

    @Test fun urlInsideCaption() {
        assertEquals("ABC123", valid("Check this out! https://www.instagram.com/reel/ABC123/?igsh=x. wow").reelId)
    }

    @Test fun userPrefixedAndReelsPaths() {
        assertEquals("ABC123", valid("https://www.instagram.com/someuser/reel/ABC123/").reelId)
        assertEquals("ABC123", valid("https://instagram.com/reels/ABC123/").reelId)
        assertEquals("ABC123", valid("https://m.instagram.com/reel/ABC123").reelId)
    }

    @Test fun rejectsInvalid() {
        assertInvalid(null)
        assertInvalid("")
        assertInvalid("hello world")
        assertInvalid("https://www.instagram.com/reel/")
        assertInvalid("https://www.instagram.com/someuser/")
        assertInvalid("https://www.instagram.com/p/ABC123/")
        assertInvalid("https://www.youtube.com/reel/ABC123/")
        assertInvalid("https://instagram.com.evil.com/reel/ABC123/")
        assertInvalid("https://evil.com/?u=instagram.com/reel/ABC123/")
        assertInvalid("https://instagram.com@evil.com/reel/ABC123/")
        assertInvalid("https://www.instagram.com/reel/AB/")
        assertInvalid("https://www.instagram.com/reels/audio/12345678/")
    }

    // ---- added for URL-handler support ----

    @Test fun wwwHostReel() {
        assertEquals("ABC123", valid("https://www.instagram.com/reel/ABC123/").reelId)
    }

    @Test fun mobileHostReel() {
        val r = valid("https://m.instagram.com/reel/ABC123/?igsh=zzz")
        assertEquals("ABC123", r.reelId)
        assertEquals("https://www.instagram.com/reel/ABC123/", r.canonicalUrl)
    }

    @Test fun queryAndFragmentPreservedInOriginal() {
        val url = "https://instagram.com/reel/C8xYz_9-AbC/?igsh=MWx0eXk&utm_source=ig_web_copy_link#t=5"
        val r = (ReelParser.parseUrl(url) as ReelParser.Result.Valid)
        assertEquals(url, r.originalUrl)
        assertEquals("C8xYz_9-AbC", r.reelId)
    }

    @Test fun rejectsLookalikeDomains() {
        listOf(
            "https://www.instagram.com.evil.com/reel/ABC123/",
            "https://instagram.com.evil.com/reel/ABC123/",
            "https://instagramm.com/reel/ABC123/",
            "https://fakeinstagram.com/reel/ABC123/",
            "https://evilinstagram.com/reel/ABC123/",
            "https://instagram.co/reel/ABC123/",
            "https://instagram-com.evil.com/reel/ABC123/",
            "https://evil.com#@instagram.com/reel/ABC123/",
            "https://instagram.com:443@evil.com/reel/ABC123/",
            "https://evil.com\\@instagram.com/reel/ABC123/"
        ).forEach { assertInvalid(it) }
    }

    @Test fun rejectsUnsupportedInstagramPaths() {
        listOf(
            "https://www.instagram.com/",
            "https://www.instagram.com/p/ABC123/",
            "https://www.instagram.com/tv/ABC123/",
            "https://www.instagram.com/stories/someuser/1234567890/",
            "https://www.instagram.com/explore/tags/cats/",
            "https://www.instagram.com/accounts/login/",
            "https://www.instagram.com/direct/inbox/",
            "https://www.instagram.com/reel",
            "https://www.instagram.com/reels/"
        ).forEach { assertInvalid(it) }
    }
}
