package com.gios.light.common.report

import android.content.Context

/**
 * A phone number to call back on, remembered between reports.
 *
 * Reports arrive as a one-way statement: a chip row, a sentence, a build table, and no way to ask
 * the one question that would settle it. On this fleet the follow-up is a text message — the
 * people running these apps are, by definition, reachable on a phone — so the field asks for a
 * number rather than pointing at a chat server nobody has joined.
 *
 * **Optional, and it stays optional.** An empty number sends a report exactly as before and the
 * body says so, because a reporter who does not want to be contacted is still a reporter worth
 * having.
 *
 * Remembered because typing on this keypad is expensive and because the second report is the one
 * that gets abandoned at the number field. Stored in the app's own private prefs, never sent
 * anywhere but into the issue body of the private tracker, and [forget] clears it.
 */
object Contact {

    private const val PREFS = "light_report"
    private const val KEY_PHONE = "reply_phone"

    /** Long enough for any real number with a country code and separators. */
    private const val MAX = 24

    fun phone(context: Context): String =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, "")
        }.getOrNull().orEmpty()

    /** Called on send, not on every keystroke — a half-typed number is not worth keeping. */
    fun remember(context: Context, phone: String) {
        val clean = tidy(phone)
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .apply { if (clean.isEmpty()) remove(KEY_PHONE) else putString(KEY_PHONE, clean) }
                .apply()
        }
    }

    fun forget(context: Context) = remember(context, "")

    /**
     * Trimmed and capped, not validated. Pure, so it can be tested on the JVM.
     *
     * Nothing here tries to decide whether a number is real. A reporter who writes "917 turn 4 to
     * 8" has told you something; a field that rejects it has thrown the report away to enforce a
     * format only a machine cares about, and nothing in this pipeline dials it automatically.
     */
    internal fun tidy(phone: String): String = phone.trim().replace(Regex("\\s+"), " ").take(MAX)
}
