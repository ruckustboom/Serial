package serial

import java.util.stream.Stream

public interface ParseState<T> {
    public val offset: Int
    public val current: T
    public val isEndOfInput: Boolean
    public fun next()
    public fun startCapturing()
    public fun stopCapturing()
    public fun capture(value: T)
    public fun getCaptured(): List<T>
    public fun purgeCaptured()
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

public fun <T> ParseState<T>.finishCapturing(): List<T> {
    stopCapturing()
    val result = getCaptured()
    purgeCaptured()
    return result
}

public inline fun <T> ParseState<T>.capturing(action: ParseState<T>.() -> Unit): List<T> {
    startCapturing()
    action()
    return finishCapturing()
}

public inline fun <T> ParseState<T>.captureWhile(predicate: (T) -> Boolean): List<T> =
    capturing { readWhile(predicate) }

public fun <T> ParseState<T>.captureCount(count: Int): List<T> =
    capturing { repeat(count) { next() } }

public fun <T> ParseState<T>.capture(literal: List<T>): Unit = literal.forEach(::capture)

public fun <T> ParseState<T>.readOptionalValue(value: T): Boolean = readIf { it == value }

public fun <T> ParseState<T>.readRequiredValue(value: T): Unit =
    ensure(readOptionalValue(value)) { "Expected: $value" }

public fun <T> ParseState<T>.readLiteral(literal: List<T>): Unit =
    literal.forEach(::readRequiredValue)

// Implementation

private abstract class ParseStateBase<T> : ParseState<T> {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) capture(current)
        offset++
    }

    private val currentCapture = mutableListOf<T>()
    private var isCapturing = false

    final override fun startCapturing() {
        isCapturing = true
    }

    final override fun stopCapturing() {
        isCapturing = false
    }

    final override fun capture(value: T) {
        currentCapture += value
    }

    override fun getCaptured() = currentCapture.toList()

    override fun purgeCaptured() = currentCapture.clear()
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
