package uz.yalla.sipphone.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

class AppTokensTest {

    private val tokens = AppTokens()

    @Test
    fun `alpha tokens are in ascending order`() {
        assertTrue(tokens.alphaSubtle < tokens.alphaMuted)
        assertTrue(tokens.alphaMuted < tokens.alphaLight)
        assertTrue(tokens.alphaLight < tokens.alphaBorder)
        assertTrue(tokens.alphaBorder < tokens.alphaMedium)
        assertTrue(tokens.alphaMedium < tokens.alphaFocus)
    }

    @Test
    fun `all alpha tokens are between 0 and 1`() {
        val alphas = listOf(
            tokens.alphaSubtle, tokens.alphaMuted, tokens.alphaLight,
            tokens.alphaBorder, tokens.alphaMedium, tokens.alphaFocus,
        )
        alphas.forEach { alpha ->
            assertTrue(alpha in 0f..1f, "Alpha $alpha out of range")
        }
    }

    @Test
    fun `typography sizes are in ascending order`() {
        assertTrue(tokens.textXs < tokens.textSm)
        assertTrue(tokens.textSm < tokens.textBase)
        assertTrue(tokens.textBase < tokens.textMd)
        assertTrue(tokens.textMd < tokens.textLg)
        assertTrue(tokens.textLg < tokens.textXl)
        assertTrue(tokens.textXl < tokens.textTitle)
    }
}
