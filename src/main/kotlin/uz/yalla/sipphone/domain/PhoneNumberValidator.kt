package uz.yalla.sipphone.domain

object PhoneNumberValidator {
    private val VALID_DIAL_PATTERN = Regex("^[+]?[0-9*#]{1,20}$")

    fun validate(number: String): Result<String> {
        val sanitized = number.trim()

        if (sanitized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number is empty"))
        }

        if (sanitized.any { it.code < 32 }) {
            return Result.failure(IllegalArgumentException("Phone number contains control characters"))
        }

        if (!VALID_DIAL_PATTERN.matches(sanitized)) {
            return Result.failure(IllegalArgumentException("Invalid phone number format"))
        }

        return Result.success(sanitized)
    }
}
