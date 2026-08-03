## light-common 1.1.0 — the wheel, actually a superset this time

**1.0.0 claimed `hw/` was the superset of all ten wheel variants. It was not.** Four things the
apps had and the library did not, found by comparing every declaration rather than by reading the
files:

- **Pressed turns.** Roll's wheel is two controls: a bare turn is zoom, a turn with the wheel held
  in is exposure compensation. `WheelBus` now carries both — `notches` and `pressedNotches`, with
  `send(notches, pressed = false)`. Two flows rather than a flag, because a screen that wants one
  of them should not have to filter the other out of its own callback.
- **`WheelTurns`.** Raw notches for a control that is not a scroller, with `armed` (the
  stray-notch guard) and `pressed`. This was already the shape of the library's private
  `ArmedNotches`, which is now a one-line call to it.
- **`reverse` on `WheelScroll`.** Roll's grid and LightChat's thread run the other way. Defaulted
  false, so nothing else changes.
- **`WheelGate`.** Phono kills the wheel for a whole subtree when a modal is up. Reads in
  composition rather than in the effect, so closing the gate tears the collector down instead of
  leaving it running behind a dead screen. Defaults to on: an app that never calls it is
  unaffected.

`WheelSteps` was left alone and is the one place where migrating is not a straight swap. The
library banks notches and rate-limits them; FastRead's copy stepped once per notch. Its call sites
now pass `notchesPerStep = 1, minIntervalMs = 0` to keep the behaviour they had.

No change to `report/` or `theme/`.
