package uz.yalla.sipphone.core.result

fun Int?.or0(): Int = this ?: 0
fun Long?.or0L(): Long = this ?: 0L
fun Boolean?.orFalse(): Boolean = this ?: false
fun Boolean?.orTrue(): Boolean = this ?: true
