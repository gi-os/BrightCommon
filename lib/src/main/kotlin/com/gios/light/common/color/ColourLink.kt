package com.gios.light.common.color

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.gios.lightcontrol.IColorProvider
import java.util.concurrent.Executors

/**
 * The connection to BrightControl.
 *
 * ### Why a bound service, and not a broadcast
 *
 * A hold has to end when the app holding it stops existing, and a broadcast has no way to say
 * that. An app that asks for colour and is then killed — swiped away, low memory, a crash — would
 * leave the phone repainted with nothing left to take it back, and the user's only route out would
 * be to find the setting themselves, which on this phone there is no screen for.
 *
 * A binder answers that for free. One connection is one hold, and the connection dying is the
 * release, whether the app unbound tidily or was never given the chance. This is the same lesson
 * as the one that made the local writer state-driven rather than edge-driven, one layer down: do
 * not depend on a message that may never be sent.
 *
 * ### Why the reply matters more than the call
 *
 * BrightControl can be installed and still unable to act — no grant, or the user has left its
 * colour switch off. So the answer to `want` is routing information, not an acknowledgement, and
 * until it arrives this library deliberately writes nothing: guessing wrong for a few hundred
 * milliseconds means both apps writing the same two settings, which is the flicker the whole
 * design exists to remove.
 */
internal object ColourLink {

    /** The last answer from BrightControl, or one of the sentinels in [ColourWire]. */
    @Volatile
    var reply: Int = ColourWire.PENDING
        private set

    /** Called on the main thread whenever [reply] changes and the route may have moved. */
    var onReplyChanged: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /**
     * Binder calls go here rather than on the caller's thread. `want` is a fast call into a
     * process that is already running, but "fast" is not a guarantee, and the caller is a Compose
     * effect on the main thread of a camera app.
     */
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "light-common-colour").apply { isDaemon = true }
    }

    private var provider: IColorProvider? = null
    private var binding = false
    private var lastFailureAt = 0L

    /** The state to send as soon as there is something to send it to. */
    private var pending = ColourWire.STATE_CLEAR

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            provider = IColorProvider.Stub.asInterface(binder)
            binding = false
            // Re-state on every connect, not only on the first. A rebind after BrightControl was
            // updated arrives with the server holding no record of this app at all, and the app
            // is very likely still sitting on the screen that asked.
            send(pending)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            provider = null
            // Pending, not absent: the framework will rebind on its own, and calling this absent
            // would hand the screen to the local writer for the length of a restart.
            set(ColourWire.PENDING)
        }

        override fun onBindingDied(name: ComponentName?) {
            provider = null
            set(ColourWire.PENDING)
        }

        override fun onNullBinding(name: ComponentName?) {
            // The service exists and refused to give out a binder. Nothing to wait for.
            binding = false
            set(ColourWire.ABSENT)
        }
    }

    /**
     * Ask for [state], connecting first if there is nothing connected.
     *
     * Returns immediately. The answer lands in [reply] and [onReplyChanged] fires, because the
     * only alternative is a blocking bind on whichever thread a composable happens to run on.
     */
    fun request(context: Context, state: Int) {
        pending = state
        val live = provider
        if (live != null) {
            send(state)
            return
        }
        connect(context)
    }

    private fun connect(context: Context) {
        if (binding) return
        // A phone with no BrightControl is the ordinary case, not an error case, and retrying it
        // from a recomposition would be a bind attempt per frame. The window is long enough that
        // installing BrightControl and coming back to the app still picks it up.
        if (reply == ColourWire.ABSENT &&
            SystemClock.elapsedRealtime() - lastFailureAt < ColourWire.RETRY_MS
        ) {
            return
        }
        binding = true
        set(ColourWire.PENDING)
        val intent = Intent(ColourWire.ACTION_COLOR).apply {
            // Explicit as well as actioned. An implicit intent cannot start a service on any
            // supported release, and naming the component is also what stops another package
            // registering the same action and being handed these requests.
            setClassName(ColourWire.PROVIDER_PACKAGE, ColourWire.PROVIDER_CLASS)
        }
        val started = runCatching {
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!started) {
            // The common answer, and it arrives synchronously: BrightControl is not installed. So
            // an app on a phone without it never waits — it finds out inside the same frame and
            // falls straight through to its own writer.
            binding = false
            runCatching { context.applicationContext.unbindService(connection) }
            fail()
            return
        }
        // A package the user has force-quit accepts the bind and then never connects. Without a
        // deadline that is a screen which stays grey with nothing on it to explain why.
        main.postDelayed({
            if (binding && provider == null) {
                binding = false
                fail()
            }
        }, ColourWire.BIND_MS)
    }

    private fun fail() {
        lastFailureAt = SystemClock.elapsedRealtime()
        set(ColourWire.ABSENT)
    }

    private fun send(state: Int) {
        val live = provider ?: return
        worker.execute {
            val answer = runCatching { live.want(state) }.getOrDefault(ColourWire.ABSENT)
            main.post { set(answer) }
        }
    }

    private fun set(value: Int) {
        if (reply == value) return
        reply = value
        main.post { onReplyChanged?.invoke() }
    }
}
