package uz.yalla.sipphone.util

object PhoneNumberMasker {
    private val phoneParamKeys = setOf("number", "phone", "callerNumber")

    fun mask(number: String): String = when {
        number.length <= 1 -> "*".repeat(number.length)
        number.length == 2 -> "*${number.last()}"
        else -> "*".repeat(number.length - 2) + number.takeLast(2)
    }

    fun maskParams(params: Map<String, String>): Map<String, String> =
        params.mapValues { (key, value) -> if (key in phoneParamKeys) mask(value) else value }
}
