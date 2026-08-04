## light-common 1.2.0 — the screenshot, and the sensor readout

**Roll, Notebook and Phono could not migrate onto this library without losing something.** They ran
an older reporting lineage that the ten newer apps never had: a screenshot in the issue body, and a
live accelerometer readout on the settings screen. Rather than drop either, both moved in here.

### The screen at the time

`Screenshot` takes `PixelCopy` off the app's own window and puts a greyscale PNG into the issue
body as base64. `View.draw` into a software canvas was tried first and comes back with the text and
none of the images, because the composition draws through a hardware canvas.

No permission is involved — the app is looking at itself, not capturing the screen. Base64 inflates
by 4/3 against a 65536-character body limit, so the encoder walks 360px → 280px → 200px and gives up
rather than filing something GitHub will reject.

`ReportOverlay` captures when an *offer* is raised, not when the sheet opens: the chip is about to
sit on top of whatever looked wrong, and by the time anyone taps it that screen is gone. A crash
offer gets no picture, because the screen after a relaunch is not the screen that died. Encoding
happens off the main thread — PNG compression is a visible hitch on the LPIII, and this is the one
moment the UI is animating a sheet away.

Dropping an offer recycles its bitmap. Holding one for a report nobody sent is exactly the kind of
leak that goes unnoticed on a phone with 4GB.

### You see it before it goes

The sheet shows the picture rather than describing it: a thumbnail, desaturated exactly the way the
encoder will desaturate it, next to an ATTACHED / LEFT OUT chip. Tapping either drops it.

Attached by default, because a picture answers most "looks wrong" reports on its own and a default
of off would make the useful case cost a decision every time. But a screenshot is the one part of a
report that can carry something you did not mean to send — a thread, a ticket, a face — and
`PixelCopy` needs no permission, so nothing else in the flow would ever mention it. Left out, the
bitmap is recycled and never encoded.

### ShakeMonitor

The numbers behind the gesture — current g, held peak, turns counted, turns needed, shakes fired —
as a `StateFlow`, for a settings screen to show. Gated on a watcher count: the detector runs at 50Hz
whenever the app is in front, and publishing that into a flow nobody collects is pure waste.

### Also

`Reports.compose` takes `shot: String?`, defaulted, so the ten apps already on 1.1.0 need no change.
