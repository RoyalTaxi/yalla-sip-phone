package uz.yalla.sipphone.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module
import uz.yalla.sipphone.core.auth.SessionExpiredSignal
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.network.createHttpClient
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.MultiplatformConfigPreferences
import uz.yalla.sipphone.core.prefs.MultiplatformSessionPreferences
import uz.yalla.sipphone.core.prefs.MultiplatformUserPreferences
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.core.prefs.UserPreferences
import uz.yalla.sipphone.data.agent.InMemoryAgentStatusRepository
import uz.yalla.sipphone.data.auth.di.AuthDataModule
import uz.yalla.sipphone.data.jcef.bridge.BridgeSecurity
import uz.yalla.sipphone.data.jcef.bridge.JcefWebPanelBridge
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.keys.KeyShortcutRegistry
import uz.yalla.sipphone.data.pjsip.account.PjsipSipAccountManager
import uz.yalla.sipphone.data.pjsip.engine.PjsipEngine
import uz.yalla.sipphone.data.system.StandardDispatchersProvider
import uz.yalla.sipphone.data.update.api.UpdateApi
import uz.yalla.sipphone.data.update.api.asContract as asApiContract
import uz.yalla.sipphone.data.update.downloader.UpdateDownloader
import uz.yalla.sipphone.data.update.downloader.asContract as asDownloaderContract
import uz.yalla.sipphone.data.update.install.MsiBootstrapperInstaller
import uz.yalla.sipphone.data.update.install.asContract as asInstallerContract
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.data.update.storage.UpdatePaths
import uz.yalla.sipphone.data.workstation.bridge.AgentStatusBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.CallEventBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.SipConnectionBridgeEmitter
import uz.yalla.sipphone.domain.BuildVersion
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.auth.usecase.LoginUseCase
import uz.yalla.sipphone.domain.auth.usecase.LogoutUseCase
import uz.yalla.sipphone.domain.auth.usecase.ManualConnectUseCase
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.panel.WebPanelBridge
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipStackLifecycle
import uz.yalla.sipphone.domain.system.DispatchersProvider
import uz.yalla.sipphone.domain.update.UpdateChannel
import uz.yalla.sipphone.feature.auth.di.AuthModule
import uz.yalla.sipphone.feature.workstation.sideeffect.CallSideEffects
import uz.yalla.sipphone.feature.workstation.sideeffect.NotificationService
import uz.yalla.sipphone.feature.workstation.sideeffect.RingtonePlayer
import uz.yalla.sipphone.navigation.ComponentFactory
import uz.yalla.sipphone.navigation.ComponentFactoryImpl

const val IO_DISPATCHER = "io"

private val coreModule = module {
    single<DispatchersProvider> { StandardDispatchersProvider }
    single(named(IO_DISPATCHER)) { Dispatchers.IO }

    single<SessionPreferences> { MultiplatformSessionPreferences() }
    single<UserPreferences> { MultiplatformUserPreferences() }
    single<ConfigPreferences> { MultiplatformConfigPreferences() }

    single { SessionStore() }
    single { SessionExpiredSignal() }

    single<HttpClient> {
        val signal: SessionExpiredSignal = get()
        createHttpClient(
            sessionPrefs = get(),
            configPrefs = get(),
            onSessionExpired = { signal.signal() },
        )
    }
}

private val sipModule = module {
    single { PjsipEngine() }
    single<SipStackLifecycle> { get<PjsipEngine>() }
    single<CallEngine> { get<PjsipEngine>() }
    single<SipAccountManager> {
        val engine: PjsipEngine = get()
        PjsipSipAccountManager(
            accountManager = engine.accountManager,
            callEngine = engine,
            pjDispatcher = engine.pjDispatcher,
        )
    }
}

private val agentModule = module {
    single<AgentStatusRepository> { InMemoryAgentStatusRepository() }
}

private val jcefModule = module {
    single { JcefManager() }
    single { BridgeSecurity() }
    single { KeyShortcutRegistry() }
    single { BridgeEventEmitter(keyRegistry = get()) }
    single<WebPanelBridge> {
        JcefWebPanelBridge(
            jcefManager = get(),
            eventEmitter = get(),
            security = get(),
            keyRegistry = get(),
            callEngine = get(),
            sipAccountManager = get(),
            agentStatusRepository = get(),
        )
    }
}

private val authUseCaseModule = module {
    factory { LoginUseCase(authRepository = get(), sipAccountManager = get(), sessionStore = get()) }
    factory { ManualConnectUseCase(sipAccountManager = get(), sessionStore = get()) }
    factory {
        LogoutUseCase(
            authRepository = get(),
            sipAccountManager = get(),
            sessionStore = get(),
            sessionPreferences = get(),
        )
    }
}

private val updateModule = module {
    single { UpdatePaths() }
    single { UpdateApi(client = get(), baseUrlProvider = { get<ConfigPreferences>().current().backendUrl }) }
    single { UpdateDownloader(client = get(), paths = get()) }
    single { MsiBootstrapperInstaller() }

    single {
        val callEngine: CallEngine = get()
        val configPrefs: ConfigPreferences = get()
        val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("update"))
        UpdateManager(
            scope = updateScope,
            api = get<UpdateApi>().asApiContract(),
            downloader = get<UpdateDownloader>().asDownloaderContract(),
            installer = get<MsiBootstrapperInstaller>().asInstallerContract(),
            paths = get(),
            callState = callEngine.callState,
            currentVersion = BuildVersion.CURRENT,
            channelProvider = { UpdateChannel.fromValue(configPrefs.current().updateChannel) },
            installIdProvider = { configPrefs.current().installId },
        )
    }
}

private val workstationModule = module {
    factory { RingtonePlayer() }
    factory { NotificationService() }
    factory { CallSideEffects(ringtone = get(), notifications = get()) }
    factory { CallEventBridgeEmitter(callEngine = get(), eventEmitter = get()) }
    factory { SipConnectionBridgeEmitter(sipAccountManager = get(), eventEmitter = get()) }
    factory { AgentStatusBridgeEmitter(agentStatusRepository = get(), eventEmitter = get()) }
}

private val navigationModule = module {
    single<ComponentFactory> {
        ComponentFactoryImpl(
            loginUseCase = get(),
            manualConnectUseCase = get(),
            logoutUseCase = get(),
            callEngine = get(),
            sipAccountManager = get(),
            agentStatusRepository = get(),
            webPanelBridge = get(),
            jcefManager = get(),
            updateManager = get(),
            userPreferences = get(),
            configPreferences = get(),
            callSideEffectsFactory = { get() },
            callEventEmitterFactory = { get() },
            sipConnectionEmitterFactory = { get() },
            agentStatusEmitterFactory = { get() },
        )
    }
}

val appModule = module {
    includes(
        coreModule,
        sipModule,
        agentModule,
        jcefModule,
        authUseCaseModule,
        updateModule,
        workstationModule,
        navigationModule,
    )
    AuthDataModule.modules.forEach { includes(it) }
    AuthModule.modules.forEach { includes(it) }
}
