package com.gios.light.common.report

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ask for the offer from inside the app — a settings row, a menu, a long-press.
 *
 * A shake is the only way in, and a shake is a gesture some people will never trust and some
 * phones will never read the same way twice. An app that wants a "Send feedback" row can call
 * [ask] and get **the same chip in the same corner**, which is the whole point: there is one
 * confirmation step in this feature, one place it appears, and nothing anywhere opens the sheet
 * without a tap on it. A settings row that opened the sheet directly would be a second, quieter
 * path with different behaviour, and the two would drift.
 *
 * A counter rather than a boolean, because a `StateFlow` swallows a repeat of the same value and
 * asking twice has to raise the offer twice.
 */
object Feedback {

    private val _asked = MutableStateFlow(0)

    /** Bumped by [ask]. Zero means nobody has asked yet, which is not the same as a dismissal. */
    val asked: StateFlow<Int> = _asked

    /** Raise the offer. Safe from any thread; does nothing if the offer is already up. */
    fun ask() {
        _asked.value += 1
    }
}
