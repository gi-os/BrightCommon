package com.gios.light.common.report

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Which phone a report came from, without knowing anything about who is holding it.
 *
 * The problem this solves is triage, not analytics. Half the issues in `light-reports` are filed
 * by Gio testing a build he pushed ten minutes ago, and half are filed by somebody who installed
 * it from BrightMarket — and those two get read completely differently. His own reports are
 * reproducible on the bench and usually already half-diagnosed by the shake that raised them; a
 * stranger's is the only evidence that will ever exist, and it has to be answered rather than
 * re-run. Until now nothing in the issue said which kind it was, and `Build.MODEL` cannot say:
 * every one of them is a Light Phone III.
 *
 * **The identity is a hash, deliberately.** `ANDROID_ID` is scoped per signing key since Android
 * 8, and every Bright\* app is signed with the same keystore — so one phone produces the *same*
 * id across the whole fleet, which is what makes an allowlist of eight hex characters worth
 * keeping. It is hashed with a fixed prefix so the value in an issue cannot be turned back into
 * the device identifier it came from, and truncated to four bytes because its only job is to be
 * comparable, not unique across the internet.
 *
 * **Owner-ness is a list of hashes, not a build flag.** A `debug` flavour would be the obvious
 * mechanism and it is the wrong one: every APK on Gio's phone is the same release build CI cut
 * for everybody else, so the flag would be false on the one device it is meant to catch. A
 * debuggable build still counts as his, because nobody else has one.
 *
 * The chicken-and-egg is real and is fine: [KNOWN_OWNERS] starts with nothing in it, so the
 * first report from his phone reads "unregistered" and carries the id that fixes it. Adding one
 * line here is cheaper than shipping a registration flow to a fleet of one.
 */
object Device {

    /**
     * Install ids belonging to Gio. Add the eight hex characters an issue reports.
     *
     * Not a secret and not sensitive — a hash of a per-signing-key identifier, useful only for
     * comparing one report against another.
     */
    private val KNOWN_OWNERS = setOf<String>(
        // "3f9a21c8",  ← the LPIII, once it has filed one report and said so
    )

    private const val PREFS = "light_report"
    private const val KEY_SEED = "install_seed"

    /**
     * Some early devices shipped this same `ANDROID_ID` in ROM, so it identifies a production
     * run rather than a phone. Treated as absent.
     */
    private const val KNOWN_BAD_ANDROID_ID = "9774d56d682e549c"

    /** Eight hex characters, stable for as long as the app is installed. */
    fun installId(context: Context): String = fingerprint(seed(context))

    /** True when this is one of Gio's own phones — see the class comment for why it is a list. */
    fun isOwner(context: Context): Boolean =
        installId(context) in KNOWN_OWNERS || debuggable(context)

    /**
     * One line for a diagnostics screen, so the id can be read off the phone rather than waited
     * for. Deliberately says "unregistered" and not "not yours": an id missing from the list is
     * a list that has not been updated, which is a different thing from a stranger's phone.
     */
    fun summary(context: Context): String =
        "Install ${installId(context)} · ${if (isOwner(context)) "yours" else "unregistered"}"

    /** How the reporter is named in an issue: the id, plus whether it is a known one. */
    internal fun reporter(context: Context): String =
        "${if (isOwner(context)) "you" else "unregistered"} (${installId(context)})"

    /**
     * The label that splits the tracker in two. `mine` is a bench report; `field` is somebody
     * else's phone and the only account of the bug that will ever exist.
     */
    internal fun label(context: Context): String = if (isOwner(context)) "mine" else "field"

    /**
     * Pure so it can be tested on the JVM. The prefix is what stops the output being a lookup
     * table of `ANDROID_ID`s, and it must never change: every id already written into an issue
     * is derived from it.
     */
    internal fun fingerprint(seed: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("light-common/install/$seed".toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }

    /**
     * `ANDROID_ID` where there is one, a remembered random otherwise.
     *
     * The fallback is persisted rather than generated per call, and it is only reached on a
     * device that returns nothing — a report whose id changed every launch would be worse than
     * a report with no id at all, because it looks like several phones.
     */
    private fun seed(context: Context): String {
        val android = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        if (!android.isNullOrBlank() && android != KNOWN_BAD_ANDROID_ID) return android

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_SEED, null)?.takeIf { it.isNotBlank() }?.let { return it }
        return UUID.randomUUID().toString().also {
            runCatching { prefs.edit().putString(KEY_SEED, it).apply() }
        }
    }

    private fun debuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
