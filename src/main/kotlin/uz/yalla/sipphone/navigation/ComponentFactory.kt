package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import uz.yalla.sipphone.feature.dialer.DialerComponent
import uz.yalla.sipphone.feature.registration.RegistrationComponent

interface ComponentFactory {
    fun createRegistration(context: ComponentContext, onRegistered: () -> Unit): RegistrationComponent
    fun createDialer(context: ComponentContext, onDisconnected: () -> Unit): DialerComponent
}
