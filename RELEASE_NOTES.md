## light-common 1.3.0 — whose phone it was, and a chip that takes an idea

Three additions to `report/`, all reached through the same chip in the corner. Nothing new opens
a sheet on its own.

**A report now says which phone filed it.** Every device in this fleet is a Light Phone III, so
`Build.MODEL` could never tell Gio's bench report from a stranger's — and the two are read
completely differently. His are reproducible on the desk and usually half-diagnosed by the shake
that raised them; a stranger's report is the only account of that bug that will ever exist.
`Device` derives an eight-hex install id from `ANDROID_ID`, hashed with a fixed salt so the value
in an issue cannot be turned back into the identifier it came from, and labels the issue `mine` or
`field`.

The id is **stable across the whole fleet**, which is what makes an allowlist worth keeping:
`ANDROID_ID` is scoped per signing key since Android 8, and every Bright\* app is signed with the
same keystore, so one phone produces one id in all of them. A debug flavour would have been the
obvious mechanism and is the wrong one — every APK on Gio's phone is the same release build CI cut
for everybody else, so the flag would be false on the one device it is meant to catch.

`Device.KNOWN_OWNERS` starts empty, on purpose. The first report from his phone reads
`unregistered (3f9a21c8)` and carries the id that fixes it; adding one line there is cheaper than
shipping a registration flow to a fleet of one. A debuggable build already counts as his, because
nobody else has one. `Device.summary(context)` is a line for a diagnostics screen so the id can be
read off the phone rather than waited for.

**BUG or IDEA, at the top of the sheet.** A feature request used to have no route off the phone at
all — no browser, no email, a private tracker nobody outside can see — so the moment somebody knew
what they wished the app did instead was the moment it was lost. The shake now leads to a sheet
that takes either, and the corner chip says `SEND FEEDBACK?` rather than `SEND ERROR?`, because a
chip that only offers to report an error is a chip nobody taps to ask for a feature. An idea is a
different document, not a bug report with empty sections: no stack trace, no heap figure, no free
space, labelled `enhancement` plus one of `idea-new` / `idea-change` / `idea-missing` /
`idea-other`, and titled `AppName v1.2 — idea: …`.

The toggle only appears for a shake. A crash and a failure the app caught itself are bugs and
cannot be anything else, and a sheet that invites you to file a stack trace as a feature request
files miscategorised issues.

**An optional phone number.** A report has always been a one-way statement — a chip row, a
sentence, a build table — with no way to ask the one question that would settle it. The field asks
for a number rather than pointing at a chat server nobody has joined: everybody running these apps
is, by definition, reachable on a phone. Empty is fine and the body says `not given` rather than
leaving a blank cell, which reads as a field that failed to collect. It is remembered between
reports (on send, not per keystroke) because the second report is the one abandoned at a field you
already filled in once, and `Contact.forget(context)` clears it. Nothing validates it — a reporter
who writes "917 turn 4 to 8" has told you something, and a field that rejects it has thrown the
report away to enforce a format only a machine cares about.

**`Feedback.ask()`** raises the offer from inside the app, for a "Send feedback" row in settings.
It raises *the chip*, not the sheet, so there is one confirmation step in this feature and one
place it appears; a settings row that opened the sheet directly would be a second, quieter path
with different behaviour, and the two would drift.

### Breaking

- `ReportSheet`'s `onSend` now hands up a single `Draft` instead of `(Symptom, String)`.
- `Reports.compose` takes a `Draft`. The old body-building path is `Reports.composeBug`, with the
  same parameters in the same order plus a defaulted `phone` at the end.

Apps using `ReportOverlay()` — which is all of them — need nothing but the version bump. New
labels (`mine`, `field`, `self-reported`, `idea-*`) exist in `gi-os/light-reports` already;
`self-reported` had never been created, so a self-reported issue would have been the first to find
out.

## light-common 1.2.3 — the wheel click, on a phone that spells its button device differently

`LightKeys` has two ways in: resolve Light's keylayout label to a keycode, or fall back to the
raw Linux scancode. The fallback has to be gated on which device sent the code, because these are
ordinary keyboard codes underneath -- 19 is `r`, 20 is `t`, 66 is F8 -- and an ungated fallback
turns a paired Bluetooth keyboard into the wheel.

The gate was one shared set of exact device names, `{"Pixart pat9126ja", "gpio-keys"}`, and it was
wrong twice over:

- **`gpio-keys` is not the only spelling.** The name is the kernel's, and the devicetree decides
  whether it reads `gpio-keys`, `gpio_keys` or `gpio-keys-wheel`. On a build that chose either of
  the other two, an exact match fails and the wheel click never arrives at all. Nothing logs
  anything; the button just does nothing, which looks like an app ignoring it.
- **One set for five codes lets either device claim any of them.** The turns come from the optical
  sensor and the click and camera stages from the board's button device, and nothing was stopping
  the sensor answering for scancode 66.

BrightRecorder has carried the right shape in its own copy since it was written: a per-scancode
predicate, exact for the sensor and prefix for the board. That is now here, along with
`fromScanCode(scanCode, deviceName)` so the gating can be tested on the JVM -- `of()` needs a real
`KeyEvent`, which needs a real device, which needs a phone.


Publishing was broken for this release and had to be fixed to ship it. The repo is
`gi-os/BrightCommon` now, and GitHub Packages does not follow a rename on the way *in*: reads
still redirect, so every consumer kept resolving and nothing looked wrong, while the publish
`PUT` returned 404. The artifact coordinates stay `com.gios:light-common` -- renaming those
would break every consumer's dependency line to fix a URL only `lib/build.gradle.kts` uses.

No API removed. Consumers get the fix by bumping.

## light-common 1.2.2 — the propagation workflow, which had never once run

No library code changed. This releases a fix to `bump-consumers.yml`, and the release exists
mostly so the fixed workflow gets to prove itself.

`bump-consumers` is what makes a release here reach anybody: every consumer pins a version, so
without it a fix is just an artifact nobody fetches. It was triggered by `release: published`,
and it had never fired — not once, across every release. GitHub will not start a workflow from an
event raised by `GITHUB_TOKEN`, and the release is created by the publish job's own token. There
is no failed run to notice in that situation. There is nothing at all, which is why 1.2.0 and
1.2.1 both went out to silence and the consumers were bumped by hand.

Now it hangs off `workflow_run` on Publish, which is exempt from that rule, and it is gated on
the publish having concluded successfully so a failed publish bumps nobody.

Two things fell out of fixing it:

- The version is read from the triggering run's `head_branch`, not from `GITHUB_REF_NAME`. Under
  `workflow_run` this job checks out the default branch, so the old expression would have
  resolved to `main` and opened a batch of pull requests pinning `com.gios:light-common:main`.
  There is now a shape check that fails loudly rather than doing that.
- `LightSync` is in the consumer matrix. It became one in its v1.2 and was missing from the list.
