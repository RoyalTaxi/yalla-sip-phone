package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent
import uz.yalla.sipphone.feature.main.MainComponent

interface ComponentFactory {
    fun createAuth(context: ComponentContext): AuthComponent
    fun createMain(context: ComponentContext, session: Session, onLogout: () -> Unit): MainComponent
}
