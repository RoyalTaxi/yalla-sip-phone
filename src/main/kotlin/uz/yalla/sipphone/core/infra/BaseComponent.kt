package uz.yalla.sipphone.core.infra

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

abstract class BaseComponent<S : Any, E : Any>(
    componentContext: ComponentContext,
    initialState: S,
) : ComponentContext by componentContext, ContainerHost<S, E> {

    protected val scope: CoroutineScope = coroutineScope()

    final override val container: Container<S, E> = scope.container(initialState)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    suspend fun <T> withLoading(block: suspend () -> T): T {
        _loading.value = true
        try {
            return block()
        } finally {
            _loading.value = false
        }
    }
}
