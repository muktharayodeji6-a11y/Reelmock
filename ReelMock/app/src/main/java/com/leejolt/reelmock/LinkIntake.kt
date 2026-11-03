package com.leejolt.reelmock

/**
 * Pure Kotlin (no Android classes) intake logic for incoming intents, so Share and URL-handler
 * behaviour can be unit-tested on the JVM. MainActivity only adapts Intent -> [Incoming].
 */
object LinkIntake {

    const val SOURCE_SHARE = "share"   // Android Share sheet (ACTION_SEND)
    const val SOURCE_VIEW = "view"     // Android URL handling (ACTION_VIEW)

    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    private const val MAX_URL = 4000

    /** The only intent fields we look at. [text] = EXTRA_TEXT, [data] = intent.data as string. */
    data class Incoming(
        val action: String?,
        val type: String?,
        val text: String?,
        val data: String?
    )

    sealed class Outcome {
        /** Not something we handle (launcher start, unknown action, non-text share). */
        object Ignored : Outcome()

        data class Handled(
            val source: String,
            val result: ReelParser.Result,
            val raw: String?
        ) : Outcome()
    }

    fun process(incoming: Incoming?): Outcome {
        if (incoming == null) return Outcome.Ignored
        return when (incoming.action) {
            ACTION_SEND -> {
                if (incoming.type?.startsWith("text/") != true) Outcome.Ignored
                else Outcome.Handled(SOURCE_SHARE, ReelParser.parse(incoming.text), incoming.text)
            }
            ACTION_VIEW -> {
                val url = incoming.data
                val result = when {
                    url.isNullOrBlank() -> ReelParser.Result.Invalid("No link was received.")
                    url.length > MAX_URL -> ReelParser.Result.Invalid("Link is too long.")
                    !url.startsWith("https://", ignoreCase = true) ->
                        ReelParser.Result.Invalid("Only https links are accepted.")
                    else -> ReelParser.parseUrl(url)   // original URL (with query) is preserved
                }
                Outcome.Handled(SOURCE_VIEW, result, url)
            }
            else -> Outcome.Ignored
        }
    }
}

/**
 * Lifecycle rules for incoming intents, kept free of Android classes for testing.
 * - cold start: the launching intent is processed once
 * - recreation (rotation/process death, savedInstanceState != null): the re-delivered intent is NOT processed again
 * - onNewIntent: every new link is processed
 */
class IntakeSession(private val onHandled: (LinkIntake.Outcome.Handled) -> Unit) {

    fun onCreate(recreated: Boolean, incoming: LinkIntake.Incoming?) {
        if (!recreated) deliver(incoming)
    }

    fun onNewIntent(incoming: LinkIntake.Incoming?) = deliver(incoming)

    private fun deliver(incoming: LinkIntake.Incoming?) {
        val outcome = LinkIntake.process(incoming)
        if (outcome is LinkIntake.Outcome.Handled) onHandled(outcome)
    }
}
