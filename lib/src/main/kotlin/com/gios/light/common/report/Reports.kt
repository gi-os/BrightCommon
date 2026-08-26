package com.gios.light.common.report

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What went wrong, in the order the chips appear.
 *
 * Two labels because the two readers are different: `chip` has to fit a third of a 3.92" screen,
 * `label` is read weeks later in an issue title with no phone in front of you.
 */
enum class Symptom(val chip: String, val label: String, val slug: String) {
    Crashed("CLOSED", "It closed itself", "crash"),
    Froze("FROZE", "It stopped responding", "freeze"),
    Wrong("LOOKS OFF", "Something looks wrong", "render"),
    Slow("SLOW", "It was very slow", "slow"),
    Other("OTHER", "Something else", "other"),
}

/**
 * Whether this is a complaint or a suggestion.
 *
 * Both arrive through the same gesture on purpose. The moment somebody notices the app is worth
 * shaking at is the same moment they know what they wish it did instead, and making the idea take
 * a different route — an email address, a repo nobody can see, a form on a phone with no browser —
 * is how you guarantee it is never sent. The chip in the corner asks one question and the sheet
 * splits it in two.
 */
enum class Kind(val chip: String, val slug: String) {
    Bug("BUG", "bug"),
    Idea("IDEA", "enhancement"),
}

/**
 * What kind of idea, in the reporter's terms rather than a maintainer's.
 *
 * Four, so they tile two by two on a 3.92" panel, and worded as wants instead of as backlog
 * categories — "Something is missing" is a sentence somebody types; "gap analysis" is not.
 */
enum class Wish(val chip: String, val label: String, val slug: String) {
    New("NEW", "Something new I want", "idea-new"),
    Change("CHANGE", "Change how something works", "idea-change"),
    Missing("MISSING", "Something is missing", "idea-missing"),
    Other("OTHER", "Something else", "idea-other"),
}

/**
 * Everything the sheet collected, handed up in one piece.
 *
 * A parameter list would have grown a boolean and two enums this release, and half of any given
 * call would have been defaults — a draft says plainly that a report is one object with an unused
 * half depending on which way [kind] went.
 */
data class Draft(
    val kind: Kind,
    val symptom: Symptom,
    val wish: Wish,
    val note: String,
    /** Optional. Empty means the reporter did not want to be called back, which is allowed. */
    val phone: String,
    /**
     * Whether to attach the picture taken when the offer was raised. Ignored when there is no
     * picture — a failed `PixelCopy` and a declined attachment look the same from here on.
     */
    val includeShot: Boolean = true,
)

/** A report on its way out: exactly the three fields the issues API wants. */
data class Report(val title: String, val body: String, val labels: List<String>)

/**
 * Shake-to-report, from the phone to a GitHub issue.
 *
 * Reports queue on disk first and are posted afterwards, always — not as a fallback for being
 * offline. A phone that reports a freeze is by definition a phone that was just misbehaving, and
 * a report that exists only in flight is the one report guaranteed to be lost. The queue is also
 * why the send button can close the sheet immediately: nothing the user sees depends on a socket.
 *
 * Ported from LightCamera, with one deliberate change: the HTTP call is `HttpURLConnection`
 * rather than OkHttp. Most of the apps this module is going into do not otherwise need a
 * networking library, and a bug reporter that drags 800KB of OkHttp into a tip calculator is a
 * bug reporter that does not get installed. Two requests an hour at most does not need a
 * connection pool.
 */
object Reports {

    private const val DIR = "reports"
    private const val MAX_QUEUED = 20
    private const val TIMEOUT_MS = 45_000

    /** True when this build can actually send. False means reports pile up in the queue. */
    fun canSend(): Boolean = LightReport.token.isNotBlank()

    fun pendingCount(context: Context): Int = queued(context).size

    /**
     * Turn what the sheet collected into an issue.
     *
     * The front door, and the only one [ReportHost] uses. A bug and an idea are two different
     * documents rather than one document with empty sections: an idea has no stack trace, no free
     * space and no heap figure, and a body that carries those anyway reads as a bug report that
     * failed to collect them.
     */
    fun compose(
        context: Context,
        draft: Draft,
        screen: String,
        crash: String?,
        failure: Failure? = null,
        /** Base64 PNG from [Screenshot.encode], or null. Dropped when the draft declined it. */
        shot: String? = null,
    ): Report {
        val picture = shot?.takeIf { draft.includeShot }
        return when (draft.kind) {
            Kind.Bug -> composeBug(
                context = context,
                symptom = draft.symptom,
                note = draft.note,
                screen = screen,
                crash = crash,
                failure = failure,
                phone = draft.phone,
                shot = picture,
            )
            Kind.Idea -> composeIdea(
                context = context,
                wish = draft.wish,
                note = draft.note,
                phone = draft.phone,
                screen = screen,
                shot = picture,
            )
        }
    }

    /**
     * What somebody wishes the app did.
     *
     * Short on purpose. The build table stays — an idea that only makes sense on the current
     * version is a common shape, and "already possible in v1.4" is an answer — but nothing here
     * pretends to be diagnostics.
     */
    fun composeIdea(
        context: Context,
        wish: Wish,
        note: String,
        phone: String,
        screen: String,
        shot: String? = null,
    ): Report {
        val version = versionName(context)
        val trimmed = note.trim()
        val headline = trimmed.takeIf { it.isNotEmpty() }?.let { first(it) } ?: wish.label
        val body = buildString {
            appendLine("### The idea")
            appendLine()
            appendLine(wish.label + (trimmed.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: ""))
            appendLine()
            appendLine("### Where")
            appendLine()
            appendLine("Asked for from the `$screen` screen.")
            appendLine()
            appendLine("### Who is asking")
            appendLine()
            appendLine("| | |")
            appendLine("|-|-|")
            appendLine("| App | ${LightReport.appName} $version |")
            appendLine("| Package | ${context.packageName} |")
            appendLine("| Device | ${Build.MANUFACTURER} ${Build.MODEL} |")
            appendLine("| Reporter | ${Device.reporter(context)} |")
            appendLine("| Reach them | ${reach(phone)} |")
            appendLine("| Reported | ${stamp()} |")
            // Worth as much here as on a bug: "this row should show the year" is a sentence that
            // needs the row.
            appendShot(shot)
        }
        return Report(
            title = "${LightReport.appName} $version — idea: $headline",
            body = body,
            labels = listOf(LightReport.label, Kind.Idea.slug, wish.slug, Device.label(context)),
        )
    }

    /**
     * A bug: what it was, where it was, and what the phone looked like at the time.
     *
     * The body this produces is the one the tracker has always had, with two rows added to the
     * build table. `phone` is last and defaulted so the shape of every existing call site is
     * unchanged apart from the name.
     */
    fun composeBug(
        context: Context,
        symptom: Symptom,
        note: String,
        screen: String,
        crash: String?,
        failure: Failure? = null,
        phone: String = "",
        shot: String? = null,
    ): Report {
        val version = versionName(context)
        val trimmed = note.trim()
        // The note is the headline when there is one. A title reading "Something else" tells
        // you nothing three weeks later; "standings empty for the WNBA" is the whole report.
        val headline = trimmed.takeIf { it.isNotEmpty() }?.let { first(it) } ?: symptom.label
        val body = buildString {
            appendLine("### What happened")
            appendLine()
            appendLine(symptom.label + (trimmed.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: ""))
            appendLine()
            if (failure != null) {
                appendLine("### What the app itself reported")
                appendLine()
                appendLine("Could not ${failure.what}.")
                if (!failure.detail.isNullOrBlank()) {
                    appendLine()
                    appendLine("```")
                    appendLine(failure.detail)
                    appendLine("```")
                }
                appendLine()
            }
            appendLine("### Where")
            appendLine()
            appendLine("On the `$screen` screen.")
            appendLine()
            appendLine("### Build")
            appendLine()
            appendLine("| | |")
            appendLine("|-|-|")
            appendLine("| App | ${LightReport.appName} $version |")
            appendLine("| Package | ${context.packageName} |")
            appendLine("| Android | ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) |")
            appendLine("| Device | ${Build.MANUFACTURER} ${Build.MODEL} |")
            appendLine("| Firmware | ${Build.DISPLAY} |")
            appendLine("| Reporter | ${Device.reporter(context)} |")
            appendLine("| Reach them | ${reach(phone)} |")
            appendLine("| Reported | ${stamp()} |")
            appendLine("| Free space | ${megabytes(context.filesDir.freeSpace)} |")
            appendLine("| Heap | ${megabytes(usedHeap())} of ${megabytes(Runtime.getRuntime().maxMemory())} |")
            appendLine()
            appendLine("### Last crash")
            appendLine()
            if (crash.isNullOrBlank()) {
                appendLine("None — the app did not die, so this is a glitch and not a stack trace.")
            } else {
                appendLine("```")
                appendLine(crash.take(6_000))
                appendLine("```")
            }
            appendShot(shot)
        }
        val labels = buildList {
            add(LightReport.label)
            // A stack trace outranks whatever chip was tapped: if the app died, that is what
            // this report is about, whatever the person picked from a list afterwards.
            add(if (!crash.isNullOrBlank()) "crash" else symptom.slug)
            // Worth separating: the app noticed this one on its own, so it is reproducible
            // from the detail rather than from somebody remembering what they were doing.
            if (failure != null) add("self-reported")
            // Which half of the tracker this belongs in. Gio's own reports are reproducible on
            // the bench; a stranger's is the only account of the bug there will ever be.
            add(Device.label(context))
        }
        return Report(
            title = "${LightReport.appName} $version — $headline",
            body = body,
            labels = labels,
        )
    }

    /**
     * Write the report to disk, then try to send everything waiting.
     *
     * Queue first, always. See the class comment.
     */
    suspend fun submit(context: Context, report: Report): Unit = withContext(Dispatchers.IO) {
        enqueue(context, report)
        flush(context)
    }

    /** Post everything queued. Safe to call on launch; does nothing without a token. */
    suspend fun flush(context: Context): Unit = withContext(Dispatchers.IO) {
        if (!canSend()) return@withContext
        for (f in queued(context)) {
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            val json = runCatching { JSONObject(text) }.getOrNull()
            // A file that is not JSON can never be posted, so it goes — but the ones behind it
            // are fine, and returning here left them stuck behind one bad byte.
            if (json == null) {
                f.delete()
                continue
            }
            if (post(json)) f.delete() else return@withContext
        }
    }

    // ---------------------------------------------------------------- queue

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun queued(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    private fun enqueue(context: Context, report: Report) {
        val d = dir(context)
        // A phone that has been failing offline for a week should not fill its own storage
        // with the evidence. Oldest goes first: the newest report is the one still relevant.
        val existing = queued(context)
        if (existing.size >= MAX_QUEUED) {
            existing.take(existing.size - MAX_QUEUED + 1).forEach { it.delete() }
        }
        val payload = JSONObject()
            .put("title", report.title)
            .put("body", report.body)
            .put("labels", JSONArray().apply { report.labels.forEach { put(it) } })
        runCatching {
            File(d, "${System.currentTimeMillis()}-${(0..999).random()}.json")
                .writeText(payload.toString())
        }
    }

    // ---------------------------------------------------------------- transport

    /** @return true when the issue was created, or when it never can be and should be dropped. */
    private fun post(payload: JSONObject): Boolean {
        val url = URL("https://api.github.com/repos/${LightReport.repo}/issues")
        var conn: HttpURLConnection? = null
        return runCatching {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${LightReport.token}")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", LightReport.appName.ifBlank { "light-common" })
            }
            conn!!.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn!!.responseCode
            // Only the payload's own fault counts as undeliverable. This used to drop every 4xx
            // that was not 403 or 429, which quietly threw the whole queue away on the two
            // failures this fleet has actually had: an expired REPORT_TOKEN (401) and a tracker
            // repo that had been renamed (404). Both are conditions a later build fixes, and a
            // report deleted because the token expired is a report nobody ever sees. A malformed
            // body or one over the size cap will never post, whatever build tries it.
            code in 200..299 || code == 400 || code == 413 || code == 422
        }.getOrDefault(false).also { runCatching { conn?.disconnect() } }
    }

    // ---------------------------------------------------------------- detail

    private fun first(text: String): String {
        val line = text.trim().lineSequence().firstOrNull().orEmpty().trim()
        return if (line.length <= 72) line else line.take(69).trimEnd() + "…"
    }

    /** Prefixed here rather than at every call site, and never blank — "v?" is still a fact. */
    private fun versionName(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.let { "v$it" } ?: "v?"

    /**
     * How to get back to the reporter, or a plain statement that there is no way to.
     *
     * "Not given" is written out rather than left blank because an empty table cell reads as a
     * field that failed to collect, and the difference matters when you are deciding whether an
     * unreproducible report can be chased.
     */
    private fun reach(phone: String): String =
        Contact.tidy(phone).takeIf { it.isNotEmpty() } ?: "not given"

    /**
     * The picture, folded away.
     *
     * Base64 inside the body rather than an uploaded file: attaching one would need
     * `contents: write` on a token that ships inside a sideloaded APK, and `issues: write` alone
     * means a lifted key can only write junk into one private tracker. Collapsed in a `<details>`
     * because 30KB of base64 is the whole issue otherwise.
     */
    private fun StringBuilder.appendShot(shot: String?) {
        if (shot == null) return
        appendLine()
        appendLine("<details><summary>Screenshot (base64 PNG, greyscale)</summary>")
        appendLine()
        appendLine("```")
        appendLine(shot)
        appendLine("```")
        appendLine()
        appendLine("</details>")
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun megabytes(bytes: Long): String = "${bytes / 1_048_576} MB"

    private fun usedHeap(): Long =
        Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
}
