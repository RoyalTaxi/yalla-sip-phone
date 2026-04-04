package uz.yalla.sipphone.util

object PhoneNumberMasker {
    fun mask(number: String): String {
        if (number.length <= 1) return "*".repeat(number.length)
        if (number.length == 2) return "*${number.last()}"

        val lastTwo = number.takeLast(2)
        val masked = "*".repeat(number.length - 2) + lastTwo
        return masked
    }
}
