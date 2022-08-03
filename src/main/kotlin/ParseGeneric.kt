package serial

import java.util.stream.Stream

@DslMarker
public annotation class CursorMarker

@CursorMarker
public interface Cursor<T> {
    public val offset: Int
    public val current: T
    public val isEndOfInput: Boolean
    public fun next()
}

public class ParseException(
    public val offset: Int,
    public val value: Any?,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found <$value> at $offset)", cause)

// Some common helpers

public fun <T> Iterator<T>.initParse(): Cursor<T> = IteratorCursor(this).apply { next() }

public inline fun <T, R> Iterator<T>.parse(
    consumeAll: Boolean = true,
    parse: Cursor<T>.() -> R,
): R = with(initParse()) {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    result
}

public inline fun <T, R> Iterable<T>.parse(
    consumeAll: Boolean = true,
    parse: Cursor<T>.() -> R,
): R = iterator().parse(consumeAll, parse)

public inline fun <T, R> Stream<T>.parse(
    consumeAll: Boolean = true,
    parse: Cursor<T>.() -> R,
): R = iterator().parse(consumeAll, parse)

public inline fun <T, R> Iterator<T>.parseMultiple(
    parse: Cursor<T>.() -> R,
): List<R> = with(initParse()) {
    val results = mutableListOf<R>()
    while (!isEndOfInput) results += parse()
    results
}

public inline fun <T, R> Iterable<T>.parseMultiple(
    parse: Cursor<T>.() -> R,
): List<R> = iterator().parseMultiple(parse)

public inline fun <T, R> Stream<T>.parseMultiple(
    parse: Cursor<T>.() -> R,
): List<R> = iterator().parseMultiple(parse)

public fun <T> Cursor<T>.crash(message: String, cause: Throwable? = null): Nothing =
    throw ParseException(offset, current, message, cause)

public inline fun <T> Cursor<T>.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun <T> Cursor<T>.readIf(predicate: (T) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun <T> Cursor<T>.readWhile(predicate: (T) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun <T> Cursor<T>.readOptionalValue(value: T): Boolean = readIf { it == value }

public fun <T> Cursor<T>.readRequiredValue(value: T): Unit = ensure(readOptionalValue(value)) { "Expected: $value" }

public fun <T> Cursor<T>.readLiteral(literal: List<T>): Unit = literal.forEach(::readRequiredValue)

// Capturing

public class CapturingCursor<T, out S : Cursor<T>>(public val base: S) : Cursor<T> by base {
    private val data = mutableListOf<T>()

    override fun next() {
        capture(current)
        base.next()
    }

    public fun capture(value: T) {
        data += value
    }

    public fun getCaptured(): List<T> = data.toList()
}

public fun <T, S : Cursor<T>> CapturingCursor<T, S>.capture(literal: List<T>): Unit = literal.forEach(::capture)

public inline fun <T, S : Cursor<T>> S.capturing(action: CapturingCursor<T, S>.() -> Unit): List<T> =
    CapturingCursor(this).apply(action).getCaptured()

public inline fun <T, S : Cursor<T>> CapturingCursor<T, S>.notCapturing(action: S.() -> Unit): Unit = base.action()

public inline fun <T> Cursor<T>.captureWhile(predicate: (T) -> Boolean): List<T> = capturing { readWhile(predicate) }

public fun <T> Cursor<T>.captureCount(count: Int): List<T> = capturing { repeat(count) { next() } }

// Implementation

private abstract class CursorBase<T> : Cursor<T> {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        offset++
    }
}

private class IteratorCursor<T>(private val iter: Iterator<T>) : CursorBase<T>() {
    override var isEndOfInput = false

    @Suppress("UNCHECKED_CAST")
    override var current: T = null as T

    // Read

    override fun next() {
        advance()
        if (iter.hasNext()) {
            current = iter.next()
        } else {
            @Suppress("UNCHECKED_CAST")
            current = null as T
            isEndOfInput = true
        }
    }
}
