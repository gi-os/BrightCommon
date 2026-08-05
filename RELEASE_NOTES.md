## light-common 1.2.0 — LightSync moves in, and R8 stops being everyone's problem

### `sync/` — the backup provider is no longer a file you paste

`LightSyncBackup` lived in `LightSync/module/` as a template with `PACKAGE` to search and
replace. Sixteen copies, and they drifted exactly where you would expect: the agent's signature
check was tightened once and stayed loose in the apps nobody re-pasted, and only two of them
ever learned to answer with a label. It is a real class now, in the library, versioned with
everything else.

**The archive layout did not change.** File stores still write `prefs/`, `db/` and `files/` at
the root, byte for byte, so every blob already on BasilNet restores into a migrated app. An app
that only overrode `contents()` needs no code change beyond the import — the default `stores()`
wraps it as a single `FileStore("main")`.

**`SyncableStore` splits an app into the parts that back up differently.** LightNotebook is
notes *and* a calendar *and* day data; LightAuth's on-disk vault is sealed with an
AndroidKeyStore key and is not portable at all. Under one flat file list an app had to pick the
worst answer for all of it, and LightAuth's answer was to back up ciphertext whose key dies with
the phone — a backup that restores cleanly and decrypts to nothing. Now:

- `FileStore` — the files on disk *are* the backup.
- `LogicalStore` — they are not, so the store writes something portable instead. One
  `blob/<name>` entry each; an older agent simply never asks for them.

Both halves of a logical store get a stream that ignores `close()`. Writing `use { }` around a
stream you were handed is the obvious thing to do, and closing a zip mid-archive truncates every
store after the first.

**`meta` answers with more than a label.** App version, light-common version, store names, a
size hint, and the app's light-reports triage label. All of it best-effort and all of it
optional, because the apps most worth seeing on LightSync's fleet screen are the ones that have
not been updated — an old app should show fewer columns, not vanish. Keys are named once in
`SyncMeta` so the two sides of an untyped `ContentResolver.call` cannot disagree again.

### `LightCommon.VERSION`

Generated from `libraryVersion` via `BuildConfig` rather than written down a second time. A
hand-copied version constant still compiles once it is stale, which is the worst way for a
diagnostic readout to be wrong.

### R8 full mode, handled here rather than in every app

`consumer-rules.pro` now carries what a consumer needs to turn on `isMinifyEnabled` and
`android.enableR8.fullMode=true` without reading this library's source first:

- The backup provider is named as a string in the manifest and nothing calls it, so full mode
  removes it. Kept, with an explicit `<init>()` — in full mode a `-keep` on a class no longer
  implies keeping its members, which is the most common way an app breaks the first time full
  mode goes on.
- `SourceFile` and `LineNumberTable` are kept and the source file renamed. Without this a
  crash report from shake-to-report arrives as a wall of `a.a.a`, which defeats the feature that
  sent it.
- Two comments name mechanisms that deliberately have *no* rule, so the next person does not add
  one out of caution.

There is no blanket `-keep class com.gios.light.**`. That would have been one line and would
have thrown away most of what minification is for.

### A baseline profile ships in the AAR

`src/main/baselineProfiles/light-common.txt`, merged into each consumer's profile at build time:
the wheel, the crash handler installed in `onCreate`, and the type lookup. All of it either runs
before the first frame or on the first input, which is where the LPIII is slowest and where the
first wheel notch after a cold start feels like the app is broken.

No change to `hw/` or `theme/`.
