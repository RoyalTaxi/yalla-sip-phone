package uz.yalla.sipphone.feature.workstation.toolbar.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import uz.yalla.sipphone.domain.BuildVersion
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.ui.component.YallaSegmentedControl
import uz.yalla.sipphone.ui.component.hoverClickable
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun SettingsPanel(
    visible: Boolean,
    isDarkTheme: Boolean,
    locale: String,
    agentInfo: AgentInfo?,
    onThemeToggle: () -> Unit,
    onLocaleChange: (String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animState = remember { MutableTransitionState(false) }
    animState.targetState = visible

    val showPopup = visible || animState.currentState || !animState.isIdle
    if (!showPopup) return

    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = animState,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(tokens.animMedium, easing = LinearOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(tokens.animFast)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(tokens.animFast, easing = LinearOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(tokens.animFast)),
        ) {
            Column(
                modifier = Modifier
                    .width(tokens.settingsPanelWidth)
                    .fillMaxHeight()
                    .background(colors.backgroundSecondary)
                    .padding(tokens.spacingMd)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.spacingMd),
            ) {
                Header(strings.settingsTitle, onDismiss)
                if (agentInfo != null) AgentInfoCard(agentInfo)
                HorizontalDivider(color = colors.borderDefault)

                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingMdSm)) {
                    SettingsRow(label = strings.settingsTheme) {
                        ThemeSegment(isDarkTheme, onThemeToggle)
                    }
                    SettingsRow(label = strings.settingsLocale) {
                        LocaleSegment(locale, onLocaleChange)
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                    HorizontalDivider(color = colors.borderDefault)
                    LogoutButton(strings.settingsLogout, onLogout)
                }

                VersionLabel()
            }
        }
    }
}

@Composable
private fun AgentInfoCard(agentInfo: AgentInfo) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current

    val initials = remember(agentInfo.name) {
        agentInfo.name.split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifEmpty { "?" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundTertiary, tokens.shapeMedium)
            .padding(tokens.spacingMdSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacingMdSm),
    ) {
        Box(
            modifier = Modifier
                .size(tokens.iconButtonSizeLarge)
                .clip(CircleShape)
                .background(colors.brandPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, fontSize = tokens.textLg, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column {
            Text(agentInfo.name, fontSize = tokens.textLg, fontWeight = FontWeight.Medium, color = colors.textBase)
            Text("ID: ${agentInfo.id}", fontSize = tokens.textBase, color = colors.textSubtle)
        }
    }
}

@Composable
private fun Header(title: String, onDismiss: () -> Unit) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = tokens.textXl,
            fontWeight = FontWeight.SemiBold,
            color = colors.textBase,
        )
        Box(
            modifier = Modifier
                .size(tokens.settingsPanelCloseButton)
                .clip(tokens.shapeXs)
                .background(colors.backgroundTertiary, tokens.shapeXs)
                .hoverClickable(
                    hoverBackground = colors.borderDefault,
                    shape = tokens.shapeXs,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(tokens.settingsPanelCloseIcon),
                tint = colors.textBase,
            )
        }
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = tokens.textMd, color = colors.textBase)
        content()
    }
}

@Composable
private fun ThemeSegment(isDark: Boolean, onToggle: () -> Unit) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    YallaSegmentedControl(
        selectedIndex = if (isDark) 1 else 0,
        onSelect = { index ->
            val wantDark = index == 1
            if (wantDark != isDark) onToggle()
        },
        first = {
            Icon(
                imageVector = Icons.Filled.LightMode,
                contentDescription = null,
                modifier = Modifier.size(tokens.iconSmall),
                tint = if (!isDark) colors.brandPrimary else colors.textSubtle,
            )
        },
        second = {
            Icon(
                imageVector = Icons.Filled.DarkMode,
                contentDescription = null,
                modifier = Modifier.size(tokens.iconSmall),
                tint = if (isDark) colors.brandPrimary else colors.textSubtle,
            )
        },
    )
}

@Composable
private fun LocaleSegment(locale: String, onChange: (String) -> Unit) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    YallaSegmentedControl(
        selectedIndex = if (locale == LOCALE_RU) 1 else 0,
        onSelect = { index -> onChange(if (index == 0) LOCALE_UZ else LOCALE_RU) },
        first = {
            Text(
                text = "UZ",
                fontSize = tokens.textBase,
                fontWeight = FontWeight.Medium,
                color = if (locale == LOCALE_UZ) colors.brandPrimary else colors.textSubtle,
            )
        },
        second = {
            Text(
                text = "RU",
                fontSize = tokens.textBase,
                fontWeight = FontWeight.Medium,
                color = if (locale == LOCALE_RU) colors.brandPrimary else colors.textSubtle,
            )
        },
    )
}

@Composable
private fun LogoutButton(label: String, onLogout: () -> Unit) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapeSmall)
            .hoverClickable(
                hoverBackground = colors.destructive.copy(alpha = tokens.alphaSubtle),
                shape = tokens.shapeSmall,
                onClick = onLogout,
            )
            .padding(vertical = tokens.spacingSm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = tokens.textMd,
            fontWeight = FontWeight.Medium,
            color = colors.destructive,
        )
    }
}

@Composable
private fun ColumnScope.VersionLabel() {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Text(
        text = "v${BuildVersion.CURRENT}",
        fontSize = tokens.textXs,
        color = colors.textSubtle,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

private const val LOCALE_UZ = "uz"
private const val LOCALE_RU = "ru"
