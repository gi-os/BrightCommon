package com.gios.light.common.sync

/**
 * The keys in the `meta` bundle, named once so the agent and the apps cannot disagree.
 *
 * The agent reads this over a `ContentResolver.call`, which is untyped both ways, so the only
 * thing keeping the two sides in step is that they compile against the same constants. They do
 * now; they did not when the provider was a file each app kept its own copy of, and the drift
 * showed up as an app silently reporting no label.
 *
 * Unknown keys are ignored and missing keys fall back, in both directions. An old app talking to
 * a new agent shows fewer columns on the fleet screen rather than nothing at all — which matters,
 * because the apps most worth seeing on that screen are the ones that have not been updated.
 */
object SyncMeta {
    const val METHOD_META = "meta"
    const val METHOD_IMPORT = "import"

    /** Short display name, e.g. `"Notebook"`. */
    const val LABEL = "label"

    /** The app's own `versionName`, e.g. `"1.4.2"`. */
    const val APP_VERSION = "appVersion"

    /** The light-common version the app was built against, or absent if it predates 1.2.0. */
    const val COMMON_VERSION = "commonVersion"

    /** Store names, in declaration order. */
    const val STORES = "stores"

    /** Rough payload size in bytes. Display only — the real size is known after zipping. */
    const val SIZE_HINT = "sizeHint"

    /** The app's triage label in light-reports, if it installed reporting. */
    const val REPORT_LABEL = "reportLabel"

    /** Whether the app expects to be killed after a restore. */
    const val RESTART_AFTER_RESTORE = "restartAfterRestore"

    /** Set on the reply to `import`. */
    const val OK = "ok"

    /** Parcel key for the descriptor handed to `import`. */
    const val FD = "fd"
}
