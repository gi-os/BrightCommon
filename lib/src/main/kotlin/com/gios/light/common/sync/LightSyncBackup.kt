package com.gios.light.common.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import com.gios.light.common.LightCommon
import com.gios.light.common.report.LightReport
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The whole of an app's contribution to LightSync.
 *
 * Android's sandbox is why this exists at all: no agent app can read another app's private data,
 * with or without permissions, so backup cannot be done *to* an app from outside. It has to be
 * offered. This class is that offer, and it is deliberately the only thing an app carries — the
 * server address, the schedule, the encryption, the retention and the restore UI all live in
 * LightSync, so none of them can force a release of anything else.
 *
 * It used to be a file each app kept its own copy of, pasted in with the package name swapped.
 * That is how the copies drifted: the trust check was tightened once and stayed loose in the
 * apps nobody re-pasted, and only two of them ever learned to report a label. It lives here now
 * for the same reason the wheel does.
 *
 * ### Declaring what to back up
 *
 * Most apps say it in one line:
 *
 * ```kotlin
 * class Backup : LightSyncBackup() {
 *     override fun stores() = listOf(
 *         FileStore("main", Contents(prefs = listOf("lighttip"))),
 *     )
 * }
 * ```
 *
 * An app with more than one subsystem lists more than one store, and anything sealed with an
 * AndroidKeyStore key uses a [LogicalStore] instead — see [SyncableStore] for why that is not a
 * matter of taste.
 *
 * Overriding [contents] on its own still works and is treated as a single [FileStore] named
 * `main`, which is what the pasted copies effectively were. It produces a byte-identical
 * archive, so blobs already on BasilNet restore into a migrated app unchanged.
 *
 * ### The trust boundary, stated plainly
 *
 * Every app here is signed with its own keystore, so a `signature`-level permission cannot match
 * across them. Instead the caller must be LightSync by package *and* by signing certificate,
 * pinned below. That is weaker than a platform permission and stronger than a package-name check
 * alone: a hostile app can claim the name only by also holding the key. On a phone whose only
 * installer is you it is a reasonable trade, and it is written down rather than assumed.
 */
abstract class LightSyncBackup : ContentProvider() {

    /** A short name for the LightSync app list. Defaults to the app's own label. */
    open fun label(): String = context?.applicationInfo?.let {
        context?.packageManager?.getApplicationLabel(it)?.toString()
    } ?: "App"

    /**
     * What this app offers, one entry per subsystem.
     *
     * The default wraps [contents] so that an app written against the old single-list shape
     * keeps working without an edit.
     */
    open fun stores(): List<SyncableStore> = listOf(FileStore(DEFAULT_STORE, contents()))

    /** The old single-list shape. Prefer [stores]; this is kept so migrations are one commit. */
    open fun contents(): Contents = Contents()

    /**
     * Whether to end the app's process after a restore.
     *
     * On by default, and not paranoia: prefs and Room both cache in memory, so an app that keeps
     * running after its files were swapped underneath it will happily write the old state back
     * over the new one. Dying is the cheapest way to be sure.
     */
    open val restartAfterRestore: Boolean get() = true

    // ------------------------------------------------------------------ archive

    /**
     * Produce the payload.
     *
     * File stores write to `prefs/`, `db/` and `files/` at the archive root — the layout the
     * pasted copies used, kept on purpose so that every blob already sitting on BasilNet is
     * still restorable. Logical stores each get one `blob/<name>` entry, which is new, and which
     * an older agent simply never asks for.
     */
    open fun export(out: FileOutputStream) {
        val ctx = context ?: return
        ZipOutputStream(out.buffered()).use { zip ->
            stores().forEach { store ->
                when (store) {
                    is FileStore -> writeFiles(ctx, zip, store.contents(ctx))
                    is LogicalStore -> {
                        zip.putNextEntry(ZipEntry("$BLOB_PREFIX${store.name}"))
                        store.exportTo(ctx, NonClosingOutput(zip))
                        zip.closeEntry()
                    }
                    // A store shape this version of the library does not know. Skipping it
                    // loses one store; throwing would lose the app's whole backup.
                    else -> Unit
                }
            }
        }
    }

    /** Consume a payload produced by [export]. */
    open fun restore(input: FileInputStream) {
        val ctx = context ?: return
        val logical = stores().filterIsInstance<LogicalStore>().associateBy { it.name }
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                if (entry.name.startsWith(BLOB_PREFIX)) {
                    // Hand the store a stream it cannot close out from under the zip reader:
                    // closing a ZipInputStream mid-archive ends the whole restore, and an
                    // implementation using `use {}` is the normal way to write this.
                    logical[entry.name.removePrefix(BLOB_PREFIX)]
                        ?.restoreFrom(ctx, NonClosingInput(zip))
                    continue
                }
                val target = destination(ctx, entry.name) ?: continue
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { zip.copyTo(it) }
            }
        }
    }

    private fun writeFiles(ctx: Context, zip: ZipOutputStream, c: Contents) {
        c.prefs.forEach { name ->
            add(zip, "prefs/$name.xml", File(ctx.dataDir, "shared_prefs/$name.xml"))
        }
        c.databases.forEach { name ->
            val db = ctx.getDatabasePath(name)
            add(zip, "db/$name", db)
            // Room's write-ahead log holds committed rows that are not in the .db yet, so a
            // backup without it can be minutes stale — or inconsistent.
            add(zip, "db/$name-wal", File(db.path + "-wal"))
            add(zip, "db/$name-shm", File(db.path + "-shm"))
        }
        c.files.forEach { rel -> addTree(zip, "files/$rel", File(ctx.filesDir, rel)) }
    }

    private fun destination(ctx: Context, entry: String): File? = when {
        // Only the three shapes export writes. Anything else is a malformed or hostile archive,
        // and an unrecognised path is exactly how a zip escapes the directory it belongs in.
        entry.startsWith("prefs/") -> File(ctx.dataDir, "shared_prefs/" + entry.removePrefix("prefs/"))
        entry.startsWith("db/") -> ctx.getDatabasePath(entry.removePrefix("db/"))
        entry.startsWith("files/") -> File(ctx.filesDir, entry.removePrefix("files/"))
        else -> null
    }?.takeIf { !it.path.contains("..") }

    private fun add(zip: ZipOutputStream, name: String, file: File) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addTree(zip: ZipOutputStream, name: String, file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { addTree(zip, "$name/${it.name}", it) }
        } else {
            add(zip, name, file)
        }
    }

    // ------------------------------------------------------------------ the plumbing

    final override fun onCreate(): Boolean = true

    final override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        requireAgent()
        if (uri.lastPathSegment != "export") return null
        val ctx = context ?: return null
        // A pipe would avoid the temp file, but it needs a thread writing into it and a reader
        // that never stalls; a file in cacheDir is dull and cannot deadlock.
        val staged = File(ctx.cacheDir, "lightsync-export.zip")
        FileOutputStream(staged).use { export(it) }
        return ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    final override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        requireAgent()
        return when (method) {
            SyncMeta.METHOD_META -> meta()
            SyncMeta.METHOD_IMPORT -> {
                @Suppress("DEPRECATION")
                val fd = extras?.getParcelable<ParcelFileDescriptor>(SyncMeta.FD)
                    ?: return Bundle().apply { putBoolean(SyncMeta.OK, false) }
                fd.use { FileInputStream(it.fileDescriptor).use { input -> restore(input) } }
                Bundle().apply { putBoolean(SyncMeta.OK, true) }.also {
                    if (restartAfterRestore) Process.killProcess(Process.myPid())
                }
            }
            else -> null
        }
    }

    /**
     * Everything the fleet screen shows, gathered in one call.
     *
     * Deliberately all best-effort: an app that cannot answer a field omits it and the agent
     * shows a dash. The alternative — throwing — would make one broken app hide the other
     * sixteen, and this is a diagnostic screen, so it has to survive the thing being diagnosed.
     */
    private fun meta(): Bundle {
        val ctx = context
        val b = Bundle()
        b.putString(SyncMeta.LABEL, runCatching { label() }.getOrDefault("App"))
        b.putString(SyncMeta.COMMON_VERSION, LightCommon.VERSION)
        b.putBoolean(SyncMeta.RESTART_AFTER_RESTORE, restartAfterRestore)
        val declared = runCatching { stores() }.getOrDefault(emptyList())
        b.putStringArray(SyncMeta.STORES, declared.map { it.name }.toTypedArray())
        if (ctx != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                b.putString(SyncMeta.APP_VERSION, info.versionName)
            }
            runCatching { b.putLong(SyncMeta.SIZE_HINT, declared.sumOf { it.sizeHint(ctx) }) }
        }
        // Reporting is optional and the label is blank until install() runs, so this is only
        // present for apps that actually file issues — which is exactly the set worth linking.
        runCatching { LightReport.label }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { b.putString(SyncMeta.REPORT_LABEL, it) }
        return b
    }

    /**
     * LightSync, or nobody. Checked on every call rather than cached: the answer can change
     * under us if the agent is reinstalled with a different key, and that is the case worth
     * catching.
     */
    private fun requireAgent() {
        val ctx = context ?: throw SecurityException("no context")
        if (callingPackage != AGENT_PACKAGE) throw SecurityException("not LightSync")
        val digest = runCatching { certDigest(ctx, AGENT_PACKAGE) }.getOrNull()
        if (digest != AGENT_CERT_SHA256) throw SecurityException("LightSync signature mismatch")
    }

    private fun certDigest(ctx: Context, pkg: String): String {
        val pm = ctx.packageManager
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
        }
        val sha = MessageDigest.getInstance("SHA-256")
        return certs.firstOrNull()
            ?.let { sha.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
            ?: ""
    }

    final override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    final override fun getType(uri: Uri): String = "application/octet-stream"
    final override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    final override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    final override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        const val AGENT_PACKAGE = "com.gios.lightsync"

        /** LightSync's release signing certificate. See its `signing-fingerprint.txt`. */
        const val AGENT_CERT_SHA256 = "bd7da1be119bc3b801523994c3da43556689db4547feaeaadb484b9ace83890e"

        /** The store name an app gets for free when it only overrides `contents()`. */
        const val DEFAULT_STORE = "main"

        internal const val BLOB_PREFIX = "blob/"
    }
}

/**
 * Stream wrappers whose `close()` does nothing.
 *
 * Both halves of a logical store are handed the live zip stream, and closing a zip stream
 * part-way through ends the archive — on the way out it truncates every store after the first,
 * and on the way in it abandons the rest of the restore. Writing `use { }` around a stream you
 * were given is the obvious thing to do, so the wrapper absorbs it rather than the docs asking
 * people not to.
 *
 * The bulk `write`/`read` overrides are not incidental. `FilterOutputStream` implements the
 * array form as a loop of single-byte calls, which for a database-sized store is the difference
 * between a backup and a hang.
 */
private class NonClosingOutput(private val inner: OutputStream) : OutputStream() {
    override fun write(b: Int) = inner.write(b)
    override fun write(b: ByteArray) = inner.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = inner.write(b, off, len)
    override fun flush() = inner.flush()
    override fun close() = inner.flush()
}

private class NonClosingInput(private val inner: InputStream) : InputStream() {
    override fun read(): Int = inner.read()
    override fun read(b: ByteArray): Int = inner.read(b)
    override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
    override fun available(): Int = inner.available()
    override fun skip(n: Long): Long = inner.skip(n)
    override fun close() = Unit
}
