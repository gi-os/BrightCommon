## light-common 1.10.0 — a report too long to post was thrown away

**GitHub refuses an issue body over 65,536 characters with a 422, and a 422 means the queue
deletes the file.** That is right as far as it goes: no later build can post a body the API will
never accept. What it meant in practice is that a report sent from a phone could vanish with
nothing on either side to say so, and the apps it happened to are the ones with the most to say.
A browser's page log and a base64 screenshot clear the cap between them.

Bodies are now trimmed before they are queued, in the order things are worth losing: the
screenshot first, because the words describe what it shows, then the middle, keeping what
happened, where, the build table and the tail. Whatever went is said in the body, so a thin
report is never mistaken for a whole one.

Nothing to change in an app. `Reports.fit` runs inside `composeBug` and `composeIdea`.

## light-common 1.9.1 — a bug report can carry what the app knows

1.9.1 over 1.9.0: in 1.9.0 the hook shipped with a private setter by mistake, so no app could set
it. Same feature, now settable.

**`LightReport.details`** is a hook an app sets once: `LightReport.details = { pageLog.dump() }`.
Whenever a bug report is composed, the answer goes into the body as a fenced block under
**What the app knows**, after *Where* and before *Build*, capped at 8,000 characters. Null, a
blank answer, or a hook that throws adds nothing, and ideas never ask.

Web Tools is the first user: its issues used to arrive with the page's navigation log, the wall
decisions, the engine version and whether the bridge extension was connected, and moving it onto
this library would have dropped all of that. Now it rides along. A player can attach the stream
it was on the same way.

Nothing else changed. Apps that never set the hook get the body they had.
