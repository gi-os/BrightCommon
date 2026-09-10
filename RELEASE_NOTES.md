## light-common 1.9.0 — a bug report can carry what the app knows

**`LightReport.details`** is a hook an app sets once: `LightReport.details = { pageLog.dump() }`.
Whenever a bug report is composed, the answer goes into the body as a fenced block under
**What the app knows**, after *Where* and before *Build*, capped at 8,000 characters. Null, a
blank answer, or a hook that throws adds nothing, and ideas never ask.

Web Tools is the first user: its issues used to arrive with the page's navigation log, the wall
decisions, the engine version and whether the bridge extension was connected, and moving it onto
this library would have dropped all of that. Now it rides along. A player can attach the stream
it was on the same way.

Nothing else changed. Apps that never set the hook get the body they had.
