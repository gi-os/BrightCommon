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
