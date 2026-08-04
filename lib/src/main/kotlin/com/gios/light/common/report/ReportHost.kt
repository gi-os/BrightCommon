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
import androidx.compose.ui.platform.LocalContext
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
fun ReportOverlay() {
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

    // The window is what gets photographed. Walked out of the Context the same way as the
    // lifecycle owner above, and null is a supported answer — a report with no picture is still
    // a report, and this module is installed in at least one app with no Activity above it.
    val window = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.window
    }

    // Two states, not one. `pending` is the offer in the corner; `sheetOpen` is the answer to it.
    // Collapsing them is what made the first version interrupt: a shake the phone misread put a
    // sheet over whatever you were reading, and the only way out was to dismiss it.
    var pending by remember { mutableStateOf<ReportReason?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Bitmap?>(null) }
    val failure by Trouble.latest.collectAsState()

    // Read once. The file is deleted as soon as it has been offered, so that a crash is asked
    // about on the next launch and not on every launch after it.
    val crash = remember { CrashLog.read(context) }

    // Take the picture, then ask. The chip is about to sit on top of whatever looked wrong, and
    // by the time the sheet is up the screen being reported on is gone — so the capture happens
    // at the moment of the shake and waits on disk-free memory until it is either sent or
    // dropped. PixelCopy is asynchronous, hence the offer being raised from its callback rather
    // than beside it.
    val raise: (ReportReason) -> Unit = remember(window) {
        { reason ->
            if (window == null) {
                pending = reason
            } else {
                Screenshot.capture(window) { bitmap ->
                    shot?.recycle()
                    shot = bitmap
                    pending = reason
                }
            }
        }
    }

    // Dropping an offer drops its picture with it. Bitmaps are large enough that holding one
    // for a report nobody sent is worth avoiding.
    val drop: () -> Unit = {
        shot?.recycle()
        shot = null
    }

    val detector = remember {
        ShakeDetector(context) {
            if (pending == null && !sheetOpen) raise(ReportReason.Shaken)
        }
    }

    // Anything left in the queue from a run that could not reach the network — or from a build
    // that had no token at all — goes out now.
    LaunchedEffect(Unit) {
        runCatching { Reports.flush(context) }
        // No screenshot for this one: the app died and relaunched, so the screen now is the
        // one you are looking at rather than the one that broke.
        if (!crash.isNullOrBlank()) pending = ReportReason.Crashed
    }

    // A failure the app noticed itself only raises the offer if nothing else already has:
    // being asked about a stale feed on top of a crash report is how people turn this off.
    LaunchedEffect(failure) {
        if (failure != null && pending == null && !sheetOpen) raise(ReportReason.Failed)
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
            onOpen = { sheetOpen = true },
            onExpire = {
                // Silence is "not now", not "no". The crash log stays on disk so the next
                // launch can offer it again; only the in-memory failure is dropped, and
                // Trouble will not re-raise the same one for an hour.
                pending = null
                drop()
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
            onDismiss = {
                sheetOpen = false
                pending = null
                drop()
                Trouble.clear()
                if (why == ReportReason.Crashed) CrashLog.clear(context)
            },
            onSend = { symptom, note ->
                val picture = shot
                shot = null
                val why0 = if (why == ReportReason.Failed) failure else null
                // Closed before the send, not after: submit() queues to disk first, so there is
                // nothing here that can fail in a way the sheet would need to report.
                sheetOpen = false
                pending = null
                Trouble.clear()
                CrashLog.clear(context)
                scope.launch {
                    runCatching {
                        // PNG compression on the main thread is a visible hitch on the LPIII,
                        // and this is the one moment the UI is animating a sheet away.
                        val encoded = picture?.let {
                            withContext(Dispatchers.Default) { Screenshot.encode(it) }
                        }
                        picture?.recycle()
                        Reports.submit(
                            context,
                            Reports.compose(
                                context = context,
                                symptom = symptom,
                                note = note,
                                screen = ReportContext.screen,
                                crash = crash,
                                failure = why0,
                                shot = encoded,
                            ),
                        )
                    }
                }
            },
        )
    }
}
