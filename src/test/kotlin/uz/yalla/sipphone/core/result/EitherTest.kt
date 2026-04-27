package uz.yalla.sipphone.core.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EitherTest {

    @Test
    fun `success holds value`() {
        val e: Either<String, Int> = Either.Success(42)
        assertEquals(42, e.getOrNull())
        assertNull(e.errorOrNull())
    }

    @Test
    fun `failure holds error`() {
        val e: Either<String, Int> = Either.Failure("boom")
        assertNull(e.getOrNull())
        assertEquals("boom", e.errorOrNull())
    }

    @Test
    fun `mapSuccess transforms value when success`() {
        val e: Either<String, Int> = Either.Success(2)
        assertEquals(4, e.mapSuccess { it * 2 }.getOrNull())
    }

    @Test
    fun `mapSuccess preserves failure`() {
        val e: Either<String, Int> = Either.Failure("err")
        val mapped: Either<String, Int> = e.mapSuccess { it * 2 }
        assertEquals("err", mapped.errorOrNull())
    }

    @Test
    fun `mapFailure transforms error`() {
        val e: Either<String, Int> = Either.Failure("err")
        val mapped: Either<Int, Int> = e.mapFailure { it.length }
        assertEquals(3, mapped.errorOrNull())
    }

    @Test
    fun `flatMapSuccess chains successes`() {
        val e: Either<String, Int> = Either.Success(2)
        val chained = e.flatMapSuccess { Either.Success(it + 1) }
        assertEquals(3, chained.getOrNull())
    }

    @Test
    fun `flatMapSuccess short-circuits on failure`() {
        val e: Either<String, Int> = Either.Failure("err")
        val chained: Either<String, Int> = e.flatMapSuccess { Either.Success(it + 1) }
        assertEquals("err", chained.errorOrNull())
    }

    @Test
    fun `onSuccess invokes only on success`() {
        var count = 0
        val a: Either<String, Int> = Either.Success(1)
        a.onSuccess { count++ }
        val b: Either<String, Int> = Either.Failure("e")
        b.onSuccess { count++ }
        assertEquals(1, count)
    }

    @Test
    fun `onFailure invokes only on failure`() {
        var count = 0
        val a: Either<String, Int> = Either.Failure("e")
        a.onFailure { count++ }
        val b: Either<String, Int> = Either.Success(1)
        b.onFailure { count++ }
        assertEquals(1, count)
    }

    @Test
    fun `fold returns result from matching arm`() {
        val s: Either<String, Int> = Either.Success(10)
        val sf: String = s.fold(onFailure = { "err" }, onSuccess = { "ok-$it" })
        assertEquals("ok-10", sf)
        val f: Either<String, Int> = Either.Failure("nope")
        val ff: String = f.fold(onFailure = { it }, onSuccess = { "ok" })
        assertEquals("nope", ff)
    }

    @Test
    fun `success and failure helpers wrap values`() {
        assertTrue(success(5) is Either.Success)
        assertTrue(failure("e") is Either.Failure)
    }
}
