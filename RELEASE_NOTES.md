## light-common 1.8.0 — the report sheet's note field stays above the keyboard

**Typing a long note into a report pushed the line you were writing behind the keyboard.**

The sheet is a scrolling column, and Compose already brings a focused text field into view inside
a scroll — so the field was being scrolled to, faithfully, into a region the keyboard was covering.
Nothing in the sheet knew the bottom of the screen had moved. The caret stayed where it was and the
words went under the keys, which from the outside looks like the field itself has broken.

One inset fixes it: the column is offset by the keyboard's height, and the scrolling that was
already happening then lands somewhere visible.

This is the sheet every app files reports from, so the fix reaches all of them — but only as each
one picks up this version. Consumers pin, and the bump PRs this release opens are what actually
deliver it.

Fixes [light-reports#134].
