package uz.yalla.sipphone.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import kotlinx.coroutines.flow.MutableStateFlow
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.update.UpdateRelease
import uz.yalla.sipphone.domain.update.UpdateState
import uz.yalla.sipphone.feature.login.LoginScreen
import uz.yalla.sipphone.feature.main.toolbar.AgentStatusButton
import uz.yalla.sipphone.feature.main.toolbar.CallActions
import uz.yalla.sipphone.feature.main.toolbar.PhoneField
import uz.yalla.sipphone.feature.main.toolbar.SettingsPanel
import uz.yalla.sipphone.feature.main.toolbar.SipChipRow
import uz.yalla.sipphone.feature.main.toolbar.ToolbarContent
import uz.yalla.sipphone.feature.main.update.UpdateDialog
import uz.yalla.sipphone.ui.component.SplashScreen
import uz.yalla.sipphone.ui.component.YallaSegmentedControl
import uz.yalla.sipphone.ui.component.YallaTooltip
import uz.yalla.sipphone.ui.theme.LocalYallaColors

/**
 * Central list of every composable that has showcase cases, in the order they appear in the
 * sidebar. Add new entries here — nothing else needs wiring.
 */
fun buildCatalog(): List<ComponentEntry> = listOf(
    interopLayersEntry(),  // ← tooltip / dropdown / settings layered over heavyweight
    loginScreenEntry(),
    toolbarContentEntry(),
    settingsPanelEntry(),
    sipChipRowEntry(),
    agentStatusButtonEntry(),
    callActionsEntry(),
    phoneFieldEntry(),
    updateDialogEntry(),
    splashScreenEntry(),
    yallaTooltipEntry(),
    yallaSegmentedControlEntry(),
)

// ========== Interop Layers — render above real JCEF ==========
//
// Purpose: validate that the tooltip (TooltipHost), AgentStatusButton dropdown, and
// SettingsPanel all render correctly above a real live JCEF browser — the exact z-order
// situation the real app has.
//
// JCEF is lazy-initialised when this case mounts (2–3s one-time cost). It's disposed when
// the case unmounts, so navigating away and back to this case will re-init. Uses a separate
// debug port from the real app so running both at once doesn't collide.
//
// What this verifies:
//   - AgentStatusButton dropdown (Popup via compose.layers.type=WINDOW) → should render
//     above JCEF (native popup window).
//   - SettingsPanel (same mechanism) → should render above.
//   - YallaTooltip (in-window via TooltipHost) → the real test. If the tooltip hides behind
//     the Chromium canvas when it extends into JCEF territory, we need a Popup variant.

@Composable
private fun InteropLayersPreview() {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    var settingsVisible by remember { mutableStateOf(false) }
    var agentStatus by remember { mutableStateOf(AgentStatus.READY) }

    // Fresh JcefManager instance scoped to this case. Initialised on Dispatchers.IO so the
    // 2–3s native setup doesn't block the UI thread; disposed via DisposableEffect.
    val jcefManager = remember { uz.yalla.sipphone.data.jcef.JcefManager() }
    var browser by remember { mutableStateOf<org.cef.browser.CefBrowser?>(null) }
    var initError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // 9223 so showcase can run alongside the real app's :9222.
                jcefManager.initialize(debugPort = 9223)
            }
            // Distinctive gradient + text so you can tell the JCEF area apart from Compose at
            // a glance. about:blank would be white and less obviously "a webview."
            val html = """
                <html><body style='margin:0;background:linear-gradient(135deg,#2a1a6a,#4a2a8a);
                color:white;font-family:-apple-system,sans-serif;display:flex;
                align-items:center;justify-content:center;height:100vh'>
                <div style='text-align:center'>
                  <h1 style='margin:0;font-size:48px'>REAL JCEF (Chromium)</h1>
                  <p style='opacity:0.7;font-size:14px;margin-top:16px'>Hover SIP chips, open the dropdown, or open the settings panel.</p>
                  <p style='opacity:0.5;font-size:12px'>All three should render above this webview area.</p>
                </div></body></html>
            """.trimIndent()
            val dataUrl = "data:text/html;charset=utf-8," + java.net.URLEncoder.encode(html, "UTF-8").replace("+", "%20")
            browser = jcefManager.createBrowser(dataUrl)
        }.onFailure { initError = it.message ?: "JCEF init failed" }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { jcefManager.shutdown() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // JCEF browser OR a loading/error placeholder that looks like JCEF.
        when {
            initError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp)
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("JCEF init failed: $initError", color = colors.destructive)
                }
            }
            browser == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp)
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Initializing JCEF… (first run pulls ~200 MB Chromium)", color = colors.textSubtle)
                }
            }
            else -> {
                // JcefManager.createBrowser already calls createImmediately() internally
                // (fixes the blank-until-resize bug on macOS, see JcefManager.kt).
                androidx.compose.ui.awt.SwingPanel(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp),
                    factory = { browser!!.uiComponent },
                )
            }
        }
        // Top "toolbar" strip — tooltip / dropdown / settings panel triggers live here.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(colors.backgroundBase),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                // Dropdown opens into the heavyweight area below.
                AgentStatusButton(
                    currentStatus = agentStatus,
                    onStatusSelected = { agentStatus = it },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                // Tooltip anchors here — its popup extends into the heavyweight area.
                SipChipRow(
                    accounts = accountsMixed(),
                    activeCallAccountId = null,
                    onChipClick = {},
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                // Settings panel slides from the right edge, overlapping the heavyweight area.
                Box(
                    Modifier
                        .background(colors.brandPrimary, tokens.shapeSmall)
                        .clickable { settingsVisible = !settingsVisible }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Open settings", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
                }
            }
        }
        SettingsPanel(
            visible = settingsVisible,
            isDarkTheme = true,
            locale = "uz",
            agentInfo = sampleAgent,
            onThemeToggle = {},
            onLocaleChange = {},
            onLogout = { settingsVisible = false },
            onDismiss = { settingsVisible = false },
        )
    }
}

private fun interopLayersEntry() = ComponentEntry(
    name = "Interop / Heavyweight",
    cases = listOf(
        Case("Tooltip + Dropdown + SettingsPanel over fake JCEF") {
            Frame { InteropLayersPreview() }
        },
    ),
)

// ========== LoginScreen ==========

private fun loginScreenEntry() = ComponentEntry(
    name = "LoginScreen",
    cases = listOf(
        Case("Idle (default)") {
            CenteredFrame { LoginScreen(remember { loginComponentFor() }) }
        },
        Case("After wrong password") {
            val component = remember {
                loginComponentFor().also { it.login("wrong") /* async — state will flip */ }
            }
            CenteredFrame { LoginScreen(component) }
        },
    ),
)

// ========== ToolbarContent ==========

@Composable
private fun ToolbarPreview(state: CallState = CallState.Idle, accounts: List<uz.yalla.sipphone.domain.SipAccount> = accountsConnected()) {
    Frame {
        ToolbarContent(
            component = remember { toolbarComponentFor(callState = state, accounts = accounts) },
            isDarkTheme = true,
            locale = "uz",
            agentInfo = sampleAgent,
            onThemeToggle = {},
            onLocaleChange = {},
            onLogout = {},
        )
    }
}

private fun toolbarContentEntry() = ComponentEntry(
    name = "ToolbarContent",
    cases = listOf(
        Case("Idle — one connected account") { ToolbarPreview() },
        Case("Idle — mixed account states") { ToolbarPreview(accounts = accountsMixed()) },
        Case("Incoming ringing call") { ToolbarPreview(state = incomingRinging) },
        Case("Active call") { ToolbarPreview(state = activeCall) },
        Case("Active — muted") { ToolbarPreview(state = activeMuted) },
        Case("Active — on hold") { ToolbarPreview(state = activeOnHold) },
        Case("All accounts disconnected") { ToolbarPreview(accounts = accountsAllDown()) },
    ),
)

// ========== SettingsPanel ==========

@Composable
private fun SettingsPanelPreview(
    locale: String = "uz",
    agentInfo: uz.yalla.sipphone.domain.AgentInfo? = sampleAgent,
) {
    // Local stateful wrapper so the X button and outside-click actually dismiss the panel
    // in the showcase (the real app wires these through ToolbarComponent). Click "Show panel"
    // to reopen it after dismissing.
    var visible by remember { mutableStateOf(true) }
    var isDark by remember { mutableStateOf(true) }
    var currentLocale by remember { mutableStateOf(locale) }
    val colors = LocalYallaColors.current
    Frame {
        Box(Modifier.fillMaxSize()) {
            if (!visible) {
                Box(
                    Modifier
                        .padding(top = 16.dp, end = 16.dp)
                        .align(Alignment.TopEnd)
                        .background(colors.brandPrimary, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable { visible = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Show panel", color = androidx.compose.ui.graphics.Color.White)
                }
            }
            SettingsPanel(
                visible = visible,
                isDarkTheme = isDark,
                locale = currentLocale,
                agentInfo = agentInfo,
                onThemeToggle = { isDark = !isDark },
                onLocaleChange = { currentLocale = it },
                onLogout = { visible = false },
                onDismiss = { visible = false },
            )
        }
    }
}

private fun settingsPanelEntry() = ComponentEntry(
    name = "SettingsPanel",
    cases = listOf(
        Case("With agent info — UZ") { SettingsPanelPreview(locale = "uz") },
        Case("Locale RU") { SettingsPanelPreview(locale = "ru") },
        Case("No agent info") { SettingsPanelPreview(agentInfo = null) },
    ),
)

// ========== SipChipRow ==========

private fun sipChipRowEntry() = ComponentEntry(
    name = "SipChipRow",
    cases = listOf(
        Case("All connected") {
            Frame(heightDp = 80) {
                SipChipRow(
                    accounts = accountsConnected(),
                    activeCallAccountId = null,
                    onChipClick = {},
                )
            }
        },
        Case("Mixed states") {
            Frame(heightDp = 80) {
                SipChipRow(
                    accounts = accountsMixed(),
                    activeCallAccountId = null,
                    onChipClick = {},
                )
            }
        },
        Case("Active call on account 1001") {
            Frame(heightDp = 80) {
                SipChipRow(
                    accounts = accountsMixed(),
                    activeCallAccountId = "1001@sip.example.uz",
                    onChipClick = {},
                )
            }
        },
        Case("All disconnected") {
            Frame(heightDp = 80) {
                SipChipRow(
                    accounts = accountsAllDown(),
                    activeCallAccountId = null,
                    onChipClick = {},
                )
            }
        },
    ),
)

// ========== AgentStatusButton ==========

private fun agentStatusButtonEntry() = ComponentEntry(
    name = "AgentStatusButton",
    cases = AgentStatus.entries.map { status ->
        Case(status.name) {
            Frame(heightDp = 120) {
                AgentStatusButton(currentStatus = status, onStatusSelected = {})
            }
        }
    },
)

// ========== CallActions ==========

private fun callActionsEntry() = ComponentEntry(
    name = "CallActions",
    cases = listOf(
        Case("Idle — empty number") {
            Frame(heightDp = 80) {
                CallActions(
                    callState = CallState.Idle,
                    phoneInputEmpty = true,
                    onCall = {}, onAnswer = {}, onReject = {},
                    onHangup = {}, onToggleMute = {}, onToggleHold = {},
                )
            }
        },
        Case("Idle — with number typed") {
            Frame(heightDp = 80) {
                CallActions(
                    callState = CallState.Idle,
                    phoneInputEmpty = false,
                    onCall = {}, onAnswer = {}, onReject = {},
                    onHangup = {}, onToggleMute = {}, onToggleHold = {},
                )
            }
        },
        Case("Incoming ringing") {
            Frame(heightDp = 80) {
                CallActions(
                    callState = incomingRinging,
                    phoneInputEmpty = true,
                    onCall = {}, onAnswer = {}, onReject = {},
                    onHangup = {}, onToggleMute = {}, onToggleHold = {},
                )
            }
        },
        Case("Active") {
            Frame(heightDp = 80) {
                CallActions(
                    callState = activeCall,
                    phoneInputEmpty = false,
                    onCall = {}, onAnswer = {}, onReject = {},
                    onHangup = {}, onToggleMute = {}, onToggleHold = {},
                )
            }
        },
        Case("Active — muted") {
            Frame(heightDp = 80) {
                CallActions(
                    callState = activeMuted,
                    phoneInputEmpty = false,
                    onCall = {}, onAnswer = {}, onReject = {},
                    onHangup = {}, onToggleMute = {}, onToggleHold = {},
                )
            }
        },
    ),
)

// ========== PhoneField ==========

private fun phoneFieldEntry() = ComponentEntry(
    name = "PhoneField",
    cases = listOf(
        Case("Empty") {
            Frame(heightDp = 100) {
                PhoneField(phoneNumber = "", onValueChange = {}, callState = CallState.Idle)
            }
        },
        Case("With number typed") {
            Frame(heightDp = 100) {
                PhoneField(phoneNumber = "+998901234567", onValueChange = {}, callState = CallState.Idle)
            }
        },
        Case("While ringing") {
            Frame(heightDp = 100) {
                PhoneField(phoneNumber = "+998901234567", onValueChange = {}, callState = incomingRinging)
            }
        },
        Case("While in active call") {
            Frame(heightDp = 100) {
                PhoneField(phoneNumber = "+998901234567", onValueChange = {}, callState = activeCall)
            }
        },
    ),
)

// ========== UpdateDialog ==========

private val fakeRelease = UpdateRelease(
    version = "1.2.3",
    releaseNotes = "• Fixed a nasty bug\n• Added some polish\n• Updated dependencies",
    installer = uz.yalla.sipphone.domain.update.UpdateInstaller(
        url = "https://downloads.example.uz/yalla-1.2.3.msi",
        sha256 = "deadbeef".repeat(8),
        size = 128 * 1024 * 1024,
    ),
    minSupportedVersion = "1.0.0",
)

@Composable
private fun UpdateDialogPreview(initial: UpdateState) {
    // Per-case state so each preview is independently dismissable. Shared flows would mean
    // clicking "Later" in one case would silently dismiss all the others too. "Show again"
    // button reopens the dialog after dismissal.
    val stateFlow = remember { MutableStateFlow(initial) }
    val callStateFlow = remember { MutableStateFlow<CallState>(CallState.Idle) }
    val dismissedFlow = remember { MutableStateFlow(false) }
    val dismissed by dismissedFlow.collectAsState()
    val colors = LocalYallaColors.current

    Frame(heightDp = 500) {
        Box(Modifier.fillMaxSize()) {
            if (dismissed) {
                Box(
                    Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.TopCenter)
                        .background(colors.brandPrimary, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable { dismissedFlow.value = false }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Show again", color = androidx.compose.ui.graphics.Color.White)
                }
            }
            UpdateDialog(
                stateFlow = stateFlow,
                callStateFlow = callStateFlow,
                dismissedFlow = dismissedFlow,
                onInstall = { /* showcase only — real app triggers MSI install */ },
                onDismiss = { dismissedFlow.value = true },
            )
        }
    }
}

private fun updateDialogEntry() = ComponentEntry(
    name = "UpdateDialog",
    cases = listOf(
        Case("Downloading 37%") {
            UpdateDialogPreview(UpdateState.Downloading(fakeRelease, bytesRead = 47_370_000, total = fakeRelease.installer.size))
        },
        Case("Verifying") { UpdateDialogPreview(UpdateState.Verifying(fakeRelease)) },
        Case("Ready to install") { UpdateDialogPreview(UpdateState.ReadyToInstall(fakeRelease, msiPath = "/tmp/fake.msi")) },
        Case("Installing") { UpdateDialogPreview(UpdateState.Installing(fakeRelease)) },
        Case("Failed — verify") { UpdateDialogPreview(UpdateState.Failed(UpdateState.Failed.Stage.VERIFY, "sha256 mismatch")) },
        Case("Failed — download") { UpdateDialogPreview(UpdateState.Failed(UpdateState.Failed.Stage.DOWNLOAD, "network error")) },
        Case("Failed — install") { UpdateDialogPreview(UpdateState.Failed(UpdateState.Failed.Stage.INSTALL, "msiexec exit 1603")) },
        Case("Failed — disk full") { UpdateDialogPreview(UpdateState.Failed(UpdateState.Failed.Stage.DISK_FULL, "insufficient space")) },
    ),
)

// ========== SplashScreen ==========

private fun splashScreenEntry() = ComponentEntry(
    name = "SplashScreen",
    cases = listOf(
        Case("Default") { Frame(heightDp = 600) { SplashScreen() } },
    ),
)

// ========== YallaTooltip ==========

private fun yallaTooltipEntry() = ComponentEntry(
    name = "YallaTooltip",
    cases = listOf(
        Case("Hover the button") {
            val colors = LocalYallaColors.current
            Frame(heightDp = 200) {
                Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    YallaTooltip(
                        tooltip = {
                            Column {
                                Text("Hello", color = colors.textBase)
                                Text("This is a tooltip", color = colors.textSubtle)
                            }
                        },
                    ) {
                        Box(
                            Modifier
                                .height(36.dp)
                                .padding(horizontal = 16.dp)
                                .background(colors.brandPrimary),
                            contentAlignment = Alignment.Center,
                        ) { Text("Hover me", color = androidx.compose.ui.graphics.Color.White) }
                    }
                }
            }
        },
    ),
)

// ========== YallaSegmentedControl ==========

private fun yallaSegmentedControlEntry() = ComponentEntry(
    name = "YallaSegmentedControl",
    cases = listOf(
        Case("Selected: first") {
            Frame(heightDp = 100) {
                SegmentedDemo(initial = 0)
            }
        },
        Case("Selected: second") {
            Frame(heightDp = 100) {
                SegmentedDemo(initial = 1)
            }
        },
    ),
)

@Composable
private fun SegmentedDemo(initial: Int) {
    var selected by remember { mutableStateOf(initial) }
    YallaSegmentedControl(
        selectedIndex = selected,
        onSelect = { selected = it },
        first = { Text("UZ") },
        second = { Text("RU") },
    )
}

// ========== Helpers ==========

/** Full-bleed frame that fills the preview pane. */
@Composable
private fun Frame(heightDp: Int? = null, content: @Composable () -> Unit) {
    val colors = LocalYallaColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { if (heightDp != null) it.height(heightDp.dp) else it }
            .background(colors.backgroundBase)
            .padding(16.dp),
    ) {
        content()
    }
}

/** Center-aligned frame for screens (Login / Splash). */
@Composable
private fun CenteredFrame(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        content()
    }
}
