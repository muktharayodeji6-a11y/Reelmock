package com.leejolt.reelmock

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var store: ReelStore
    private lateinit var state: JSONObject
    private val session = IntakeSession { onHandled(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReelStore(this)
        state = store.buildState("idle")

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#0B0B10"))
        setContentView(webView)
        configureWebView()
        webView.loadUrl("file:///android_asset/index.html")

        // savedInstanceState != null => system recreated us (e.g. process death);
        // the old intent is re-delivered and must not be counted twice.
        session.onCreate(savedInstanceState != null, incoming(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        session.onNewIntent(incoming(intent))   // links arriving while the app is already open
    }

    /** Adapts an Intent (ACTION_SEND or ACTION_VIEW) to the pure-Kotlin intake model. */
    private fun incoming(intent: Intent?): LinkIntake.Incoming? = intent?.let {
        LinkIntake.Incoming(
            action = it.action,
            type = it.type,
            text = if (it.action == Intent.ACTION_SEND) it.getStringExtra(Intent.EXTRA_TEXT) else null,
            data = if (it.action == Intent.ACTION_VIEW) it.data?.toString() else null
        )
    }

    private fun onHandled(h: LinkIntake.Outcome.Handled) {
        state = when (val result = h.result) {
            is ReelParser.Result.Valid -> {
                store.save(result, h.source)
                store.buildState("valid", source = h.source)
            }
            is ReelParser.Result.Invalid ->
                store.buildState("invalid", result.reason, h.raw, h.source)
        }
        setIntent(Intent(Intent.ACTION_MAIN))   // mark this intent as consumed
        pushState()
    }

    private fun pushState() {
        // JSONObject.quote() yields a safely escaped JS string literal -> no injection.
        val literal = JSONObject.quote(state.toString())
        webView.evaluateJavascript(
            "window.ReelMock && window.ReelMock.render(JSON.parse($literal));", null
        )
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true       // needed for the local UI only
            allowFileAccess = false        // file:///android_asset is exempt from this
            allowContentAccess = false
            blockNetworkLoads = true       // the UI can never touch the network
            domStorageEnabled = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pushState()
            }

            // Never navigate away from the bundled page.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
        }
        webView.addJavascriptInterface(Bridge(), "Android")
    }

    /** Minimal JS -> native bridge. Only reads the stored URL, never takes a URL from JS. */
    inner class Bridge {
        @JavascriptInterface
        fun openOriginal() {
            runOnUiThread {
                val url = store.lastUrl() ?: return@runOnUiThread
                try {
                    // ReelMock now handles instagram.com Reel links itself, so a plain
                    // ACTION_VIEW could route straight back here (redirect loop). Use the
                    // system chooser with ReelMock excluded: Android still decides which
                    // installed app (Instagram, browser...) may open it, and the user picks.
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                    val chooser = Intent.createChooser(view, "Open original link with")
                        .putExtra(
                            Intent.EXTRA_EXCLUDE_COMPONENTS,
                            arrayOf(ComponentName(this@MainActivity, MainActivity::class.java))
                        )
                    startActivity(chooser)
                } catch (e: ActivityNotFoundException) {
                    // nothing can open it; ignore
                }
            }
        }

        @JavascriptInterface
        fun clearHistory() {
            runOnUiThread {
                store.clear()
                state = store.buildState("idle")
                pushState()
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
