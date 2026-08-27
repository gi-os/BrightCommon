package com.gios.light.common.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every case here is one that shipped wrong at least once and cost a release to find, because on
 * the phone they all look the same: the panel is grey, or the panel flickers.
 */
class ColourPlanTest {

    private val monoPhone = Filter.MONO
    private val colourPhone = Filter(enabled = 0, mode = Filter.MODE_NONE)

    // ------------------------------------------------------------------ the filter pair

    @Test
    fun `off is no filter, not a filter switched off`() {
        // enabled = 0 with mode = 0 reads back as "monochrome, currently off", and anything that
        // reconstitutes the pair afterwards makes the screen grey again. Only -1 is no filter.
        assertEquals(Filter.MODE_NONE, Filter.COLOUR.mode)
        assertEquals(-1, Filter.COLOUR.mode)
        assertTrue(Filter.COLOUR.isColour)
    }

    @Test
    fun `mono is the filter LightOS pins`() {
        assertEquals(Filter(enabled = 1, mode = 0), Filter.MONO)
        assertTrue(!Filter.MONO.isColour)
    }

    // ------------------------------------------------------------------ desired state

    @Test
    fun `nothing holding leaves the phone at its baseline`() {
        assertEquals(
            monoPhone,
            ColourPlan.desired(0, ColourWant.Colour, appVisible = true, baseline = monoPhone),
        )
    }

    @Test
    fun `a phone that was colour is not left mono by an app that borrowed the setting`() {
        assertEquals(
            colourPhone,
            ColourPlan.desired(0, ColourWant.Colour, appVisible = true, baseline = colourPhone),
        )
    }

    @Test
    fun `one holder in front asks for colour`() {
        assertEquals(
            Filter.COLOUR,
            ColourPlan.desired(1, ColourWant.Colour, appVisible = true, baseline = monoPhone),
        )
    }

    @Test
    fun `overlapping holders across a swipe do not hand back to the baseline`() {
        // A pager keeps the next page composed, so the count goes 1 to 2 to 1 and never touches
        // zero. Roll's viewfinder and roll grid overlap exactly like this.
        listOf(1, 2, 1).forEach { holders ->
            assertEquals(
                Filter.COLOUR,
                ColourPlan.desired(holders, ColourWant.Colour, true, monoPhone),
            )
        }
    }

    @Test
    fun `a hold survives the app being counted down to nothing`() {
        // Release below zero must not wrap into "holding". BrightColour clamps, but the plan is
        // asked directly by the local writer and must be safe on its own.
        assertEquals(
            monoPhone,
            ColourPlan.desired(-1, ColourWant.Colour, appVisible = true, baseline = monoPhone),
        )
    }

    @Test
    fun `leaving the app puts the phone back, on the local path`() {
        // The provider gates on the app being in front and never asks this. A writer with no such
        // gate has to put the setting back itself or the whole phone keeps what the app wanted.
        assertEquals(
            monoPhone,
            ColourPlan.desired(1, ColourWant.Colour, appVisible = false, baseline = monoPhone),
        )
    }

    @Test
    fun `mono is a thing an app can ask for`() {
        assertEquals(
            Filter.MONO,
            ColourPlan.desired(1, ColourWant.Mono, appVisible = true, baseline = colourPhone),
        )
    }

    // ------------------------------------------------------------------ write order

    @Test
    fun `going to a filter writes the mode first`() {
        // Enabling first switches on whatever filter is still stored, which is a visible flash of
        // the wrong screen for as long as the second write takes.
        assertEquals(
            listOf(ColourPlan.Setting.Mode, ColourPlan.Setting.Enabled),
            ColourPlan.order(Filter.MONO),
        )
    }

    @Test
    fun `coming off a filter writes the enable flag first`() {
        assertEquals(
            listOf(ColourPlan.Setting.Enabled, ColourPlan.Setting.Mode),
            ColourPlan.order(Filter.COLOUR),
        )
    }

    // ------------------------------------------------------------------ routing

    @Test
    fun `a serving provider means this app writes nothing, grant or no grant`() {
        // The two-writers guard, and the single most important line in the file. An app that holds
        // the grant and writes anyway takes turns with BrightControl on every re-assert.
        assertEquals(
            ColourSource.Provider,
            ColourPlan.route(ColourWire.SERVING, canWriteLocally = true),
        )
        assertEquals(
            ColourSource.Provider,
            ColourPlan.route(ColourWire.SERVING, canWriteLocally = false),
        )
    }

    @Test
    fun `while the bind is in flight nobody writes`() {
        assertEquals(
            ColourSource.None,
            ColourPlan.route(ColourWire.PENDING, canWriteLocally = true),
        )
    }

    @Test
    fun `no provider and a grant falls back to writing it here`() {
        assertEquals(
            ColourSource.Local,
            ColourPlan.route(ColourWire.ABSENT, canWriteLocally = true),
        )
    }

    @Test
    fun `a provider that cannot act falls back to writing it here`() {
        // BrightControl installed but ungranted, or its colour switch off. The request stays
        // remembered over there, so this becomes Provider later without asking again.
        assertEquals(
            ColourSource.Local,
            ColourPlan.route(ColourWire.INERT, canWriteLocally = true),
        )
        assertEquals(
            ColourSource.Local,
            ColourPlan.route(ColourWire.REFUSED, canWriteLocally = true),
        )
    }

    @Test
    fun `no provider and no grant is inert, never a crash`() {
        assertEquals(
            ColourSource.None,
            ColourPlan.route(ColourWire.ABSENT, canWriteLocally = false),
        )
        assertEquals(
            ColourSource.None,
            ColourPlan.route(ColourWire.INERT, canWriteLocally = false),
        )
    }

    @Test
    fun `the sentinels do not collide with a real reply`() {
        // All four are compared as ints against the same field. A duplicate would route one state
        // as another silently.
        val all = listOf(
            ColourWire.SERVING,
            ColourWire.INERT,
            ColourWire.REFUSED,
            ColourWire.ABSENT,
            ColourWire.PENDING,
        )
        assertEquals(all.size, all.distinct().size)
    }
}
