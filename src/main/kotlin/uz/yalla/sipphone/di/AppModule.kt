package uz.yalla.sipphone.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import uz.yalla.sipphone.data.pjsip.PjsipEngine
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.RegistrationEngine
import uz.yalla.sipphone.domain.SipStackLifecycle

val appModule = module {
    single { PjsipEngine() }
    single<SipStackLifecycle> { get<PjsipEngine>() }
    single<RegistrationEngine> { get<PjsipEngine>() }
    single<CallEngine> { get<PjsipEngine>() }
    singleOf(::AppSettings)
}
