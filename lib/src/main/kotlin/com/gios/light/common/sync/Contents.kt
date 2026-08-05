package com.gios.light.common.sync

/**
 * What to include, for the ordinary case where an app's files are portable as they sit.
 *
 * Portable means another install of the same app, on another phone, could open these bytes.
 * That rules out anything sealed with an AndroidKeyStore key, which by design cannot leave the
 * device — see [SyncableStore] for what to do instead. Backing up ciphertext whose key died with
 * the phone is worse than no backup, because it looks like one.
 */
data class Contents(
    /** SharedPreferences names, without `.xml`. */
    val prefs: List<String> = emptyList(),
    /** Database file names, as passed to Room. Journals travel with them. */
    val databases: List<String> = emptyList(),
    /** Paths under `filesDir`, files or directories. */
    val files: List<String> = emptyList(),
) {
    /** True when a store has nothing to contribute, so the writer can skip it entirely. */
    val isEmpty: Boolean get() = prefs.isEmpty() && databases.isEmpty() && files.isEmpty()

    operator fun plus(other: Contents) = Contents(
        prefs = prefs + other.prefs,
        databases = databases + other.databases,
        files = files + other.files,
    )
}
