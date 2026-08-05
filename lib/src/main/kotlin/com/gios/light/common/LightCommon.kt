package com.gios.light.common

/**
 * Which version of this library an app is carrying.
 *
 * Read from `BuildConfig` rather than written down twice. The version already exists in
 * `lib/build.gradle.kts`, the publish workflow already checks the tag against it, and a second
 * hand-maintained copy here would be a third thing to forget — the kind that goes wrong quietly,
 * because a stale constant still compiles and still looks right in a settings readout.
 *
 * It is public because LightSync's fleet screen reports it per app: an app a library version
 * behind is the likeliest explanation for a bug that was already fixed elsewhere, and finding
 * that out from the phone beats going repo by repo.
 */
object LightCommon {

    /** e.g. `"1.2.0"`. */
    @JvmStatic
    val VERSION: String = BuildConfig.LIGHT_COMMON_VERSION
}
