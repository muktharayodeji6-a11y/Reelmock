package com.leejolt.reelmock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class LinkIntakeTest {

    private val send = LinkIntake.ACTION_SEND
    private val view = LinkIntake.ACTION_VIEW

    private fun share(text: String?, type: String? = "text/plain") =
        LinkIntake.Incoming(send, type, text, null)

    private fun tap(url: String?) = LinkIntake.Incoming(view, null, null, url)

    private fun handled(i: LinkIntake.Incoming?) = LinkIntake.process(i) as LinkIntake.Outcome.Handled
    private fun valid(h: LinkIntake.Outcome.Handled) = h.result as ReelParser.Result.Valid

    // ---- ACTION_SEND compatibility (8) ----

    @Test fun sendStillWorks() {
        val h = handled(share("Look at this https://www.instagram.com/reel/XYZ789/?igsh=a"))
        assertEquals(LinkIntake.SOURCE_SHARE, h.source)
        assertEquals("XYZ789", valid(h).reelId)
        assertEquals("https://www.instagram.com/reel/XYZ789/?igsh=a", valid(h).originalUrl)
    }

    @Test fun sendInvalidIsReportedNotIgnored() {
        val h = handled(share("just some text"))
        assertTrue(h.result is ReelParser.Result.Invalid)
        assertEquals(LinkIntake.SOURCE_SHARE, h.source)
    }

    @Test fun sendNonTextIsIgnored() {
        assertSame(LinkIntake.Outcome.Ignored, LinkIntake.process(share("https://www.instagram.com/reel/ABC123/", "image/png")))
    }

    // ---- ACTION_VIEW parsing ----

    @Test fun viewValidKeepsQueryAndSource() {
        val url = "https://www.instagram.com/reel/C8xYz_9-AbC/?igsh=MWx0eXk"
        val h = handled(tap(url))
        assertEquals(LinkIntake.SOURCE_VIEW, h.source)
        assertEquals("C8xYz_9-AbC", valid(h).reelId)
        assertEquals(url, valid(h).originalUrl)
    }

    @Test fun viewAllSupportedHostsAndPaths() {
        listOf(
            "https://instagram.com/reel/ABC123/",
            "https://www.instagram.com/reel/ABC123/",
            "https://m.instagram.com/reel/ABC123/",
            "https://www.instagram.com/reels/ABC123/",
            "https://www.instagram.com/someuser/reel/ABC123/"
        ).forEach { assertEquals(it, "ABC123", valid(handled(tap(it))).reelId) }
    }

    @Test fun viewInvalidShowsErrorNotIgnored() {
        listOf(
            "https://www.instagram.com/p/ABC123/",                       // unsupported path
            "https://www.instagram.com.evil.com/reel/ABC123/",           // lookalike
            "https://instagram.com@evil.com/reel/ABC123/",               // userinfo trick
            "http://www.instagram.com/reel/ABC123/",                     // not https
            "ftp://www.instagram.com/reel/ABC123/",
            "",
            null
        ).forEach {
            val h = handled(tap(it))
            assertTrue("expected Invalid for $it", h.result is ReelParser.Result.Invalid)
            assertEquals(LinkIntake.SOURCE_VIEW, h.source)
        }
    }

    @Test fun viewIgnoresExtraText() {
        // VIEW must use intent.data only, never EXTRA_TEXT.
        val i = LinkIntake.Incoming(view, null, "https://www.instagram.com/reel/AAAAA1/", "https://www.instagram.com/reel/BBBBB2/")
        assertEquals("BBBBB2", valid(handled(i)).reelId)
    }

    @Test fun otherActionsAndNullAreIgnored() {
        assertSame(LinkIntake.Outcome.Ignored, LinkIntake.process(null))
        assertSame(LinkIntake.Outcome.Ignored, LinkIntake.process(LinkIntake.Incoming("android.intent.action.MAIN", null, null, null)))
        assertSame(LinkIntake.Outcome.Ignored, LinkIntake.process(LinkIntake.Incoming(null, null, null, null)))
    }

    // ---- lifecycle: cold start (9), already running (10), recreation ----

    private class Recorder {
        val seen = mutableListOf<LinkIntake.Outcome.Handled>()
        val session = IntakeSession { seen += it }
    }

    @Test fun viewColdStartProcessedOnce() {
        val r = Recorder()
        r.session.onCreate(recreated = false, incoming = tap("https://www.instagram.com/reel/ABC123/?igsh=1"))
        assertEquals(1, r.seen.size)
        assertEquals("ABC123", valid(r.seen[0]).reelId)
        assertEquals(LinkIntake.SOURCE_VIEW, r.seen[0].source)
    }

    @Test fun sendColdStartProcessedOnce() {
        val r = Recorder()
        r.session.onCreate(false, share("https://www.instagram.com/reel/ABC123/"))
        assertEquals(1, r.seen.size)
        assertEquals(LinkIntake.SOURCE_SHARE, r.seen[0].source)
    }

    @Test fun launcherColdStartDoesNothing() {
        val r = Recorder()
        r.session.onCreate(false, LinkIntake.Incoming("android.intent.action.MAIN", null, null, null))
        assertEquals(0, r.seen.size)
    }

    @Test fun viewWhileRunningIsProcessed() {
        val r = Recorder()
        r.session.onCreate(false, LinkIntake.Incoming("android.intent.action.MAIN", null, null, null))
        r.session.onNewIntent(tap("https://www.instagram.com/reel/AAAAA1/"))
        r.session.onNewIntent(tap("https://m.instagram.com/reel/BBBBB2/"))
        r.session.onNewIntent(share("https://www.instagram.com/reel/CCCCC3/"))
        assertEquals(listOf("AAAAA1", "BBBBB2", "CCCCC3"), r.seen.map { valid(it).reelId })
        assertEquals(listOf("view", "view", "share"), r.seen.map { it.source })
    }

    @Test fun recreationDoesNotReprocessRedeliveredIntent() {
        val r = Recorder()
        r.session.onCreate(recreated = true, incoming = tap("https://www.instagram.com/reel/ABC123/"))
        assertEquals(0, r.seen.size)
    }

    @Test fun invalidViewWhileRunningSurfacesError() {
        val r = Recorder()
        r.session.onNewIntent(tap("https://www.instagram.com/p/ABC123/"))
        assertEquals(1, r.seen.size)
        assertTrue(r.seen[0].result is ReelParser.Result.Invalid)
    }
}
