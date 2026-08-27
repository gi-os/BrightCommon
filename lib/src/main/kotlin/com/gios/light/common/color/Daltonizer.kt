package com.gios.light.common.color

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * The fallback: this app writing the two secure settings itself.
 *
 * Only reached when BrightControl is not there to do it, and only when this app happens to hold
 * `WRITE_SECURE_SETTINGS` — a `signature|privileged` permission that no runtime prompt can grant,
 * so in practice it means somebody ran `pm grant` from a computer or from BrightControl's own ADB
 * screen, and a reinstall of the app takes it away again.
 *
 * That is the whole argument for the provider path existing. This one is kept because it is what
 * a phone without BrightControl still has, and because removing it from an app that already
 * worked would be a regression dressed up as a migration.
 */
internal object Daltonizer {

    private const val PREFS = "light-common-colour"
    private const val KEY_BASE_ENABLED = "base_enabled"
    private const val KEY_BASE_MODE = "base_mode"

    /** Not captured yet. Chosen because the enable flag is only ever 0 or 1. */
    private const val UNCAPTURED = -1

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** What the pair reads right now, or null if it cannot be read at all. */
    fun live(context: Context): Filter? = runCatching {
        Filter(
            enabled = read(context, ColourWire.SETTING_ENABLED, 1),
            mode = read(context, ColourWire.SETTING_MODE, Filter.MODE_MONO),
        )
    }.getOrNull()

    /**
     * What to go back to when nothing is holding colour.
     *
     * Read once and stored, so a phone the user had set to colour is not left monochrome by an
     * app that borrowed the setting. Stored on disk rather than in a field because the process
     * dies more often than the phone does, and a baseline recaptured after this app has already
     * changed the setting records this app's own opinion as the user's.
     */
    fun baseline(context: Context): Filter {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = sp.getInt(KEY_BASE_ENABLED, UNCAPTURED)
        if (stored != UNCAPTURED) {
            return Filter(stored, sp.getInt(KEY_BASE_MODE, Filter.MODE_MONO))
        }
        val now = live(context) ?: Filter.MONO
        sp.edit().putInt(KEY_BASE_ENABLED, now.enabled).putInt(KEY_BASE_MODE, now.mode).apply()
        return now
    }

    /**
     * State [target] and make it so. Returns true if anything was actually written.
     *
     * Idempotent on purpose: this is called from every re-assert, and the overwhelmingly common
     * outcome is that the panel is already right. Writing a value the provider already holds
     * would also be the two-writers flicker with only one app involved.
     */
    fun apply(context: Context, target: Filter): Boolean {
        if (!granted(context)) return false
        val now = live(context)
        if (now == target) return false
        var wrote = false
        for (setting in ColourPlan.order(target)) {
            val (key, value) = when (setting) {
                ColourPlan.Setting.Enabled -> ColourWire.SETTING_ENABLED to target.enabled
                ColourPlan.Setting.Mode -> ColourWire.SETTING_MODE to target.mode
            }
            if (now != null && current(now, setting) == value) continue
            // Swallowed rather than propagated. A missing grant is the normal state of this
            // permission and it must leave the feature inert, never take the app down with it.
            wrote = runCatching {
                Settings.Secure.putInt(context.contentResolver, key, value)
            }.isSuccess || wrote
        }
        return wrote
    }

    private fun current(filter: Filter, setting: ColourPlan.Setting): Int = when (setting) {
        ColourPlan.Setting.Enabled -> filter.enabled
        ColourPlan.Setting.Mode -> filter.mode
    }

    private fun read(context: Context, key: String, fallback: Int): Int =
        Settings.Secure.getInt(context.contentResolver, key, fallback)
}
