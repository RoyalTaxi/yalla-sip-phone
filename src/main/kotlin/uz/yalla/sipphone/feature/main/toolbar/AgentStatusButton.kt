package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors
import uz.yalla.sipphone.ui.theme.YallaSipPhoneTheme
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

enum class DisplayAgentStatus { ONLINE, BUSY, OFFLINE }

fun AgentStatus.toDisplayStatus(): DisplayAgentStatus = when (this) {
    AgentStatus.READY -> DisplayAgentStatus.ONLINE
    AgentStatus.AWAY, AgentStatus.BREAK, AgentStatus.WRAP_UP -> DisplayAgentStatus.BUSY
    AgentStatus.OFFLINE -> DisplayAgentStatus.OFFLINE
}

fun DisplayAgentStatus.toAgentStatus(): AgentStatus = when (this) {
    DisplayAgentStatus.ONLINE -> AgentStatus.READY
    DisplayAgentStatus.BUSY -> AgentStatus.AWAY
    DisplayAgentStatus.OFFLINE -> AgentStatus.OFFLINE
}

@Composable
fun AgentStatusButton(
    currentStatus: AgentStatus,
    isDarkTheme: Boolean,
    locale: String,
    onStatusSelected: (AgentStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current
    val density = LocalDensity.current

    var showDropdown by remember { mutableStateOf(false) }
    val displayStatus = currentStatus.toDisplayStatus()

    var buttonWindowX by remember { mutableStateOf(0f) }
    var buttonWindowY by remember { mutableStateOf(0f) }
    var buttonHeight by remember { mutableStateOf(0f) }

    fun dotColor(status: DisplayAgentStatus): Color = when (status) {
        DisplayAgentStatus.ONLINE -> colors.brandPrimary
        DisplayAgentStatus.BUSY -> colors.statusWarning
        DisplayAgentStatus.OFFLINE -> colors.textSubtle
    }

    fun label(status: DisplayAgentStatus): String = when (status) {
        DisplayAgentStatus.ONLINE -> strings.agentStatusOnline
        DisplayAgentStatus.BUSY -> strings.agentStatusBusy
        DisplayAgentStatus.OFFLINE -> strings.agentStatusOffline
    }

    // Button
    Box(
        modifier = modifier
            .size(tokens.iconButtonSize)
            .clip(tokens.shapeSmall)
            .background(colors.backgroundSecondary)
            .pointerHoverIcon(PointerIcon.Hand)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                buttonWindowX = pos.x
                buttonWindowY = pos.y
                buttonHeight = coords.size.height.toFloat()
            }
            .clickable { showDropdown = true },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(tokens.indicatorDotLarge)
                .clip(CircleShape)
                .background(dotColor(displayStatus)),
        )
    }

    // Dropdown
    if (showDropdown) {
        val dropdownWidth = tokens.dropdownWidth
        val dropdownHeight = 130.dp

        val screenXDp: Dp
        val screenYDp: Dp
        with(density) {
            val awtWindow = java.awt.KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .activeWindow
            val windowScreenX = awtWindow?.locationOnScreen?.x ?: 0
            val windowScreenY = awtWindow?.locationOnScreen?.y ?: 0
            screenXDp = (windowScreenX + buttonWindowX).toDp()
            screenYDp = (windowScreenY + buttonWindowY + buttonHeight).toDp()
        }

        DialogWindow(
            onCloseRequest = { showDropdown = false },
            title = "",
            state = rememberDialogState(
                position = WindowPosition(screenXDp, screenYDp + tokens.spacingXs),
                size = DpSize(dropdownWidth, dropdownHeight),
            ),
            resizable = false,
            alwaysOnTop = true,
            undecorated = true,
            transparent = true,
        ) {
            DisposableEffect(Unit) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) {}
                    override fun windowLostFocus(e: WindowEvent?) { showDropdown = false }
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }

            YallaSipPhoneTheme(isDark = isDarkTheme, locale = locale) {
                val dc = LocalYallaColors.current
                val ds = LocalStrings.current
                val dt = LocalAppTokens.current

                Box(modifier = Modifier.size(dropdownWidth, dropdownHeight)) {
                    Column(
                        modifier = Modifier
                            .size(dropdownWidth, dropdownHeight)
                            .clip(dt.shapeMedium)
                            .background(dc.backgroundSecondary)
                            .padding(dt.spacingXs),
                    ) {
                        DisplayAgentStatus.entries.forEach { status ->
                            val isSelected = status == displayStatus
                            val statusDotColor = when (status) {
                                DisplayAgentStatus.ONLINE -> dc.brandPrimary
                                DisplayAgentStatus.BUSY -> dc.statusWarning
                                DisplayAgentStatus.OFFLINE -> dc.textSubtle
                            }
                            val statusLabel = when (status) {
                                DisplayAgentStatus.ONLINE -> ds.agentStatusOnline
                                DisplayAgentStatus.BUSY -> ds.agentStatusBusy
                                DisplayAgentStatus.OFFLINE -> ds.agentStatusOffline
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(dt.shapeSmall)
                                    .then(if (isSelected) Modifier.background(dc.backgroundTertiary) else Modifier)
                                    .clickable {
                                        onStatusSelected(status.toAgentStatus())
                                        showDropdown = false
                                    }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(horizontal = dt.spacingMdSm, vertical = dt.spacingSm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dt.spacingSm),
                            ) {
                                Box(Modifier.size(dt.indicatorDot).clip(CircleShape).background(statusDotColor))
                                Text(
                                    text = statusLabel,
                                    fontSize = dt.textMd,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = dc.textBase,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Text("\u2713", fontSize = dt.textMd, color = dc.brandPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
