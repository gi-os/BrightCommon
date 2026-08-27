package com.gios.light.common.color

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper

/**
 * Colour on a phone with one colour switch, asked for rather than taken.
 *
 * ## What this replaces
 *
 * LightOS pins the whole phone to monochrome through the accessibility daltonizer. An app that
 * wants to show a photograph has to move that setting, and moving it needs
 * `WRITE_SECURE_SETTINGS` — `signature|privileged`, no runtime prompt, no LightOS screen. So every
 * app that wanted colour carried the permission in its manifest and a copy of the same writer, and
 * every one of them needed `pm grant` from a computer, again after every reinstall.
 *
 * Five apps holding a privileged permission to fight over two settings is the wrong shape twice
 * over. It is five grants to lose, and it is five writers: two apps with different opinions about
 * the same setting take turns, and the panel flickers on every scroll.
 *
 * BrightControl already holds the grant, already knows which app is in front, and already writes
 * these two settings as state rather than as edges. So an app asks it, and it writes. One grant on
 * the phone, one writer, and an app that loses nothing to a reinstall.
 *
 * ## Using it
 *
 * ```kotlin
 * ColourEffect()                       // colour while this screen is composed
 * ColourEffect(enabled = active)       // …and only while it is the visible page of a pager
 * ColourAppEffect()                    // colour for the whole app while it is in front
 * ```
 *
 * Nothing else. No permission in the manifest, no grant, no writer, and no dependency on
 * BrightControl being installed: with no provider to answer, an app that happens to hold the grant
 * still writes the settings itself, and an app that does not is simply inert.
 *
 * An app whose whole self wants colour can say so in its manifest instead and write no code at
 * all — see the README. This object is for the apps that change their mind screen by screen.
 *
 * ## The one rule
 *
 * Call from the main thread. Every entry point here is called from a composable and the state is
 * plain fields on purpose; a lock would be protecting against a caller that does not exist.
 */
object BrightColour {

    private var app: Application? = null

    /** How many screens are asking. Refcounted so overlapping holds across a swipe are free. */
    private var holders = 0

    /** What the most recent hold asked for. */
    private var want = ColourWant.Colour

    /** How many activities of this app are started. Only the local writer consults it. */
    private var visibleActivities = 0

    /**
     * True until the first activity callback of any kind arrives.
     *
     * The callbacks are registered on the first [hold], and the first hold comes from a composable
     * inside an activity that is already started — so the `onActivityStarted` for the activity
     * asking is the one call that can never be observed. Counting from zero would make the first
     * apply restore the baseline over the top of the request that triggered it, which reads as
     * colour never engaging at all. Counting from one instead would double-count the same activity
     * and leave the app looking visible after it had been stopped. So it is a separate question
     * with a separate answer, and it is settled by the first callback that arrives either way.
     */
    private var assumeVisible = true

    private var wired = false

    /**
     * Ask for colour. Every [hold] must be matched by a [release], which is why the Compose
     * effects are the intended way in — `onDispose` cannot be forgotten.
     */
    fun hold(context: Context, want: ColourWant = ColourWant.Colour) {
        wire(context)
        holders++
        this.want = want
        apply()
    }

    fun release(context: Context) {
        wire(context)
        if (holders > 0) holders--
        apply()
    }

    /** Who is driving the two settings for this app right now. For a diagnostics row. */
    fun source(context: Context): ColourSource {
        wire(context)
        return ColourPlan.route(ColourLink.reply, Daltonizer.granted(context))
    }

    /**
     * One line for a settings screen, because every question this feature raises is invisible
     * from the outside. "Colour does not work" has four causes that look identical on the panel —
     * BrightControl absent, BrightControl present but ungranted, this app holding a stale grant,
     * or nothing asking — and they are answered differently.
     */
    fun summary(context: Context): String {
        val route = source(context)
        val live = Daltonizer.live(context)
        val panel = when {
            live == null -> "unreadable"
            live.isColour -> "colour"
            else -> "mono"
        }
        val who = when (route) {
            ColourSource.Provider -> "BrightControl"
            ColourSource.Local -> "this app"
            ColourSource.None -> when (ColourLink.reply) {
                ColourWire.PENDING -> "asking"
                ColourWire.INERT, ColourWire.REFUSED -> "BrightControl, not granted"
                else -> "nobody"
            }
        }
        return "panel $panel · driven by $who · $holders asking"
    }

    /**
     * State the request and the screen, from whatever the fields say now.
     *
     * The same shape as BrightControl's own `applyFor`: it says what should be true rather than
     * reacting to what changed. A call that arrives out of order, twice, or not at all leaves the
     * next one able to correct it.
     */
    private fun apply() {
        val context = app ?: return
        val state = when {
            holders <= 0 -> ColourWire.STATE_CLEAR
            want == ColourWant.Mono -> ColourWire.STATE_MONO
            else -> ColourWire.STATE_COLOUR
        }
        // Told on every apply, including while it is answering. The request is one int and the
        // provider stores it idempotently; the alternative is tracking what was last sent, which
        // is the transition model that this whole file exists to avoid.
        ColourLink.request(context, state)
        when (ColourPlan.route(ColourLink.reply, Daltonizer.granted(context))) {
            // BrightControl owns the two settings. Writing them from here as well is the flicker.
            ColourSource.Provider -> Unit
            ColourSource.Local -> Daltonizer.apply(
                context,
                ColourPlan.desired(
                    holders = holders,
                    want = want,
                    appVisible = visibleActivities > 0 || assumeVisible,
                    baseline = Daltonizer.baseline(context),
                ),
            )
            ColourSource.None -> Unit
        }
    }

    /**
     * Attach to the process, once.
     *
     * Visibility is counted from the application's own activity callbacks rather than from a
     * composable's lifecycle owner. Two reasons: it is the honest definition of "this app is on
     * screen" when an app has more than one activity, and it keeps this file off
     * `LocalLifecycleOwner`, which moved package between Compose releases and would pin every
     * consumer to a BOM range to fix a question the platform already answers.
     */
    private fun wire(context: Context) {
        if (wired) return
        val application = context.applicationContext as? Application ?: return
        app = application
        wired = true
        ColourLink.onReplyChanged = {
            if (Looper.myLooper() == Looper.getMainLooper()) apply()
        }
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    assumeVisible = false
                    visibleActivities++
                    apply()
                }

                override fun onActivityStopped(activity: Activity) {
                    assumeVisible = false
                    if (visibleActivities > 0) visibleActivities--
                    apply()
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
