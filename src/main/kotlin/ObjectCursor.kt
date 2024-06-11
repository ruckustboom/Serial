package serial

import java.util.stream.Stream

public interface ObjectCursor<T> : DataCursor {
    public val current: T

    public interface Handler<T> : DataCursor.Handler {
        public fun update(value: T)
    }
}

// Exceptions

public class ObjectCursorException(
    public val offset: Long,
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

public fun <T> Array<T>.toCursor(): ObjectCursor<T> = ObjectArrayCursor(this)
public fun <T> Iterable<T>.toCursor(): ObjectCursor<T> = iterator().toCursor()
public fun <T> Iterator<T>.toCursor(): ObjectCursor<T> = IteratorSource(this).toCursor()
public fun <T> DataCursor.Source<ObjectCursor.Handler<T>>.toCursor(): ObjectCursor<T> =
    StatefulObjectCursor(this)

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
    StatefulObjectCursor(ObjectTokenizer(this, parseToken))

// Some common helpers

public fun <T> ObjectCursor<T>.read(): T = current.also { advance() }

public fun <T> ObjectCursor<T>.readCount(count: Int): Unit = repeat(count) { read() }

public inline fun <T> ObjectCursor<T>.readIf(predicate: (T) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        advance()
        true
    } else false

public inline fun <T> ObjectCursor<T>.readWhile(predicate: (T) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        advance()
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

    override fun advance() {
        capture(current)
        base.advance()
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

public fun <T> ObjectCursor<T>.captureCount(count: Int): List<T> = capturing { readCount(count) }

// Conversion

public fun <T> ObjectCursor<T>.toIterator(): Iterator<T> = ObjectCursorIterator(this)
public inline fun <T, R> ObjectCursor<T>.asIterator(action: Iterator<T>.() -> R): R = toIterator().action()

// Implementation

private abstract class ObjectCursorBase<T> : ObjectCursor<T> {
    final override var offset = 0L
        private set

    override fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        offset++
    }
}

private class ObjectArrayCursor<T>(private val array: Array<T>) : ObjectCursorBase<T>() {
    @Suppress("UNCHECKED_CAST")
    override val current get() = if (offset in array.indices) array[offset.toInt()] else null as T
    override val isEndOfInput get() = offset >= array.size
}

private class StatefulObjectCursor<T>(
    private val source: DataCursor.Source<ObjectCursor.Handler<T>>,
) : ObjectCursorBase<T>() {
    @Suppress("UNCHECKED_CAST")
    override var current: T = null as T
        private set
    override var isEndOfInput = false
        private set

    private val handler = object : ObjectCursor.Handler<T> {
        override fun update(value: T) {
            current = value
        }

        override fun eoi() {
            isEndOfInput = true
            @Suppress("UNCHECKED_CAST")
            current = null as T
        }
    }

    init {
        source.fetch(handler)
    }

    override fun advance() {
        super.advance()
        source.fetch(handler)
    }
}

private class IteratorSource<T>(
    private val iter: Iterator<T>,
) : DataCursor.Source<ObjectCursor.Handler<T>> {
    override fun fetch(handler: ObjectCursor.Handler<T>) {
        if (iter.hasNext()) handler.update(iter.next())
        else handler.eoi()
    }
}

private class ObjectTokenizer<S : DataCursor, T>(
    private val base: S,
    private val parse: S.() -> T,
) : DataCursor.Source<ObjectCursor.Handler<T>> {
    override fun fetch(handler: ObjectCursor.Handler<T>) {
        if (base.isEndOfInput) handler.eoi()
        else handler.update(base.parse())
    }
}

private class ObjectCursorIterator<T>(private val cursor: ObjectCursor<T>) : Iterator<T> {
    override fun hasNext() = !cursor.isEndOfInput
    override fun next() = cursor.read()
}
