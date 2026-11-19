package com.gios.light.common.report

import com.gios.light.common.BuildConfig

/**
 * What this app is, and where its reports go.
 *
 * **This is the one thing a consuming app must set up.** Reports are posted to the report relay
 * (`relay/` in this repo), which files the issue with a key that lives only on the server. No
 * credential is compiled into an app any more: a sideloaded APK is a zip anyone can open, and
 * the `REPORT_TOKEN` that used to ship in every BuildConfig could read the whole tracker.
 *
 * Call [install] once, early, from `MainActivity.onCreate` or an `Application`:
 *
 * ```kotlin
 * LightReport.install(
 *     context = this,
 *     appName = "LightTip",
 *     label = "tip",
 * )
 * ```
 *
 * [install] also arms the crash handler, so there is exactly one call to make and no way to end
 * up with reporting that works except for crashes.
 *
 * Not calling it at all is a supported state: [ReportOverlay] renders nothing, the sheet never
 * appears, and nothing is queued. An app that has not opted in pays nothing.
 */
object LightReport {

    /** How the app names itself in an issue title — what it is called on the phone. */
    @Volatile
    var appName: String = ""
        private set

    /** The triage label. One per app, lower case, matching the light-reports convention. */
    @Volatile
    var label: String = ""
        private set

    /** `owner/repo` the issues are filed into. */
    @Volatile
    var repo: String = "gi-os/light-reports"
        private set

    /**
     * Where reports are posted. Defaults to the relay this library was published with
     * (`REPORT_RELAY_URL` at publish time); blank means reports queue on the phone.
     */
    @Volatile
    var relay: String = BuildConfig.REPORT_RELAY_URL
        private set

    /** False until [install] has been called. Everything in the module no-ops until it is. */
    @Volatile
    var installed: Boolean = false
        private set

    /**
     * What the app knows that the phone does not: a page log, an engine version, the state of
     * a connection. Asked at the moment a bug report is composed, so it should be cheap and
     * must not throw; the answer rides in the body as a fenced block, capped at 8,000
     * characters. Null, or a blank answer, adds nothing. A browser attaches its navigation
     * log here; a player, the stream it was on.
     */
    @Volatile
    var details: (() -> String?)? = null

    /**
     * @param token ignored. Kept so call sites written before 1.11.0 still compile; nothing
     *   sends a key from the phone any more, so pass nothing.
     * @param relay override for the relay URL, for a debug build pointed at a local worker.
     *   Blank is fine — reports queue on disk and go out from a later build that has one.
     */
    @JvmStatic
    @JvmOverloads
    fun install(
        context: android.content.Context,
        appName: String,
        label: String,
        @Suppress("UNUSED_PARAMETER") token: String = "",
        repo: String = "gi-os/light-reports",
        relay: String = BuildConfig.REPORT_RELAY_URL,
    ) {
        this.appName = appName
        this.label = label
        this.repo = repo
        this.relay = relay
        this.installed = true
        CrashLog.install(context)
    }
}

/**
 * Where the app was when it went wrong.
 *
 * A single field rather than anything passed down: the crash handler runs on a dying thread that
 * has no view of the composition, and a report is worth far more with "standings" on it than
 * without. Written from the navigation, read from anywhere, so it is deliberately volatile.
 */
object ReportContext {
    @Volatile
    var screen: String = "home"
}
