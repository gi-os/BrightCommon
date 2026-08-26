package com.gios.light.common.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole reporting feature, as one line in `MainActivity`.
 *
 * A sibling of the app's content rather than a wrapper around it. The sheet is a
 * `ModalBottomSheet`, which renders in its own window, so it does not need to contain anything
 * to sit on top of everything — and a call that does not wrap means installing this in a new
 * app is inserting one line inside the theme, with no re-indentation and no brace to move.
 * Everything it owns — the sensor, the crash file, the queue — is tied to the composition and
 * to the lifecycle, so an app that stops calling it stops paying for it.
 *
 * Nothing here opens the sheet on its own. Three things can raise the *offer* — a chip in the
 * corner — and only a tap on that chip opens the sheet. They are deliberately different questions:
 *
 *  - **A shake.** You noticed something. The gesture is in [ShakeGesture] and is tuned to be
 *    hard to trigger in a pocket.
 *  - **A crash last run.** The app died and left a stack trace behind. Asked once, on the
 *    launch after, because that is the only moment the trace is still worth anything.
 *  - **A failure the app noticed itself.** See [Trouble] — the quiet ones, which are the
 *    reports that otherwise never get filed.
 *
 * The sensor is registered on RESUME and dropped on PAUSE. That is what keeps a 50Hz
 * accelerometer stream from being a battery question: it only runs while you are looking at
 * the app, and shaking a phone that is showing something else has nothing to do with this one.
 */
@Composable
fun ReportOverlay(
    /** Where the offer sits. See [ReportChip] for why this is a parameter at all. */
    corner: Alignment = Alignment.BottomStart,
    inset: Dp = 16.dp,
    bottomInset: Dp = inset,
) {
    // An app that never called LightReport.install gets nothing: no sensor, no crash offer,
    // no queue. Opting in is a positive act, and forgetting it should be quiet rather than a
    // half-working reporter that files issues with a blank app name.
    if (!LightReport.installed) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Walked out of the Context rather than read from `LocalLifecycleOwner`, on purpose. That
    // composition local moved from `androidx.compose.ui.platform` to `androidx.lifecycle.compose`
    // and the old one is gone in current Compose — so reading it would make this file compile in
    // some of the apps it is installed in and not others, which is the one thing this package is
    // supposed to avoid. Every Activity is a LifecycleOwner, and unwrapping ContextWrappers to
    // find it works on every version.
    val lifecycleOwner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>()
            .firstOrNull()
    }

    // Two states, not one. `pending` is the offer in the corner; `sheetOpen` is the answer to it.
    // Collapsing them is what made the first version interrupt: a shake the phone misread put a
    // sheet over whatever you were reading, and the only way out was to dismiss it.
    var pending by remember { mutableStateOf<ReportReason?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    // The picture of the screen as it was when the offer went up — see [Screenshot]. Held as a
    // Bitmap and encoded only on send: encoding is a PNG compress and a base64 pass, and most
    // offers are ignored rather than tapped.
    var shot by remember { mutableStateOf<Bitmap?>(null) }
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
    }
    // Taken at the moment something raises the offer, not when the sheet asks: by then the chip
    // and the sheet are what is on screen, and the thing that looked wrong is behind them.
    val grab: (ReportReason) -> Unit = { why ->
        val window = activity?.window
        if (window == null) {
            pending = why
        } else {
            Screenshot.capture(window) { bitmap ->
                shot = bitmap
                pending = why
            }
        }
    }
    val failure by Trouble.latest.collectAsState()
    val asked by Feedback.asked.collectAsState()

    // Read once. The file is deleted as soon as it has been offered, so that a crash is asked
    // about on the next launch and not on every launch after it.
    val crash = remember { CrashLog.read(context) }

    val detector = remember {
        ShakeDetector(context) {
            if (pending == null && !sheetOpen) grab(ReportReason.Shaken)
        }
    }

    // Anything left in the queue from a run that could not reach the network — or from a build
    // that had no token at all — goes out now.
    LaunchedEffect(Unit) {
        runCatching { Reports.flush(context) }
        // No screenshot for a crash: this is the launch after the one that died, so the window
        // holds whatever has just started rather than what went wrong.
        if (!crash.isNullOrBlank()) pending = ReportReason.Crashed
    }

    // An app asking on the user's behalf — a "Send feedback" row in settings. Routed through the
    // same chip as everything else rather than opening the sheet, so there is exactly one
    // confirmation step in this feature and one place it appears. See [Feedback].
    LaunchedEffect(asked) {
        if (asked > 0 && pending == null && !sheetOpen) grab(ReportReason.Shaken)
    }

    // A failure the app noticed itself only raises the offer if nothing else already has:
    // being asked about a stale feed on top of a crash report is how people turn this off.
    LaunchedEffect(failure) {
        if (failure != null && pending == null && !sheetOpen) grab(ReportReason.Failed)
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner?.lifecycle
        if (lifecycle == null) {
            // No Activity above us — nothing to hang the sensor off. The sheet still works if
            // something else raises it; only the shake is unavailable.
            onDispose { detector.stop() }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> detector.start()
                    Lifecycle.Event.ON_PAUSE -> detector.stop()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                detector.stop()
            }
        }
    }

    // The offer. Hidden while the sheet is up — it has already been answered.
    pending?.takeIf { !sheetOpen }?.let { why ->
        // The shake that raised this must not raise a second one behind it.
        LaunchedEffect(why) { detector.forget() }
        ReportChip(
            reason = why,
            corner = corner,
            inset = inset,
            bottomInset = bottomInset,
            onOpen = { sheetOpen = true },
            onExpire = {
                // Silence is "not now", not "no". The crash log stays on disk so the next
                // launch can offer it again; only the in-memory failure is dropped, and
                // Trouble will not re-raise the same one for an hour.
                pending = null
                // A picture nobody asked for is a few megabytes of ARGB_8888 held for nothing.
                shot = null
                if (why == ReportReason.Failed) Trouble.clear()
                detector.forget()
            },
        )
    }

    pending?.takeIf { sheetOpen }?.let { why ->
        ReportSheet(
            reason = why,
            failure = if (why == ReportReason.Failed) failure?.what else null,
            appName = LightReport.appName,
            // Read once per open rather than held in state up here: the sheet owns the field,
            // and this is only the value it starts with.
            knownPhone = remember { Contact.phone(context) },
            hasScreenshot = shot != null,
            onDismiss = {
                sheetOpen = false
                pending = null
                shot = null
                Trouble.clear()
                if (why == ReportReason.Crashed) CrashLog.clear(context)
            },
            onSend = { draft ->
                // Snapshotted before the state below is cleared: the coroutine runs after this
                // lambda returns, and by then `shot` and `failure` are null.
                val picture = shot?.takeIf { draft.includeShot }
                val cause = if (why == ReportReason.Failed) failure else null
                // Kept for next time, on send and not on every keystroke. A half-typed number
                // is not worth remembering, and the second report is the one that gets
                // abandoned at a field you have already filled in once.
                Contact.remember(context, draft.phone)
                // Closed before the send, not after: submit() queues to disk first, so there is
                // nothing here that can fail in a way the sheet would need to report.
                sheetOpen = false
                pending = null
                shot = null
                Trouble.clear()
                CrashLog.clear(context)
                scope.launch {
                    runCatching {
                        // Off the main thread, all of it. Encoding the picture is a PNG compress
                        // of a full-screen bitmap plus a base64 pass — tens to hundreds of
                        // milliseconds on this hardware — and doing it inline would stall the
                        // frame that closes the sheet. The vendored copies this replaces always
                        // did it on IO; the library has to as well, or "SEND" hitches.
                        val report = withContext(Dispatchers.IO) {
                            Reports.compose(
                                context = context,
                                draft = draft,
                                screen = ReportContext.screen,
                                // An idea is not about a crash even when there was one.
                                // compose() drops it on that branch.
                                crash = crash,
                                failure = cause,
                                shot = picture?.let { Screenshot.encode(it) },
                            )
                        }
                        Reports.submit(context, report)
                    }
                }
            },
        )
    }
}
