package uz.yalla.sipphone.data.system

import kotlinx.coroutines.Dispatchers
import uz.yalla.sipphone.domain.system.DispatchersProvider

object StandardDispatchersProvider : DispatchersProvider {
    override val main = Dispatchers.Main
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
    override val unconfined = Dispatchers.Unconfined
}
