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
