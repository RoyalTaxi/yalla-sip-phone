package uz.yalla.sipphone.di

import org.koin.dsl.module
import uz.yalla.sipphone.data.auth.MockAuthRepository
import uz.yalla.sipphone.domain.AuthRepository

val authModule = module {
    single<AuthRepository> { MockAuthRepository() }
}
