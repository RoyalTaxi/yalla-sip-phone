package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import uz.yalla.sipphone.core.auth.SessionExpiredSignal
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.usecase.LogoutUseCase
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent
import uz.yalla.sipphone.feature.workstation.presentation.model.WorkstationComponent

class RootComponent(
    componentContext: ComponentContext,
    private val factory: ComponentFactory,
    private val sessionStore: SessionStore,
    private val sessionExpired: SessionExpiredSignal,
    private val logoutUseCase: LogoutUseCase,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Screen>()
    private val scope = coroutineScope()

    val childStack: Value<ChildStack<Screen, Child>> = childStack(
        source = navigation,
        serializer = Screen.serializer(),
        initialConfiguration = Screen.Auth,
        handleBackButton = false,
        childFactory = ::createChild,
    )

    init {
        scope.launch {
            sessionExpired.events.collect {
                // Idempotency guard — multiple 401s can land before logout completes; only
                // run the logout flow when there's actually a session to tear down.
                if (sessionStore.session.value == null) return@collect
                logoutUseCase()
                navigation.replaceAll(Screen.Auth)
            }
        }
    }

    private fun createChild(screen: Screen, context: ComponentContext): Child = when (screen) {
        Screen.Auth -> Child.Auth(factory.createAuth(context))
        Screen.Workstation -> {
            val session = sessionStore.session.value
            if (session == null) {
                navigation.replaceAll(Screen.Auth)
                Child.Auth(factory.createAuth(context))
            } else {
                Child.Workstation(factory.createWorkstation(context, session))
            }
        }
    }

    fun onAuthSuccess(@Suppress("UNUSED_PARAMETER") session: Session) {
        // SessionStore is already populated by LoginUseCase / ManualConnectUseCase before the
        // LoggedIn side-effect fires — RootComponent only navigates.
        navigation.replaceAll(Screen.Workstation)
    }

    fun onWorkstationLogout() {
        navigation.replaceAll(Screen.Auth)
    }

    sealed interface Child {
        data class Auth(val component: AuthComponent) : Child
        data class Workstation(val component: WorkstationComponent) : Child
    }
}
