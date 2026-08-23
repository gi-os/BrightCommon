package com.gios.light.common

import com.gios.light.common.hw.LightKey
import com.gios.light.common.hw.LightKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The scancode fallback, and which device is allowed to speak for which code.
 *
 * This path only runs on a build whose keylayout does not carry Light's labels, which is
 * exactly the build nobody is testing on — so the gating is worth pinning here. Both failure
 * modes are silent on a phone: too strict and the button never arrives, too loose and a paired
 * keyboard drives the app.
 *
 * [LightKeys.of] itself needs a real `KeyEvent` with a real `InputDevice`, which needs a phone,
 * so this goes through `fromScanCode`.
 */
class LightKeysTest {

    private companion object {
        const val WHEEL_UP = 19
        const val WHEEL_DOWN = 20
        const val WHEEL_CLICK = 66
        const val FOCUS = 80
        const val CAMERA = 27
    }

    @Test
    fun `the optical sensor owns the turns`() {
        assertEquals(LightKey.WheelUp, LightKeys.fromScanCode(WHEEL_UP, "Pixart pat9126ja"))
        assertEquals(LightKey.WheelDown, LightKeys.fromScanCode(WHEEL_DOWN, "Pixart pat9126ja"))
    }

    @Test
    fun `the button device owns the click and the camera stages`() {
        assertEquals(LightKey.WheelClick, LightKeys.fromScanCode(WHEEL_CLICK, "gpio-keys"))
        assertEquals(LightKey.Focus, LightKeys.fromScanCode(FOCUS, "gpio-keys"))
        assertEquals(LightKey.Camera, LightKeys.fromScanCode(CAMERA, "gpio-keys"))
    }

    /**
     * The bug this port fixes. The name is the kernel's, and the devicetree decides how it is
     * spelled; an exact match against one spelling means the wheel click never arrives at all on
     * a build that chose another.
     */
    @Test
    fun `the button device is matched however the devicetree spells it`() {
        listOf("gpio-keys", "gpio_keys", "gpio-keys-wheel", "GPIO-KEYS").forEach { name ->
            assertEquals(
                "$name did not resolve the wheel click",
                LightKey.WheelClick,
                LightKeys.fromScanCode(WHEEL_CLICK, name),
            )
        }
    }

    /**
     * The other half of why the gating is per scancode. These are ordinary keyboard codes
     * underneath — 19 is `r`, 66 is F8 — so a device that is not the one that physically owns
     * the code gets nothing.
     */
    @Test
    fun `a paired keyboard drives nothing`() {
        listOf(WHEEL_UP, WHEEL_DOWN, WHEEL_CLICK, FOCUS, CAMERA).forEach { code ->
            assertNull(
                "scancode $code was accepted from a keyboard",
                LightKeys.fromScanCode(code, "Magic Keyboard"),
            )
        }
    }

    @Test
    fun `neither device may claim the other's codes`() {
        assertNull(LightKeys.fromScanCode(WHEEL_UP, "gpio-keys"))
        assertNull(LightKeys.fromScanCode(WHEEL_CLICK, "Pixart pat9126ja"))
        assertNull(LightKeys.fromScanCode(CAMERA, "Pixart pat9126ja"))
    }

    @Test
    fun `an unknown scancode is not one of ours`() {
        assertNull(LightKeys.fromScanCode(1, "gpio-keys"))
        assertNull(LightKeys.fromScanCode(0, "Pixart pat9126ja"))
    }
}
