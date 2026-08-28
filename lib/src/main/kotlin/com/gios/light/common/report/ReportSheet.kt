package com.gios.light.common.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Why the sheet is up: you shook the phone, the app died the last time you had it open, or the
 * app noticed by itself that something it tried did not work.
 */
enum class ReportReason { Shaken, Crashed, Failed }

/**
 * What went wrong — or what you wish the app did — once you have said you want to tell somebody.
 *
 * **Deliberately self-contained.** The version of this in LightCamera leans on that app's own
 * `LightChip`, `LightWideButton`, `LightListRow` and theme tokens, which is why it has never
 * moved anywhere else — no two Light apps share a UI vocabulary. Everything visual below is
 * private to this file and built from plain Compose plus `MaterialTheme.colorScheme`, so the
 * whole `report/` package drops into any app with nothing changed but the package line.
 *
 * The greys are the LightOS three — background, content, contentSecondary — read from the host
 * app's own colour scheme where possible so a sheet in a light-themed app is not a black hole.
 *
 * It assumes typing on this phone is expensive: a chip is a complete report on its own, and every
 * text field is optional. But the note is also the only part that carries anything the build table
 * cannot — "standings empty for the WNBA" is a bug, "Something looks wrong" is a shrug — so it
 * takes the headline in the issue title whenever it is filled in.
 *
 * Three things stack top to bottom, and the order is the argument:
 *
 *  - **BUG or IDEA**, first, because it changes what the row underneath means. Only offered for a
 *    shake: a crash and a failure the app caught are not suggestions, and a sheet that invites you
 *    to file a stack trace as a feature request is a sheet that files miscategorised issues.
 *  - **The chips**, which are a complete report on their own.
 *  - **A note and a number**, both optional, both skippable with one tap on SEND.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    reason: ReportReason,
    /** What the app already knows went wrong, for a failure it noticed itself. */
    failure: String? = null,
    /** How the app calls itself in the sentence "X could not …". */
    appName: String = "This app",
    /**
     * What the note starts as.
     *
     * Used for a failure the app caught: the note becomes the issue *title*, so an empty one
     * means every self-reported issue arrives called "Something else" and triage reads a wall of
     * identical headlines. The app already knows what failed; making somebody retype it on this
     * keypad is a tax for no information.
     */
    seedNote: String = "",
    /** The number given last time, so a second report does not retype it. See [Contact]. */
    knownPhone: String = "",
    /**
     * Whether a picture of the screen was taken when the offer went up. False also covers the
     * copy having failed, which is why the row says so rather than disappearing.
     */
    hasScreenshot: Boolean = false,
    onDismiss: () -> Unit,
    onSend: (Draft) -> Unit,
) {
    // A crash or a caught failure is a bug and cannot be anything else, so the toggle is not
    // shown for them — and the state still starts on Bug, so nothing depends on it being hidden.
    val choosable = reason == ReportReason.Shaken
    var kind by remember { mutableStateOf(Kind.Bug) }
    var symptom by remember {
        mutableStateOf(if (reason == ReportReason.Crashed) Symptom.Crashed else Symptom.Other)
    }
    var wish by remember { mutableStateOf(Wish.New) }
    var note by remember { mutableStateOf(seedNote) }
    var phone by remember { mutableStateOf(knownPhone) }
    var withShot by remember { mutableStateOf(hasScreenshot) }
    val scroll = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val content = MaterialTheme.colorScheme.onBackground
    val secondary = content.copy(alpha = 0.55f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        // The keyboard is a window over this one, and this sheet is inside a scroll, so
        // without an inset the note field slides underneath it as the text grows: the caret
        // stays where it was and the line you are typing disappears behind the keys. Reported
        // as the note line "becoming hidden by keyboard as text becomes longer", which is
        // exactly what it looks like — the field is fine, the sheet simply does not know the
        // bottom of the screen moved.
        //
        // `imePadding` moves the whole column up by the keyboard's height, which is what lets
        // the scroll do the rest: a focused text field inside a vertical scroll is already
        // brought into view by Compose, and it was being brought into a region the keyboard
        // was covering.
        Column(
            Modifier
                .imePadding()
                .verticalScroll(scroll)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
        ) {
            // Said back as the app's own failure, so it is clear the phone already knows and
            // this is not a question you have to answer from memory.
            if (failure != null) {
                Text(
                    text = "$appName could not $failure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            if (choosable) {
                SheetLabel("WHAT IS THIS", secondary)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Kind.entries.forEach { option ->
                        SheetChip(
                            label = option.chip,
                            selected = kind == option,
                            content = content,
                            secondary = secondary,
                            modifier = Modifier.weight(1f),
                        ) { kind = option }
                    }
                }
            }

            SheetLabel(
                text = if (kind == Kind.Bug) "WHAT HAPPENED" else "WHAT WOULD YOU LIKE",
                color = secondary,
                modifier = Modifier.padding(top = if (choosable) 22.dp else 0.dp),
            )
            // Two per row rather than five full-width rows: five rows would push the note
            // field and the send button off a 3.92" panel.
            ChipGrid(
                labels = if (kind == Kind.Bug) {
                    Symptom.entries.map { it.chip }
                } else {
                    Wish.entries.map { it.chip }
                },
                selected = if (kind == Kind.Bug) symptom.ordinal else wish.ordinal,
                content = content,
                secondary = secondary,
                modifier = Modifier.padding(top = 10.dp),
            ) { index ->
                if (kind == Kind.Bug) symptom = Symptom.entries[index] else wish = Wish.entries[index]
            }

            SheetLabel("NOTE", secondary, Modifier.padding(top = 22.dp))
            SheetField(
                value = note,
                onValueChange = { note = it },
                placeholder = if (kind == Kind.Bug) {
                    "What were you doing? (optional)"
                } else {
                    "What should it do? (optional)"
                },
                content = content,
                secondary = secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            // A number, not a chat handle. A report is a one-way statement — a chip row, a
            // sentence and a build table — and the one thing that turns an unreproducible one
            // into a fixed bug is being able to ask a follow-up question. Everybody running
            // these apps is, by definition, reachable on a phone.
            SheetLabel("PHONE", secondary, Modifier.padding(top = 22.dp))
            SheetField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = "So I can ask more (optional)",
                content = content,
                secondary = secondary,
                keyboard = KeyboardType.Phone,
                modifier = Modifier.padding(top = 8.dp),
            )

            // On by default and one tap to turn off. The picture is the difference between
            // "LOOKS OFF" being actionable and being a shrug, so the default has to be attach —
            // but it is a picture of whatever was on screen, so it must always be refusable.
            SheetLabel("SCREENSHOT", secondary, Modifier.padding(top = 22.dp))
            SheetChip(
                label = when {
                    !hasScreenshot -> "NONE TAKEN"
                    withShot -> "ATTACHED"
                    else -> "NOT ATTACHED"
                },
                selected = withShot && hasScreenshot,
                content = content,
                secondary = secondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { if (hasScreenshot) withShot = !withShot }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetChip(
                    label = "CANCEL",
                    selected = false,
                    content = content,
                    secondary = secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                SheetChip(
                    label = "SEND",
                    selected = true,
                    content = content,
                    secondary = secondary,
                    modifier = Modifier.weight(1f),
                ) {
                    onSend(
                        Draft(
                            kind = kind,
                            symptom = symptom,
                            wish = wish,
                            note = note,
                            phone = phone,
                            includeShot = withShot && hasScreenshot,
                        ),
                    )
                }
            }

            Text(
                text = when {
                    !Reports.canSend() ->
                        "This build has no reporting key, so it will wait on the phone until one does."
                    kind == Kind.Idea ->
                        "Goes to the private light-reports tracker as an idea, with your app version."
                    withShot && hasScreenshot ->
                        "Goes to the private light-reports tracker, with the build details, the " +
                            "last crash and the screenshot attached."
                    else ->
                        "Goes to the private light-reports tracker, with the build details and the " +
                            "last crash attached."
                },
                style = MaterialTheme.typography.labelSmall,
                color = secondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = color,
        modifier = modifier,
    )
}

/**
 * Chips two to a row, selected by index.
 *
 * By index rather than by enum, because the row shows symptoms or wishes depending on a toggle
 * above it and two near-identical generic overloads is a worse trade than one integer. An odd
 * last chip keeps its half of the row instead of stretching to fill it.
 */
@Composable
private fun ChipGrid(
    labels: List<String>,
    selected: Int,
    content: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { column, label ->
                    val index = row * 2 + column
                    SheetChip(
                        label = label,
                        selected = index == selected,
                        content = content,
                        secondary = secondary,
                        modifier = Modifier.weight(1f),
                    ) { onSelect(index) }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A chip. Outlined when it is one of several, filled when it is the thing you came to press.
 *
 * No ripple: LightOS has none anywhere, and a Material ripple is the single clearest tell that
 * a sheet was not written for this phone.
 */
@Composable
private fun SheetChip(
    label: String,
    selected: Boolean,
    content: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(40.dp)
            .then(
                if (selected) {
                    Modifier.background(content)
                } else {
                    Modifier.border(BorderStroke(1.dp, secondary))
                },
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = if (selected) MaterialTheme.colorScheme.background else content,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A one-line field, underlined rather than boxed.
 *
 * Material's filled container and floating label appear nowhere in LightOS, so this is a
 * [BasicTextField] over a rule — the same shape as the SDK's own `LightTextField`. The
 * placeholder sits behind the field rather than floating away, because on a screen this size a
 * label that moves is a label you lose.
 *
 * `keyboard` exists for the phone field: on a keypad-first phone the difference between the
 * number pad and the alphabet is the difference between four taps and forty.
 */
@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    content: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = content),
                cursorBrush = SolidColor(content),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(secondary))
    }
}
