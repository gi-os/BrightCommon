package com.gios.light.common.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of `sync/` that hold no Android types, which is deliberately most of the parts that
 * can be wrong. Archive layout and the trust check need a device; this does not.
 */
class ContentsTest {

    @Test
    fun `empty means nothing to write`() {
        assertTrue(Contents().isEmpty)
        assertFalse(Contents(prefs = listOf("a")).isEmpty)
        assertFalse(Contents(databases = listOf("a")).isEmpty)
        assertFalse(Contents(files = listOf("a")).isEmpty)
    }

    @Test
    fun `plus concatenates rather than merging`() {
        // Order and duplicates are kept on purpose: the writer skips files that are not there,
        // so a repeated entry costs one stat call, and de-duplicating here would quietly hide
        // an app declaring the same database from two stores — which is a bug worth seeing.
        val sum = Contents(prefs = listOf("a"), files = listOf("x")) +
            Contents(prefs = listOf("b"), databases = listOf("d"))
        assertEquals(listOf("a", "b"), sum.prefs)
        assertEquals(listOf("d"), sum.databases)
        assertEquals(listOf("x"), sum.files)
    }

    @Test
    fun `a store keeps the name it was given`() {
        // The name is written into the archive and read back on restore, so this is not a
        // triviality: renaming a store orphans every blob already on BasilNet.
        assertEquals("notes", FileStore("notes", Contents()).name)
    }
}
