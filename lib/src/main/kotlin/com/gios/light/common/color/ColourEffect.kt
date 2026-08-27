package com.gios.light.common.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Colour for as long as this is composed.
 *
 * The signature is the one Roll and BrightChat already call, deliberately: migrating them is an
 * import change and nothing else. What moved is underneath — the app no longer writes the secure
 * settings, or holds the permission to, it asks BrightControl.
 *
 * ```kotlin
 * @Composable
 * fun CameraScreen(active: Boolean) {
 *     ColourEffect(enabled = active)
 *     …
 * }
 * ```
 *
 * [enabled] rather than a conditional call, because a composable that is sometimes not called is
 * a composable whose `onDispose` sometimes does not run. Passing false releases; leaving the
 * composition releases; the process dying releases, because the hold lives on a binder.
 *
 * **Every screen that draws a photograph needs its own.** Roll shipped with the viewfinder and the
 * viewer holding colour and the roll grid not, and the grid is where the photographs actually are.
 * Overlapping holds are free: they are counted, so a pager keeping two pages composed across a
 * swipe hands over with nothing on screen changing.
 */
@Composable
fun ColourEffect(enabled: Boolean = true, want: ColourWant = ColourWant.Colour) {
    val context = LocalContext.current
    DisposableEffect(enabled, want) {
        if (enabled) BrightColour.hold(context, want)
        onDispose { if (enabled) BrightColour.release(context) }
    }
}

/**
 * Colour for the whole app while it is in front.
 *
 * Placed once, in `setContent`, for an app that has one opinion rather than one per screen — a
 * notebook, a music app, a chat app whose whole point is the picture in the thread.
 *
 * **Prefer this whenever it is true.** An app that asks per screen and puts the setting back
 * between screens gives BrightControl something to disagree with, and two writers alternating on
 * the same two settings is a panel that flickers on every scroll — which is what BrightMusic did
 * while it held colour per album cover. One statement, held while the app is up, cannot do that.
 *
 * An app that wants this and nothing else does not need the call at all: one line of manifest
 * metadata says the same thing, and says it on a phone where the app has never been launched.
 * See the README. This is the version for an app that wants to turn it on and off.
 */
@Composable
fun ColourAppEffect(enabled: Boolean = true, want: ColourWant = ColourWant.Colour) =
    ColourEffect(enabled = enabled, want = want)
