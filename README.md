# light-common

The plumbing every Light Phone III app was keeping its own copy of.

Published to GitHub Packages as `com.gios:light-common`.

---

## Why this exists

Before this, `LightKeys.kt` was byte-identical in fifteen repos, `Wheel.kt` existed in ten
variants across twenty, and shake-to-report lived only in the three apps somebody had bothered to
paste it into. A fix to the wheel meant twenty edits, so it got made once and the other nineteen
drifted.

It is also a correctness argument, not only a tidiness one. Wiring the same feature into ten repos
by pattern-matching is how you get eight of them subtly wrong and find out from CI — which is
exactly what happened porting the report module by hand.

---

## Using it

**1. Add the repository and the dependency.**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/gi-os/light-common")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts
implementation("com.gios:light-common:1.2.0")
```

GitHub Packages requires authentication **even for public packages** — there is no anonymous
read. Locally that means `gpr.user` / `gpr.key` in `local.properties` (a PAT with `read:packages`).
In CI, `GITHUB_ACTOR` / `GITHUB_TOKEN` are already there.

**2. Install reporting, once, early.**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    LightReport.install(
        context = this,
        appName = "LightTip",          // what the app is called on the phone, not the repo name
        label = "tip",                 // the triage label in light-reports
        token = BuildConfig.REPORT_TOKEN,
    )
    ...
}
```

`install` also arms the crash handler, so there is one call and no way to end up with reporting
that works except for crashes. Skip it entirely and the whole feature is inert — `ReportOverlay`
renders nothing and nothing is queued.

**3. Put the overlay inside your theme.**

```kotlin
setContent {
    LightTipTheme {
        App()
        ReportOverlay()   // a sibling, not a wrapper
    }
}
```

It draws in its own window, so it does not care what layout it is called from.

**4. The wheel, if you use it.**

```kotlin
CompositionLocalProvider(LocalWheelBus provides wheel) { ... }

WheelScroll(listState)                    // any ScrollableState
WheelScroll(listState, reverse = true)    // wheel up moves up the list
WheelScroll(webView)                      // WebViews scroll differently
WheelSteps(onStep = { ... })              // discrete steps rather than pixels
WheelTurns(armed = true) { notches -> }   // raw notches: zoom, exposure, a filter list
WheelTurns(pressed = true) { notches -> } // turns made with the wheel held in
WheelInDialog()                           // inside a Dialog or ModalBottomSheet — see below
WheelGate(active = !modalOpen) { ... }    // kill the wheel for a whole subtree
```

`armed` is the stray-notch guard: two notches to start, on by default for scrollers and off by
default for `WheelTurns`. Leave it off where every notch must count and a wrong one is harmless
(stepping a filter list); turn it on where a stray notch changes something (zoom, exposure).

`WheelSteps` banks notches and rate-limits them, because the sensor fires faster than anyone can
read a moving highlight. For a control that really does want one step per notch, pass
`notchesPerStep = 1, minIntervalMs = 0`.

A Compose `Dialog` — and a `ModalBottomSheet`, which is one underneath — is a window of its own.
Keys go to whichever window has focus, so while a sheet is up the activity never sees them and
the wheel goes dead. `WheelInDialog()` inside the sheet fixes that.

---

## Backing up, via LightSync

`LightSyncBackup` is the whole of an app's contribution to LightSync. It used to be a file each
app kept its own copy of; it is a class here now, and the archive layout is unchanged, so blobs
already on BasilNet restore into a migrated app.

```kotlin
class Backup : LightSyncBackup() {
    override fun stores() = listOf(
        FileStore("main", Contents(prefs = listOf("lighttip"))),
    )
}
```

```xml
<provider
    android:name=".backup.Backup"
    android:authorities="${applicationId}.lightsync.backup"
    android:exported="true" />
```

The authority suffix `.lightsync.backup` *is* the registration — the agent finds apps by asking
the package manager, so adding the seventeenth app never touches LightSync.

**Two kinds of store, and the choice is not stylistic.**

- `FileStore` — the files on disk are the backup. Another install of the same app on another
  phone can open them. Almost everything is this.
- `LogicalStore` — they are not, so the store produces something portable. Anything sealed with
  an AndroidKeyStore key **must** be this: that key cannot leave the device and will not survive
  a factory reset, so copying the ciphertext yields a backup that restores cleanly and decrypts
  to nothing.

An app with several subsystems lists several stores. Under one flat file list it had to pick the
worst answer for all of them.

`name` is an identifier, not a display string — it is written into the archive and read back on
restore, so renaming one orphans every blob already stored.

Overriding `contents()` alone still works and is treated as a single `FileStore("main")`.

---

## R8, minification and the baseline profile

`consumer-rules.pro` covers everything in here, so a consumer can go straight to:

```properties
# gradle.properties
android.enableR8.fullMode=true
```

```kotlin
// app/build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

What an app still owes its own rules for is anything *it* reaches by name: ML Kit's model
loading (Camera, OCR), any Room entity read reflectively, and any class parsed out of JSON by
field name. Full mode's rule of thumb is that a `-keep` on a class no longer keeps its members,
so a rule that worked before may now need `{ *; }` or an explicit `<init>()`.

The AAR carries a baseline profile for the wheel, the crash handler and the type lookup, merged
into the app's own at build time. Nothing to wire up.

---

## Releasing

```bash
# 1. bump `libraryVersion` in lib/build.gradle.kts, one minor step
# 2. rewrite RELEASE_NOTES.md for this release only
git tag v1.1.0 && git push origin v1.1.0
```

The tag must match `libraryVersion`; publish.yml checks rather than trusts, because Maven
artifacts are immutable and a wrong number can only be superseded, never fixed.

Publishing then triggers `bump-consumers.yml`, which opens a version-bump PR in every consumer.
Pull requests, not pushes — a push to a consumer's default branch releases onto the phone, and a
library bump is exactly the change that should compile first.

That workflow needs `CONSUMER_BUMP_TOKEN`: a PAT with contents and pull-requests write on the
consumer repos. `GITHUB_TOKEN` will not do, as its write access stops at this repository.

---

## What is deliberately *not* here

- **`Theme.kt` itself.** Each app's is 33–50 lines of its own Material setup, and four of them
  (Camera, Notebook, OCR, Fastread) are much larger and genuinely app-specific. Only the font
  lookup and the greys were the same everywhere, so only those moved.
- **`Common.kt` composables** — `Chip`, `Rule`, `MenuRow` and friends. They share names across
  apps and not much else; unifying them is a design decision, not a refactor.
- **The `light-sdk` design system.** That is upstream and MIT, and the apps here are plain APKs
  precisely because the SDK sandbox forbids camera and BLE. This is not a replacement for it.
