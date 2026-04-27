package uz.yalla.sipphone.core.result

sealed interface Either<out L, out R> {
    data class Success<R>(val value: R) : Either<Nothing, R>
    data class Failure<L>(val error: L) : Either<L, Nothing>
}

inline fun <L, R> Either<L, R>.onSuccess(block: (R) -> Unit): Either<L, R> = also {
    if (this is Either.Success) block(value)
}

inline fun <L, R> Either<L, R>.onFailure(block: (L) -> Unit): Either<L, R> = also {
    if (this is Either.Failure) block(error)
}

inline fun <L, R, T> Either<L, R>.mapSuccess(block: (R) -> T): Either<L, T> = when (this) {
    is Either.Success -> Either.Success(block(value))
    is Either.Failure -> this
}

inline fun <L, R, M> Either<L, R>.mapFailure(block: (L) -> M): Either<M, R> = when (this) {
    is Either.Success -> this
    is Either.Failure -> Either.Failure(block(error))
}

inline fun <L, R, T> Either<L, R>.flatMapSuccess(block: (R) -> Either<L, T>): Either<L, T> = when (this) {
    is Either.Success -> block(value)
    is Either.Failure -> this
}

inline fun <L, R, T> Either<L, R>.fold(
    onFailure: (L) -> T,
    onSuccess: (R) -> T,
): T = when (this) {
    is Either.Success -> onSuccess(value)
    is Either.Failure -> onFailure(error)
}

fun <L, R> Either<L, R>.getOrNull(): R? = (this as? Either.Success)?.value
fun <L, R> Either<L, R>.errorOrNull(): L? = (this as? Either.Failure)?.error

fun <R> success(value: R): Either<Nothing, R> = Either.Success(value)
fun <L> failure(error: L): Either<L, Nothing> = Either.Failure(error)
