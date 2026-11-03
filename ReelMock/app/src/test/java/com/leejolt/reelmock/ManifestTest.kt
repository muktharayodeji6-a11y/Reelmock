package com.leejolt.reelmock

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Static checks on AndroidManifest.xml (Gradle runs unit tests with cwd = app/). */
class ManifestTest {

    private val ns = "http://schemas.android.com/apk/res/android"

    private val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    private fun Element.a(name: String) = getAttributeNS(ns, name)
    private fun Element.kids(tag: String) =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private val filters: List<Element> = run {
        val act = doc.getElementsByTagName("activity").item(0) as Element
        act.kids("intent-filter")
    }

    private fun Element.actions() = kids("action").map { it.a("name") }.toSet()
    private fun Element.categories() = kids("category").map { it.a("name") }.toSet()
    private fun Element.data() = kids("data")
    private fun filterWith(action: String) = filters.single { action in it.actions() }

    @Test fun launcherAndSendFiltersPreserved() {
        assertTrue("android.intent.category.LAUNCHER" in filterWith("android.intent.action.MAIN").categories())
        val send = filterWith("android.intent.action.SEND")
        assertEquals(setOf("text/plain"), send.data().map { it.a("mimeType") }.toSet())
        assertTrue("android.intent.category.DEFAULT" in send.categories())
    }

    @Test fun viewFilterHasRequiredCategoriesSchemeHostsAndPaths() {
        val v = filterWith("android.intent.action.VIEW")
        assertEquals(setOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"), v.categories())
        val d = v.data()
        assertEquals(setOf("https"), d.map { it.a("scheme") }.filter { it.isNotEmpty() }.toSet())
        assertEquals(setOf("instagram.com", "www.instagram.com", "m.instagram.com"),
            d.map { it.a("host") }.filter { it.isNotEmpty() }.toSet())
        val prefixes = d.map { it.a("pathPrefix") }.filter { it.isNotEmpty() }.toSet()
        assertTrue("/reel/" in prefixes && "/reels/" in prefixes)
        assertTrue("/.*/reel/.*" in d.map { it.a("pathPattern") })
    }

    @Test fun noAutoVerifyAnywhere() {
        filters.forEach { assertFalse(it.hasAttributeNS(ns, "autoVerify")) }
    }

    @Test fun noPermissionsRequested() {
        assertEquals(0, doc.getElementsByTagName("uses-permission").length)
    }
}
