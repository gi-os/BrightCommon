package com.gios.light.common.color

/**
 * The names and numbers shared with BrightControl.
 *
 * Kept in one object because every one of them is half of a contract with a separately versioned
 * APK. A constant that only exists on one side of that boundary is a bind that succeeds and then
 * does nothing, which is the hardest kind of failure to see from a phone.
 */
object ColourWire {

    /** BrightControl. The only package this library will bind to. */
    const val PROVIDER_PACKAGE = "com.gios.lightcontrol"

    /** The exported service inside it. */
    const val PROVIDER_CLASS = "com.gios.lightcontrol.color.ColorService"

    const val ACTION_COLOR = "com.gios.lightcontrol.action.COLOR"

    /** The revision of [com.gios.lightcontrol.IColorProvider] this library speaks. */
    const val PROTOCOL = 1

    // ------------------------------------------------------------------ want(state)

    const val STATE_CLEAR = 0
    const val STATE_COLOUR = 1
    const val STATE_MONO = 2

    // ------------------------------------------------------------------ want() returns

    /** BrightControl is driving the screen. Do not write the settings from here. */
    const val SERVING = 1

    /**
     * BrightControl is installed and answered, but cannot act — it has no
     * `WRITE_SECURE_SETTINGS` grant, or the user has left its colour switch off. The request is
     * remembered there, so this becomes [SERVING] later without the app asking again; meanwhile
     * an app that holds the grant itself should use it.
     */
    const val INERT = 0

    /** Refused. Treated exactly like [INERT] — there is nothing an app can do differently. */
    const val REFUSED = -1

    /** No answer at all: not installed, or a bind that never connected. */
    const val ABSENT = -2

    /**
     * A bind is in flight and nothing has answered yet.
     *
     * Routed as "write nothing", which is the point of having a separate value for it. The
     * alternative is to assume BrightControl is absent, start writing the settings locally, and
     * then be told it was there all along — two writers for as long as a bind takes, which is
     * exactly the flicker this design removes. A beat of the screen not changing is a better
     * wrong answer than a beat of it changing twice.
     */
    const val PENDING = -3

    // ------------------------------------------------------------------ the settings themselves

    /**
     * Also declared here rather than only in the local writer, because the two sides have to
     * agree on which settings are being fought over even though only one of them writes.
     */
    const val SETTING_ENABLED = "accessibility_display_daltonizer_enabled"
    const val SETTING_MODE = "accessibility_display_daltonizer"

    // ------------------------------------------------------------------ timings

    /**
     * How long a bind may take before this library gives up and writes the settings itself.
     *
     * There has to be a deadline, and it has to be short. `bindService` says no immediately when
     * BrightControl is not installed, which is the common case on a phone that has not set it up
     * — but a package the user has force-quit accepts the bind and then never connects, and
     * waiting on that forever is a viewfinder that stays grey with nothing to explain it.
     *
     * Short enough to be a beat rather than a bug, long enough that a cold process start on this
     * hardware is not cut off doing exactly what was asked.
     */
    const val BIND_MS = 1_200L

    /**
     * How long to wait before trying a bind again after one failed.
     *
     * BrightControl being absent is not a transient condition, and a composable that retries per
     * frame would be a bind storm. Long enough that installing BrightControl mid-session is still
     * picked up without a relaunch.
     */
    const val RETRY_MS = 30_000L
}
