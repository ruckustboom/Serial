package serial

import java.util.stream.Stream

public interface ParseState<T> {
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

public fun <T> Iterator<T>.initParse(): ParseState<T> = IteratorParseState(this).apply { next() }

public inline fun <T, R> Iterator<T>.parse(
    consumeAll: Boolean = true,
    parse: ParseState<T>.() -> R,
): R = with(initParse()) {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    result
}

public inline fun <T, R> Iterable<T>.parse(
    consumeAll: Boolean = true,
    parse: ParseState<T>.() -> R,
): R = iterator().parse(consumeAll, parse)

public inline fun <T, R> Stream<T>.parse(
    consumeAll: Boolean = true,
    parse: ParseState<T>.() -> R,
): R = iterator().parse(consumeAll, parse)

public inline fun <T, R> Iterator<T>.parseMultiple(
    parse: ParseState<T>.() -> R,
): List<R> = with(initParse()) {
    val results = mutableListOf<R>()
    while (!isEndOfInput) results += parse()
    results
}

public inline fun <T, R> Iterable<T>.parseMultiple(
    parse: ParseState<T>.() -> R,
): List<R> = iterator().parseMultiple(parse)

public inline fun <T, R> Stream<T>.parseMultiple(
    parse: ParseState<T>.() -> R,
): List<R> = iterator().parseMultiple(parse)

public fun <T> ParseState<T>.crash(message: String, cause: Throwable? = null): Nothing =
    throw ParseException(offset, current, message, cause)

public inline fun <T> ParseState<T>.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun <T> ParseState<T>.readIf(predicate: (T) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun <T> ParseState<T>.readWhile(predicate: (T) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun <T> ParseState<T>.readOptionalValue(value: T): Boolean = readIf { it == value }

public fun <T> ParseState<T>.readRequiredValue(value: T): Unit =
    ensure(readOptionalValue(value)) { "Expected: $value" }

public fun <T> ParseState<T>.readLiteral(literal: List<T>): Unit =
    literal.forEach(::readRequiredValue)

// Capturing

public class CapturingParseState<T, out S : ParseState<T>>(
    public val base: S,
) : ParseState<T> by base {
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

public fun <T, S : ParseState<T>> CapturingParseState<T, S>.capture(literal: List<T>): Unit =
    literal.forEach(::capture)

public inline fun <T, S : ParseState<T>> S.capturing(
    action: CapturingParseState<T, S>.() -> Unit,
): List<T> = CapturingParseState(this).apply(action).getCaptured()

public inline fun <T, S : ParseState<T>> CapturingParseState<T, S>.notCapturing(
    action: S.(CapturingParseState<T, S>) -> Unit,
): Unit = base.action(this)

public inline fun <T> ParseState<T>.captureWhile(predicate: (T) -> Boolean): List<T> =
    capturing { readWhile(predicate) }

public fun <T> ParseState<T>.captureCount(count: Int): List<T> =
    capturing { repeat(count) { next() } }

// Implementation

private abstract class ParseStateBase<T> : ParseState<T> {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        offset++
    }
}

private class IteratorParseState<T>(private val iter: Iterator<T>) : ParseStateBase<T>() {
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
