package serial

import java.io.Reader
import java.io.StringReader
import java.util.stream.Stream

public interface ParseState<T> {
    public val offset: Int
    public val value: T
    public val isEndOfInput: Boolean
    public fun next()
    public fun startCapture()
    public fun pauseCapture()
    public fun addToCapture(value: T)
    public fun finishCapture(): List<T>
}

public class ParseException(
    public val offset: Int,
    public val value: Any?,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found <$value> at $offset)", cause)

// Some common helpers

public fun <T> Iterator<T>.initParse(): ParseState<T> = ParseStateImpl(iterator()).apply { next() }

public inline fun <T, R> Iterator<T>.parse(
    consumeAll: Boolean = true,
    parse: ParseState<T>.() -> R,
): R = with(initParse()) {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $value")
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
    throw ParseException(offset, value, message, cause)

public inline fun <T> ParseState<T>.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun <T> ParseState<T>.readIf(predicate: (T) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(value)) {
        next()
        true
    } else false

public inline fun <T> ParseState<T>.readWhile(predicate: (T) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(value)) {
        next()
        count++
    }
    return count
}

public inline fun <T> ParseState<T>.captureWhile(predicate: (T) -> Boolean): List<T> {
    startCapture()
    readWhile(predicate)
    return finishCapture()
}

public fun <T> ParseState<T>.readOptionalValue(value: T): Boolean = readIf { it == value }

public fun <T> ParseState<T>.readRequiredValue(value: T): Unit =
    ensure(readOptionalValue(value)) { "Expected: $value" }

public fun <T> ParseState<T>.readLiteral(literal: List<T>): Unit =
    literal.forEach(::readRequiredValue)

// Implementation

private class ParseStateImpl<T>(private val stream: Iterator<T>) : ParseState<T> {
    override var offset = -1
    override var isEndOfInput = false

    @Suppress("UNCHECKED_CAST")
    override var value: T = null as T

    // Read

    override fun next() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) addToCapture(value)
        offset++
        if (stream.hasNext()) {
            value = stream.next()
        } else {
            @Suppress("UNCHECKED_CAST")
            value = null as T
            isEndOfInput = true
        }
    }

    // Capture

    private val capture = mutableListOf<T>()
    private var isCapturing = false

    override fun startCapture() {
        isCapturing = true
    }

    override fun pauseCapture() {
        isCapturing = false
    }

    override fun addToCapture(value: T) {
        capture += value
    }

    override fun finishCapture(): List<T> {
        isCapturing = false
        return capture.truncate()
    }
}

private fun <T> MutableList<T>.truncate(): List<T> {
    val copy = toList()
    clear()
    return copy
}
