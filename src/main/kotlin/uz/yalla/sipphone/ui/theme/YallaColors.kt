package uz.yalla.sipphone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class YallaColors(
    // Brand
    val brandPrimary: Color,
    val brandPrimaryDisabled: Color,
    val brandPrimaryText: Color,
    // Backgrounds
    val backgroundBase: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,
    // Text
    val textBase: Color,
    val textSubtle: Color,
    // Borders
    val borderDisabled: Color,
    val borderFilled: Color,
    // Error
    val errorText: Color,
    val errorIndicator: Color,
    // Buttons
    val buttonActive: Color,
    val buttonDisabled: Color,
    // Icons
    val iconDisabled: Color,
    val iconSubtle: Color,
    val iconRed: Color,
    // Accent
    val pinkSun: Color,
    // Call states
    val callReady: Color,
    val callIncoming: Color,
    val callMuted: Color,
    val callOffline: Color,
    val callWrapUp: Color,
) {
    companion object {
        val Light = YallaColors(
            brandPrimary = Color(0xFF562DF8),
            brandPrimaryDisabled = Color(0xFFC8CBFA),
            brandPrimaryText = Color(0xFF562DF8),
            backgroundBase = Color(0xFFFFFFFF),
            backgroundSecondary = Color(0xFFF7F7F7),
            backgroundTertiary = Color(0xFFE9EAEA),
            textBase = Color(0xFF101828),
            textSubtle = Color(0xFF98A2B3),
            borderDisabled = Color(0xFFE4E7EC),
            borderFilled = Color(0xFF101828),
            errorText = Color(0xFFF42500),
            errorIndicator = Color(0xFFF42500),
            buttonActive = Color(0xFF562DF8),
            buttonDisabled = Color(0xFFF7F7F7),
            iconDisabled = Color(0xFFC8CBFA),
            iconSubtle = Color(0xFF98A2B3),
            iconRed = Color(0xFFF42500),
            pinkSun = Color(0xFFFF234B),
            callReady = Color(0xFF2E7D32),
            callIncoming = Color(0xFFD97706),
            callMuted = Color(0xFFF42500),
            callOffline = Color(0xFF6B7280),
            callWrapUp = Color(0xFF7C3AED),
        )

        val Dark = YallaColors(
            brandPrimary = Color(0xFF562DF8),
            brandPrimaryDisabled = Color(0xFF2C2D34),
            brandPrimaryText = Color(0xFF8B6FFF),
            backgroundBase = Color(0xFF1A1A20),
            backgroundSecondary = Color(0xFF21222B),
            backgroundTertiary = Color(0xFF1D1D26),
            textBase = Color(0xFFFFFFFF),
            textSubtle = Color(0xFF747C8B),
            borderDisabled = Color(0xFF383843),
            borderFilled = Color(0xFFFFFFFF),
            errorText = Color(0xFFF42500),
            errorIndicator = Color(0xFFF42500),
            buttonActive = Color(0xFF562DF8),
            buttonDisabled = Color(0xFF2C2D34),
            iconDisabled = Color(0xFFC8CBFA),
            iconSubtle = Color(0xFF98A2B3),
            iconRed = Color(0xFFF42500),
            pinkSun = Color(0xFFFF234B),
            callReady = Color(0xFF66BB6A),
            callIncoming = Color(0xFFF59E0B),
            callMuted = Color(0xFFF42500),
            callOffline = Color(0xFF98A2B3),
            callWrapUp = Color(0xFF8B5CF6),
        )
    }
}

val LocalYallaColors = staticCompositionLocalOf { YallaColors.Light }
