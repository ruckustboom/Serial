package serial

import java.util.stream.Stream

@DslMarker
public annotation class CursorDSL

@CursorDSL
public interface DataCursor {
    public val offset: Int
    public val isEndOfInput: Boolean
    public fun next()
}

public interface ObjectCursor<T> : DataCursor {
    public val current: T
}

// Exceptions

public class ObjectCursorException(
    public val offset: Int,
    public val value: Any?,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found <$value> at $offset)", cause)

public fun <T> ObjectCursor<T>.crash(message: String, cause: Throwable? = null): Nothing =
    throw ObjectCursorException(offset, current, message, cause)

public inline fun <T> ObjectCursor<T>.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

// Initialize

public fun <T> Iterable<T>.toCursor(): ObjectCursor<T> = iterator().toCursor()

public fun <T> Iterator<T>.toCursor(): ObjectCursor<T> = IteratorCursor(this).apply { next() }

public inline fun <T, R> ObjectCursor<T>.parse(consumeAll: Boolean = true, parse: ObjectCursor<T>.() -> R): R {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T, R> Iterator<T>.parse(consumeAll: Boolean = true, parse: ObjectCursor<T>.() -> R): R =
    toCursor().parse(consumeAll, parse)

public inline fun <T, R> Iterable<T>.parse(consumeAll: Boolean = true, parse: ObjectCursor<T>.() -> R): R =
    iterator().parse(consumeAll, parse)

public inline fun <T, R> Stream<T>.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: ObjectCursor<T>.() -> R,
): R = try {
    iterator().parse(consumeAll, parse)
} finally {
    if (closeWhenDone) close()
}

public fun <S : DataCursor, T> S.tokenize(parseToken: S.() -> T): ObjectCursor<T> =
    ObjectTokenizer(this, parseToken).apply { next() }

// Some common helpers

public fun <T> ObjectCursor<T>.read(): T = current.also { next() }

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

private class ObjectTokenizer<S : DataCursor, T>(
    private val base: S,
    private val parse: S.() -> T,
) : ObjectCursorBase<T>() {
    @Suppress("UNCHECKED_CAST")
    override var current: T = null as T
    override var isEndOfInput = false

    override fun next() {
        if (!isEndOfInput && base.isEndOfInput) isEndOfInput = true else {
            advance()
            current = base.parse()
        }
    }
}
