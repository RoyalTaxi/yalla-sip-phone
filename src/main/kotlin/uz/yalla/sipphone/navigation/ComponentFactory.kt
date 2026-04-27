package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent
import uz.yalla.sipphone.feature.workstation.presentation.model.WorkstationComponent

interface ComponentFactory {
    fun createAuth(context: ComponentContext): AuthComponent
    fun createWorkstation(context: ComponentContext, session: Session): WorkstationComponent
}
