package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import uz.yalla.sipphone.feature.dialer.DialerComponent
import uz.yalla.sipphone.feature.registration.RegistrationComponent

class RootComponent(
    componentContext: ComponentContext,
    private val factory: ComponentFactory,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Screen>()

    val childStack: Value<ChildStack<Screen, Child>> = childStack(
        source = navigation,
        serializer = Screen.serializer(),
        initialConfiguration = Screen.Registration,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private fun createChild(screen: Screen, context: ComponentContext): Child =
        when (screen) {
            is Screen.Registration -> Child.Registration(
                factory.createRegistration(context) { navigation.pushNew(Screen.Dialer) }
            )
            is Screen.Dialer -> Child.Dialer(
                factory.createDialer(context) { navigation.pop() }
            )
        }

    sealed interface Child {
        data class Registration(val component: RegistrationComponent) : Child
        data class Dialer(val component: DialerComponent) : Child
    }
}
