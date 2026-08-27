package com.gios.light.common.color

/**
 * What the screen should look like, decided as arithmetic so it can be checked on the JVM.
 *
 * Every number in here is one that cannot be read off a phone. "The panel stayed grey" and
 * "the panel flickered on every scroll" are the same sentence from the outside, and both were
 * shipped more than once — so the decisions are pulled out of the code that talks to Android
 * and left here, where a test can ask them directly.
 */

/** What an app is asking for while it holds colour. */
enum class ColourWant {
    /** No filter: the panel shows what the app drew. */
    Colour,

    /** Monochrome, deliberately — a reading screen on a phone the user has set to colour. */
    Mono,
}

/** Who is actually driving the daltonizer for this app. */
enum class ColourSource {
    /** BrightControl is. This app must not write the settings itself. */
    Provider,

    /** Nobody else can, and this app holds the grant, so it writes them. */
    Local,

    /** Neither. The request is remembered and nothing is written. */
    None,
}

/**
 * The daltonizer, which is a pair of secure settings and not a boolean.
 *
 * `accessibility_display_daltonizer_enabled` is 0 or 1, and
 * `accessibility_display_daltonizer` is the filter: **0 is monochromacy**, 11 to 13 are the
 * colour-blindness corrections, and only **-1 means no filter at all**.
 *
 * That -1 is the whole reason this type exists rather than a Boolean. Writing
 * `enabled = 0, mode = 0` reads back as "monochrome, currently switched off", and anything that
 * reconstitutes the pair afterwards — LightOS does, on its own shell coming forward — makes the
 * screen grey again from a state that every readout said was correct.
 */
data class Filter(val enabled: Int, val mode: Int) {

    val isColour: Boolean get() = enabled == 0 || mode == MODE_NONE

    companion object {
        /** No filter. Not the same as a filter that is switched off. */
        const val MODE_NONE = -1

        /** The filter LightOS pins this phone to. */
        const val MODE_MONO = 0

        val COLOUR = Filter(enabled = 0, mode = MODE_NONE)
        val MONO = Filter(enabled = 1, mode = MODE_MONO)
    }
}

internal object ColourPlan {

    /**
     * The filter that should be on the panel right now.
     *
     * **Stated, never stepped.** The predecessor of this function wrote on transitions — lift on
     * the way in, restore on the way out — and one missed edge stranded BrightMusic's panel in
     * the wrong mode until the process died, because coming back took the holder count from 3 to
     * 4 rather than from 0 to 1 and so re-lifted nothing. A function that says what the answer is
     * cannot be stranded: a call that was missed is corrected by the next one.
     *
     * [appVisible] belongs to the local path and only to it. When BrightControl is serving, it
     * gates every request on the requesting app being in front already, so this function is never
     * asked: a hold that outlives a trip to the launcher is inert over there rather than wrong,
     * and dropping it on the way out would be churn with an IPC round trip on the way back. With
     * no such gate, an app writing the settings itself must put them back when it leaves, or the
     * whole phone keeps whatever the app wanted.
     */
    fun desired(
        holders: Int,
        want: ColourWant,
        appVisible: Boolean,
        baseline: Filter,
    ): Filter {
        if (holders <= 0 || !appVisible) return baseline
        return when (want) {
            ColourWant.Colour -> Filter.COLOUR
            ColourWant.Mono -> Filter.MONO
        }
    }

    /**
     * Which of the two settings to write first.
     *
     * Both orders work and one of them flashes. Turning a filter **on** with the enable flag
     * first switches on whatever filter is still stored, which is the wrong screen for as long as
     * the second write takes; turning one **off** by clearing the mode first does the same thing
     * in reverse. So: going to a filter, mode first. Coming off one, enabled first.
     */
    fun order(target: Filter): List<Setting> =
        if (target.enabled == 1) listOf(Setting.Mode, Setting.Enabled)
        else listOf(Setting.Enabled, Setting.Mode)

    /**
     * Who writes, given what BrightControl said and whether this app could write anyway.
     *
     * The one answer that matters is the first: **[ColourSource.Provider] means this app writes
     * nothing.** Two writers with opinions about the same two settings is not a redundancy, it is
     * a flicker — BrightMusic held colour per album cover and restored grey between them, and
     * BrightControl answered every restore by re-asserting colour, so the panel took turns on
     * every scroll.
     */
    fun route(reply: Int, canWriteLocally: Boolean): ColourSource = when {
        reply == ColourWire.SERVING -> ColourSource.Provider
        // Waiting is a decision, not an absence of one. See [ColourWire.PENDING].
        reply == ColourWire.PENDING -> ColourSource.None
        canWriteLocally -> ColourSource.Local
        else -> ColourSource.None
    }

    internal enum class Setting { Enabled, Mode }
}
