## light-common v1.0.0 — The first version

**The plumbing every Light app was carrying its own copy of, in one place.**

Three packages. `report` is shake-to-report: the corner chip, the sheet with the note field, the
disk queue, the crash handler and the shake gesture. `hw` is `LightKeys` and the wheel — the
superset of every variant that was in the wild, so an app that had the small one loses nothing
and an app that had the big one gains nothing it did not already have. `theme` is the Akkurat
font lookup and the three greys.

This replaces roughly 42,000 redundant lines. `LightKeys.kt` was byte-identical in fifteen repos.
`Wheel.kt` had ten variants, which sounds like divergence and was not — the differences were
purely additive, and the shared core was identical everywhere it appeared.

The one thing that changed shape in the move is configuration. `BuildConfig` does not cross a
library boundary, so `LightReport.install()` takes the app's name, label and token as arguments
rather than reading them. That is the better shape anyway: the token stays a build-time secret in
the app that owns it, and nothing here needs to know how it got there.
