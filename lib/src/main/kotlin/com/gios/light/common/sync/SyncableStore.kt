package com.gios.light.common.sync

import android.content.Context
import java.io.InputStream
import java.io.OutputStream

/**
 * One backed-up thing inside an app.
 *
 * Apps used to answer LightSync with a single flat list of files, which was fine while an app
 * was a single thing. It stopped being fine at LightNotebook, which is notes *and* a calendar
 * *and* day data, each with a different answer to "is this portable?" — and at LightAuth, where
 * the whole point is that the on-disk form is not portable at all. A list of named stores lets
 * an app answer per subsystem instead of picking the worst answer for all of them.
 *
 * Two shapes, and the choice is not stylistic:
 *
 *  - [FileStore] — the files on disk *are* the backup. Another install of the same app on
 *    another phone can open them. Almost everything is this.
 *  - [LogicalStore] — the files on disk are **not** portable, so the store produces something
 *    that is. Anything sealed with an AndroidKeyStore key must be this, because that key cannot
 *    leave the device and will not survive a factory reset. Copying the ciphertext would produce
 *    a backup that restores cleanly and decrypts to nothing.
 *
 * [name] is a stable identifier, not a display string: it is written into the archive and read
 * back on restore, so renaming one orphans every blob already on BasilNet.
 *
 * Not `sealed`, though the two subclasses below are the whole intended set. A sealed hierarchy
 * cannot be extended outside the module that declares it, and every implementor of
 * [LogicalStore] is by definition in another module — the apps. So the exhaustiveness is a
 * convention here rather than a compiler guarantee, and [LightSyncBackup] ignores a store it
 * does not recognise instead of failing the whole export over one.
 */
abstract class SyncableStore(val name: String) {

    /** Roughly how many bytes this store will contribute. Best effort; used only for display. */
    open fun sizeHint(context: Context): Long = 0L
}

/** A store whose on-disk files travel as they are. */
open class FileStore(
    name: String,
    private val contents: Contents,
) : SyncableStore(name) {

    open fun contents(context: Context): Contents = contents

    override fun sizeHint(context: Context): Long {
        val c = contents(context)
        var total = 0L
        c.prefs.forEach { total += java.io.File(context.dataDir, "shared_prefs/$it.xml").lengthOrZero() }
        c.databases.forEach { name ->
            val db = context.getDatabasePath(name)
            total += db.lengthOrZero() + java.io.File(db.path + "-wal").lengthOrZero()
        }
        c.files.forEach { total += java.io.File(context.filesDir, it).treeSize() }
        return total
    }
}

/**
 * A store that produces its own portable bytes.
 *
 * Implementations get one stream out and one stream in, and owe nothing else. Whatever format
 * is written here has to be readable by a *future* build of the app, so it is worth being as
 * boring as the data allows — JSON that a person can read beats a serialised object graph that
 * only this version's class shapes can load.
 */
abstract class LogicalStore(name: String) : SyncableStore(name) {

    abstract fun exportTo(context: Context, out: OutputStream)

    abstract fun restoreFrom(context: Context, input: InputStream)
}

private fun java.io.File.lengthOrZero(): Long = if (isFile) length() else 0L

private fun java.io.File.treeSize(): Long = when {
    isDirectory -> listFiles()?.sumOf { it.treeSize() } ?: 0L
    isFile -> length()
    else -> 0L
}
