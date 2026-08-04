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

`ReportOverlay` photographs the app's own window when an offer is raised — before the chip covers
anything — and the picture rides in the issue body as base64. No permission is involved: this is
the app looking at itself, not screen capture. A crash offer has no picture, because the screen
after a relaunch is not the screen that died.

The sheet shows the picture as a thumbnail with an ATTACHED / LEFT OUT chip, so a report that would
have carried something private is one tap from not carrying it.

**3b. The sensor readout, if you want it.**

```kotlin
DisposableEffect(Unit) {
    ShakeMonitor.watch()
    onDispose { ShakeMonitor.unwatch() }
}
val reading by ShakeMonitor.reading.collectAsState()
// reading.magnitudeG, reading.peakG, reading.turns, reading.turnsNeeded, reading.fires
```

For a settings screen. "I shook it and nothing happened" is unanswerable from outside the phone;
"it peaked at 1.2g and needs 1.38g" is something you can act on. Nothing is published while
`watchers` is zero, so an app that never calls `watch()` pays nothing for this.

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
