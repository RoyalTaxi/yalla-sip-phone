package uz.yalla.sipphone.data.auth.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import uz.yalla.sipphone.data.auth.remote.service.AuthService
import uz.yalla.sipphone.data.auth.repository.AuthRepositoryImpl
import uz.yalla.sipphone.domain.auth.repository.AuthRepository

object AuthDataModule {
    private val serviceModule = module {
        single { AuthService(client = get()) }
    }
    private val repositoryModule = module {
        single<AuthRepository> {
            AuthRepositoryImpl(
                service = get(),
                sessionPreferences = get(),
                ioDispatcher = get(named(IO_DISPATCHER)),
            )
        }
    }
    val modules = listOf(serviceModule, repositoryModule)

    const val IO_DISPATCHER = "io"
}
