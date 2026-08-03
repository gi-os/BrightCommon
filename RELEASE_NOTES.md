## light-common v1.0.1 — The first version that actually published

Same code as the 1.0.0 tag. 1.0.0 is a broken publish and should not be used.

The first publish run uploaded the `.aar` to GitHub Packages and *then* failed, because the
job asked for `contents: read` while its last step creates a GitHub release — which is a write.
Fixing that and re-tagging did not work either: Maven artifacts are immutable, so re-publishing
the same version returns `409 Conflict`, which is the registry doing exactly what it should.

So this release carries the two fixes and a version number that is free:

- `contents: write` on the publish job.
- The publication resolves `components["release"]` inside a top-level `afterEvaluate`. That
  software component is created by the Android plugin during its own `afterEvaluate`, so
  resolving it from a block nested inside `register<MavenPublication>` can find nothing.

The 1.0.0 package version is a partial upload of unknown completeness. Delete it rather than
leave it resolvable.

### What is in it

Three packages. `report` is shake-to-report: the corner chip, the sheet with the note field, the
disk queue, the crash handler, the shake gesture and its tests. `hw` is `LightKeys` and the wheel,
as the superset of every variant in the wild. `theme` is the Akkurat font lookup and the three
greys.

Configuration is the one thing that changed shape from the vendored copies. `BuildConfig` does not
cross a library boundary, so `LightReport.install()` takes the app's name, label and token as
arguments. That is the better shape anyway: the token stays a build-time secret in the app that
owns it.
