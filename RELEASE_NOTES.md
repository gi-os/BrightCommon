## light-common 1.2.1 — the baseline profile is actually in the AAR this time

A packaging fix, and nothing else. 1.2.0 announced a baseline profile and shipped an AAR without
one: `unzip -l` on the published artifact lists `R.txt`, `AndroidManifest.xml`, `classes.jar`,
`proguard.txt` and the metadata, and no profile.

The file was at `src/main/baselineProfiles/light-common.txt`. That directory is for app modules
and for profiles produced by the baseline profile plugin; a library ships one as
`src/main/baseline-prof.txt`, which is where it is now. AGP does not warn about a profile in the
wrong place — it simply packages nothing, so every consumer that added `profileinstaller` in
good faith had nothing to install.

Worth stating as a rule: check the artifact, not the source tree. The failure mode for a build
input that is picked up by convention is silence.

No code change. Consumers on 1.2.0 get this by bumping the version and nothing else.
