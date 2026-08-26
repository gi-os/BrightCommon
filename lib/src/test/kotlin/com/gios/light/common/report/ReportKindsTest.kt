package com.gios.light.common.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of reporting that do not need a phone.
 *
 * Everything worth checking here is a value that ends up in an issue and is then acted on weeks
 * later — a label that triage filters on, an install id an allowlist is compared against. A typo
 * in any of them still compiles and still files issues; it just files them into a label nobody
 * reads.
 */
class ReportKindsTest {

    @Test
    fun `install ids are eight hex characters`() {
        val id = Device.fingerprint("cd9e459a3b1a9a4d")
        assertEquals(8, id.length)
        assertTrue(id, id.all { it in "0123456789abcdef" })
    }

    @Test
    fun `the same phone always gets the same id`() {
        assertEquals(Device.fingerprint("abc123"), Device.fingerprint("abc123"))
    }

    @Test
    fun `a different phone gets a different id`() {
        assertNotEquals(Device.fingerprint("abc123"), Device.fingerprint("abc124"))
    }

    /**
     * The prefix is part of the value, not decoration. Every id already written into an issue was
     * derived with it, so changing it silently invalidates the owner allowlist — which fails by
     * relabelling Gio's own reports as a stranger's rather than by breaking anything.
     */
    @Test
    fun `the salt is fixed`() {
        assertEquals("7cecdeab", Device.fingerprint("light-phone-iii"))
    }

    @Test
    fun `a number is trimmed and capped, never rejected`() {
        assertEquals("+1 917 555 0142", Contact.tidy("  +1 917   555 0142 "))
        assertEquals("917 turn 4 to 8", Contact.tidy("917 turn 4 to 8"))
        assertEquals(24, Contact.tidy("1".repeat(80)).length)
        assertEquals("", Contact.tidy("   "))
    }

    @Test
    fun `every chip fits the panel and every slug is triage-safe`() {
        val chips = Symptom.entries.map { it.chip } +
            Wish.entries.map { it.chip } +
            Kind.entries.map { it.chip }
        chips.forEach { assertTrue(it, it.length <= 9 && it == it.uppercase()) }

        val slugs = Symptom.entries.map { it.slug } +
            Wish.entries.map { it.slug } +
            Kind.entries.map { it.slug }
        slugs.forEach { assertTrue(it, it.isNotBlank() && it == it.lowercase() && ' ' !in it) }
    }

    /** Two chips to a row, so an odd count is a layout decision rather than an accident. */
    @Test
    fun `the wish chips tile evenly`() {
        assertEquals(0, Wish.entries.size % 2)
    }
}
