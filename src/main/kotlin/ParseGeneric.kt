package serial

import java.util.stream.Stream

@DslMarker
public annotation class ObjectCursorMarker

@ObjectCursorMarker
public interface ObjectCursor<T> {
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

public fun <T> Iterator<T>.initParse(): ObjectCursor<T> = IteratorCursor(this).apply { next() }

public inline fun <T, R> Iterator<T>.parse(
    consumeAll: Boolean = true,
    parse: ObjectCursor<T>.() -> R,
): R = with(initParse()) {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    result
}

public inline fun <T, R> Iterable<T>.parse(
    consumeAll: Boolean = true,
    parse: ObjectCursor<T>.() -> R,
): R = iterator().parse(consumeAll, parse)

public inline fun <T, R> Stream<T>.parse(
    consumeAll: Boolean = true,
    parse: ObjectCursor<T>.() -> R,
): R = iterator().parse(consumeAll, parse)

public inline fun <T, R> Iterator<T>.parseMultiple(
    parse: ObjectCursor<T>.() -> R,
): List<R> = with(initParse()) {
    val results = mutableListOf<R>()
    while (!isEndOfInput) results += parse()
    results
}

public inline fun <T, R> Iterable<T>.parseMultiple(
    parse: ObjectCursor<T>.() -> R,
): List<R> = iterator().parseMultiple(parse)

public inline fun <T, R> Stream<T>.parseMultiple(
    parse: ObjectCursor<T>.() -> R,
): List<R> = iterator().parseMultiple(parse)

public fun <T> ObjectCursor<T>.crash(message: String, cause: Throwable? = null): Nothing =
    throw ParseException(offset, current, message, cause)

public inline fun <T> ObjectCursor<T>.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun <T> ObjectCursor<T>.readIf(predicate: (T) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun <T> ObjectCursor<T>.readWhile(predicate: (T) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun <T> ObjectCursor<T>.readOptionalValue(value: T): Boolean = readIf { it == value }

public fun <T> ObjectCursor<T>.readRequiredValue(value: T): Unit =
    ensure(readOptionalValue(value)) { "Expected: $value" }

public fun <T> ObjectCursor<T>.readLiteral(literal: List<T>): Unit = literal.forEach(::readRequiredValue)

// Capturing

public class CapturingObjectCursor<T, out S : ObjectCursor<T>>(public val base: S) : ObjectCursor<T> by base {
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

public fun <T, S : ObjectCursor<T>> CapturingObjectCursor<T, S>.capture(literal: List<T>): Unit =
    literal.forEach(::capture)

public inline fun <T, S : ObjectCursor<T>> S.capturing(action: CapturingObjectCursor<T, S>.() -> Unit): List<T> =
    CapturingObjectCursor(this).apply(action).getCaptured()

public inline fun <T, S : ObjectCursor<T>> CapturingObjectCursor<T, S>.notCapturing(action: S.() -> Unit): Unit =
    base.action()

public inline fun <T> ObjectCursor<T>.captureWhile(predicate: (T) -> Boolean): List<T> =
    capturing { readWhile(predicate) }

public fun <T> ObjectCursor<T>.captureCount(count: Int): List<T> = capturing { repeat(count) { next() } }

// Implementation

private abstract class ObjectCursorBase<T> : ObjectCursor<T> {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        offset++
    }
}

private class IteratorCursor<T>(private val iter: Iterator<T>) : ObjectCursorBase<T>() {
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
