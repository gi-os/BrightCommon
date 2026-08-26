## light-common 1.5.0 — four things the vendored copies did that this one did not

Migrating BrightMusic, Roll and BrightNotebook off their own `report/` meant reading their copies
line by line against this one, and they were not identical. Four differences were real, and all
four are fixed here rather than in the apps.

**A 401 or a 404 no longer deletes the queue.** `post` treated every 4xx that was not 403 or 429 as
"this can never succeed, drop it". The two failures this fleet has actually had are an expired
`REPORT_TOKEN` (401) and a tracker repo that had been renamed (404) — both conditions a later build
fixes, and both were quietly throwing away every report on disk. Only 400, 413 and 422 are the
payload's own fault now; everything else waits. A corrupt queue file also no longer blocks the
reports behind it.

**`seedNote` is back.** All three apps pre-filled the note with "Could not …" for a failure the app
caught itself, and the note becomes the issue *title*. Without it every self-reported issue arrived
called "Something else", which is a wall of identical headlines in the one place triage actually
reads.

**The stack trace only travels with a report that is about a crash.** `ReportHost` was passing it to
every report filed on the launch after a crash — and `crash` outranks the symptom when the label is
chosen, so a report about a slow list was being filed as a crash, with a trace about something else
attached. It now goes with a crash prompt or with a shake that says the app closed itself, which is
what the apps did. The log is also only cleared when the report carried it.

**The crash offer appears once per launch, not once per activity.** The apps guarded on
`savedInstanceState == null`; a library composable cannot see that. `CrashLog.readOnce` uses a
process-scoped flag instead, which draws the same line — a recreation stays in the process, a launch
does not. Roll and BrightNotebook both recreate the activity on their own colour-mode handling, and
both were re-raising "IT CRASHED · SEND?" every time.

Also: three gesture tests came back from the deleted suites — one turn short of the count, just
under the threshold, and a second shake after the cooldown. Every one of them is a number that
cannot be checked on a phone.

## light-common 1.4.1 — the send stays off the main thread

A fix to 1.4.0, found reading it back rather than on a phone. `ReportOverlay` encoded the
screenshot inline in the send handler: a PNG compress of a full-screen bitmap plus a base64 pass,
tens to hundreds of milliseconds on this hardware, on the frame that closes the sheet. The vendored
copies this release replaces had always done that work on `Dispatchers.IO`, so shipping it inline
would have turned a lossless migration into a visible hitch on SEND — in a camera app, on a phone
with no spare frames.

Composing and encoding now happen inside the coroutine, and the picture and the failure are
snapshotted before the sheet state is cleared, because the lambda returns before the coroutine
runs.

## light-common 1.4.0 — the screenshot, and the last three apps

No new feature on the phone. This is the release that lets BrightMusic, Roll and BrightNotebook
stop carrying their own `report/`, which they have done since the library existed for one reason:
`Screenshot.kt`. Migrating them without it would have silently dropped the picture from every
issue they file, and a report saying "LOOKS OFF" with a screenshot is a different class of thing
from one without.

**`Screenshot`** is Roll's, behaviour unchanged: `PixelCopy` off the app's own window (no
permission, because nothing outside the app is being read), greyscaled and shrunk down a
360/280/200px ladder until it fits 30KB, base64 inside the issue body inside a `<details>`.
Uploading a file instead would need `contents: write` on a token that ships inside a sideloaded
APK; `issues: write` alone means a lifted key can only write junk into one private tracker.

**Taken when the offer goes up, not when the sheet asks.** `ReportOverlay` grabs the window the
moment a shake, a `Trouble.record` or a `Feedback.ask()` raises the chip — by the time the sheet is
open, the chip and the sheet are what is on screen and the thing that looked wrong is behind them.
It is held as a `Bitmap` and encoded only on send, because most offers are ignored rather than
tapped. A crash gets no picture at all: that offer appears on the launch *after* the one that
died.

**A SCREENSHOT row in the sheet**, attached by default, one tap to refuse, and it says `NONE TAKEN`
rather than disappearing when `PixelCopy` came back empty — a row that vanishes reads as a feature
that was never there. Ideas carry a picture too: "this row should show the year" is a sentence that
needs the row.

**`ShakeMonitor` / `ShakeReading`** came over in the same pass. BrightNotebook's settings screen
shows the live g-force readout, and it exists because "I shook it and nothing happened" cannot be
answered from a phone with no logcat attached — with the numbers on screen the question becomes "it
peaked at 1.2g and needs 1.38g". Publishing is gated on `ShakeMonitor.watchers`, so the 50Hz stream
costs nothing unless a screen is actually displaying it.

**The chip's corner is a parameter now.** Each of the three apps had already placed its own chip
around its own chrome — Roll bottom-start to stay off the shutter, BrightNotebook bottom-end and
raised above a bottom bar — and moving a chip they had already positioned would be a regression
dressed up as consolidation. `ReportOverlay(corner =, inset =, bottomInset =)` defaults to exactly
where it was.

### API

- `ReportOverlay` and `ReportChip` gain `corner`, `inset`, `bottomInset`, all defaulted.
- `Draft` gains `includeShot` (defaulted true).
- `Reports.compose` gains `shot: String?`, as do `composeBug` and `composeIdea`. Both defaulted, so
  existing calls are untouched.
- New: `Screenshot`, `ShakeMonitor`, `ShakeReading`.

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
