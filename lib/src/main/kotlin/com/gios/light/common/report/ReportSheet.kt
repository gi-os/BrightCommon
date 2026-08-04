package com.gios.light.common.report

import androidx.compose.foundation.BorderStroke
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Why the sheet is up: you shook the phone, the app died the last time you had it open, or the
 * app noticed by itself that something it tried did not work.
 */
enum class ReportReason { Shaken, Crashed, Failed }

/**
 * What went wrong, once you have said you want to tell somebody.
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
 * It assumes typing on this phone is expensive: a chip is a complete report on its own, and the
 * note is optional. But the note is also the only part that carries anything the build table
 * cannot — "standings empty for the WNBA" is a bug, "Something looks wrong" is a shrug — so it
 * takes the headline in the issue title whenever it is filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    reason: ReportReason,
    /** What the app already knows went wrong, for a failure it noticed itself. */
    failure: String? = null,
    /** How the app calls itself in the sentence "X could not …". */
    appName: String = "This app",
    /** The screen as it was when the offer went up, or null when there is no picture. */
    shot: Bitmap? = null,
    onDismiss: () -> Unit,
    onSend: (symptom: Symptom, note: String, attachShot: Boolean) -> Unit,
) {
    var symptom by remember {
        mutableStateOf(if (reason == ReportReason.Crashed) Symptom.Crashed else Symptom.Other)
    }
    var note by remember { mutableStateOf("") }
    // Attached by default: a picture answers most "looks wrong" reports on its own, and a
    // default of off would mean the useful case needs a decision every single time. Shown
    // rather than described, so leaving it out is a glance and one tap.
    var attachShot by remember { mutableStateOf(true) }
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
        Column(
            Modifier
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

            SheetLabel("WHAT HAPPENED", secondary)
            // Two per row rather than five full-width rows: five rows would push the note
            // field and the send button off a 3.92" panel.
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symptom.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { option ->
                            SheetChip(
                                label = option.chip,
                                selected = symptom == option,
                                content = content,
                                secondary = secondary,
                                modifier = Modifier.weight(1f),
                            ) { symptom = option }
                        }
                        // Five is odd; the last chip keeps its half rather than stretching.
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            SheetLabel("NOTE", secondary, Modifier.padding(top = 22.dp))
            NoteField(
                value = note,
                onValueChange = { note = it },
                content = content,
                secondary = secondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            // The picture, if there is one. Visible before sending rather than after, because
            // this is the part of a report that can carry something you did not mean to send —
            // a thread, a ticket, a photograph. Rendered desaturated, which is exactly what
            // gets encoded, so what you see is what leaves the phone.
            if (shot != null) {
                SheetLabel("THE SCREEN", secondary, Modifier.padding(top = 22.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(84.dp)
                            .border(BorderStroke(1.dp, secondary))
                            .clickable(interactionSource = null, indication = null) {
                                attachShot = !attachShot
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = shot.asImageBitmap(),
                            contentDescription = "The screen at the time of the report",
                            contentScale = ContentScale.Crop,
                            alpha = if (attachShot) 1f else 0.25f,
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix().apply { setToSaturation(0f) },
                            ),
                            modifier = Modifier.size(width = 54.dp, height = 82.dp),
                        )
                    }
                    SheetChip(
                        label = if (attachShot) "ATTACHED" else "LEFT OUT",
                        selected = attachShot,
                        content = content,
                        secondary = secondary,
                        modifier = Modifier.weight(1f),
                    ) { attachShot = !attachShot }
                }
            }

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
                ) { onSend(symptom, note, attachShot && shot != null) }
            }

            Text(
                text = if (Reports.canSend()) {
                    "Goes to the private light-reports tracker, with the build details and the " +
                        "last crash attached."
                } else {
                    "This build has no reporting key, so it will wait on the phone until one does."
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
 * A one-line note, underlined rather than boxed.
 *
 * Material's filled container and floating label appear nowhere in LightOS, so this is a
 * [BasicTextField] over a rule — the same shape as the SDK's own `LightTextField`. The
 * placeholder sits behind the field rather than floating away, because on a screen this size a
 * label that moves is a label you lose.
 */
@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    content: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (value.isEmpty()) {
                Text(
                    text = "What were you doing? (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = content),
                cursorBrush = SolidColor(content),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(secondary))
    }
}
