package uz.yalla.sipphone.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.yalla.sipphone.ui.component.YallaSegmentedControl
import uz.yalla.sipphone.ui.theme.LocalYallaColors
import uz.yalla.sipphone.ui.theme.YallaSipPhoneTheme

/**
 * Sidebar (list of components and their cases) + live preview of the selected case.
 *
 * Top bar has theme and locale toggles so designers can see every case under both variants
 * without relaunching. The app never touches JCEF / pjsip / the network — it's the
 * composables-only sandbox the production app sits on top of.
 */
@Composable
fun ShowcaseApp() {
    var isDark by remember { mutableStateOf(true) }
    var locale by remember { mutableStateOf("uz") }

    val catalog = remember { buildCatalog() }
    val flatCases = remember(catalog) {
        catalog.flatMap { entry -> entry.cases.map { entry to it } }
    }
    var selected by remember {
        mutableStateOf(flatCases.firstOrNull()?.let { it.first to it.second })
    }

    YallaSipPhoneTheme(isDark = isDark, locale = locale) {
        val colors = LocalYallaColors.current
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundSecondary),
        ) {
            Sidebar(
                catalog = catalog,
                selected = selected,
                onSelect = { entry, case -> selected = entry to case },
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            )
            Divider(
                modifier = Modifier.fillMaxHeight().width(1.dp),
                color = colors.borderDefault,
            )
            PreviewPane(
                selected = selected,
                isDark = isDark,
                locale = locale,
                onThemeToggle = { isDark = !isDark },
                onLocaleChange = { locale = it },
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Sidebar(
    catalog: List<ComponentEntry>,
    selected: Pair<ComponentEntry, Case>?,
    onSelect: (ComponentEntry, Case) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYallaColors.current
    Column(modifier.background(colors.backgroundBase)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = "Showcase",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textBase,
            )
        }
        Divider(color = colors.borderDefault)

        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(catalog) { entry ->
                Column {
                    Text(
                        text = entry.name,
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    entry.cases.forEach { case ->
                        SidebarRow(
                            label = case.name,
                            isSelected = selected?.second === case,
                            onClick = { onSelect(entry, case) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalYallaColors.current
    val bg = if (isSelected) colors.brandPrimary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (isSelected) colors.brandPrimary else colors.textBase
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 13.sp)
    }
}

@Composable
private fun PreviewPane(
    selected: Pair<ComponentEntry, Case>?,
    isDark: Boolean,
    locale: String,
    onThemeToggle: () -> Unit,
    onLocaleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYallaColors.current
    Column(modifier.background(colors.backgroundSecondary)) {
        // Top bar with theme + locale toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.backgroundBase)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = selected?.first?.name ?: "—",
                    color = colors.textBase,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selected?.second?.name ?: "",
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                YallaSegmentedControl(
                    selectedIndex = if (locale == "ru") 1 else 0,
                    onSelect = { onLocaleChange(if (it == 0) "uz" else "ru") },
                    first = { Text("UZ", fontSize = 11.sp) },
                    second = { Text("RU", fontSize = 11.sp) },
                )
                Spacer(Modifier.width(12.dp))
                YallaSegmentedControl(
                    selectedIndex = if (isDark) 1 else 0,
                    onSelect = { onThemeToggle() },
                    first = { Text("Light", fontSize = 11.sp) },
                    second = { Text("Dark", fontSize = 11.sp) },
                )
            }
        }
        Divider(color = colors.borderDefault)

        // Preview area
        Box(modifier = Modifier.fillMaxSize().background(colors.backgroundSecondary)) {
            if (selected != null) {
                selected.second.content()
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a case from the sidebar", color = colors.textSubtle)
                }
            }
        }
    }
}
