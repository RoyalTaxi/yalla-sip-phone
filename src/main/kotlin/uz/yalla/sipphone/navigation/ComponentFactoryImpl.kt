package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import org.koin.core.Koin
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.RegistrationEngine
import uz.yalla.sipphone.feature.dialer.DialerComponent
import uz.yalla.sipphone.feature.registration.RegistrationComponent

class ComponentFactoryImpl(private val koin: Koin) : ComponentFactory {

    override fun createRegistration(
        context: ComponentContext,
        onRegistered: () -> Unit,
    ): RegistrationComponent = RegistrationComponent(
        componentContext = context,
        sipEngine = koin.get<RegistrationEngine>(),
        appSettings = koin.get<AppSettings>(),
        onRegistered = onRegistered,
    )

    override fun createDialer(
        context: ComponentContext,
        onDisconnected: () -> Unit,
    ): DialerComponent = DialerComponent(
        componentContext = context,
        registrationEngine = koin.get<RegistrationEngine>(),
        callEngine = koin.get<CallEngine>(),
        onDisconnected = onDisconnected,
    )
}
