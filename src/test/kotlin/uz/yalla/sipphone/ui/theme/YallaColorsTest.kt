package uz.yalla.sipphone.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

class YallaColorsTest {

    @Test
    fun `dark theme surface hierarchy has increasing brightness`() {
        val dark = YallaColors.Dark
        val baseBrightness = brightness(dark.backgroundBase)
        val secondaryBrightness = brightness(dark.backgroundSecondary)
        val tertiaryBrightness = brightness(dark.backgroundTertiary)

        assertTrue(
            baseBrightness < secondaryBrightness,
            "backgroundBase ($baseBrightness) should be darker than backgroundSecondary ($secondaryBrightness)",
        )
        assertTrue(
            secondaryBrightness < tertiaryBrightness,
            "backgroundSecondary ($secondaryBrightness) should be darker than backgroundTertiary ($tertiaryBrightness)",
        )
    }

    @Test
    fun `light theme surface hierarchy has decreasing brightness`() {
        val light = YallaColors.Light
        val baseBrightness = brightness(light.backgroundBase)
        val secondaryBrightness = brightness(light.backgroundSecondary)
        val tertiaryBrightness = brightness(light.backgroundTertiary)

        assertTrue(
            baseBrightness > secondaryBrightness,
            "Light backgroundBase should be brighter than backgroundSecondary",
        )
        assertTrue(
            secondaryBrightness > tertiaryBrightness,
            "Light backgroundSecondary should be brighter than backgroundTertiary",
        )
    }

    @Test
    fun `brandPrimary has same value in both themes`() {
        assertTrue(YallaColors.Light.brandPrimary == YallaColors.Dark.brandPrimary)
    }

    private fun brightness(color: androidx.compose.ui.graphics.Color): Float =
        0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
}
