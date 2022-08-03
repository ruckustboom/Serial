package serial

import java.io.Reader

public interface TextParseState {
    public val offset: Int
    public val line: Int
    public val lineStart: Int
    public val current: Char
    public val isEndOfInput: Boolean
    public val isCapturing: Boolean
    public fun next()
    public fun startCapturing()
    public fun stopCapturing()
    public fun capture(char: Char)
    public fun getCaptured(): String
    public fun purgeCaptured()
}

public class TextParseException(
    public val offset: Int,
    public val line: Int,
    public val column: Int,
    public val character: Char,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found <$character>/${character.code} at $offset ($line:$column))", cause)

// Some common helpers

public fun Reader.initParse(): TextParseState = ReaderTextParseState(this).apply { next() }
public fun String.initParse(): TextParseState = StringTextParseState(this).apply { next() }

public inline fun <T> TextParseState.parse(consumeAll: Boolean = true, parse: TextParseState.() -> T): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> Reader.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: TextParseState.() -> T,
): T = with(initParse()) { parse(consumeAll, parse).also { if (closeWhenDone) close() } }

public inline fun <T> String.parse(
    consumeAll: Boolean = true,
    parse: TextParseState.() -> T,
): T = with(initParse()) { parse(consumeAll, parse) }

public inline fun <T> TextParseState.parseMultiple(
    parse: TextParseState.() -> T,
): List<T> = buildList { while (!isEndOfInput) add(parse()) }

public inline fun <T> Reader.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: TextParseState.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse).also { if (closeWhenDone) close() } }

public inline fun <T> String.parseMultiple(
    parse: TextParseState.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse) }

public fun TextParseState.crash(message: String, cause: Throwable? = null): Nothing =
    throw TextParseException(offset, line, offset - lineStart + 1, current, message, cause)

public inline fun TextParseState.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun TextParseState.readIf(predicate: (Char) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun TextParseState.readWhile(predicate: (Char) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun TextParseState.finishCapturing(): String {
    ensure(isCapturing) { "Not currently capturing" }
    stopCapturing()
    val result = getCaptured()
    purgeCaptured()
    return result
}

public inline fun TextParseState.capturing(action: TextParseState.() -> Unit): String {
    ensure(!isCapturing) { "Already capturing" }
    startCapturing()
    action()
    return finishCapturing()
}

public inline fun TextParseState.notCapturing(action: TextParseState.() -> Unit) {
    ensure(isCapturing) { "Not currently capturing" }
    stopCapturing()
    action()
    startCapturing()
}

public inline fun TextParseState.captureWhile(predicate: (Char) -> Boolean): String =
    capturing { readWhile(predicate) }

public fun TextParseState.captureCount(count: Int): String =
    capturing { repeat(count) { next() } }

public fun TextParseState.capture(literal: String): Unit = literal.forEach(::capture)

public fun TextParseState.skipWhitespace(): Int = readWhile(Char::isWhitespace)

public fun TextParseState.readOptionalChar(char: Char, ignoreCase: Boolean = false): Boolean =
    readIf { it.equals(char, ignoreCase) }

public fun TextParseState.readRequiredChar(char: Char, ignoreCase: Boolean = false): Unit =
    ensure(readOptionalChar(char, ignoreCase)) { "Expected: $char" }

public fun TextParseState.readLiteral(literal: String, ignoreCase: Boolean = false): Unit =
    literal.forEach { readRequiredChar(it, ignoreCase) }

// Implementation

private abstract class TextParseStateBase : TextParseState {
    final override var offset = -1
        private set
    final override var line = 1
        private set
    final override var lineStart = 0
        private set

    protected fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) capture(current)
        // Check for newline
        if (current == '\n') {
            line++
            lineStart = offset
        }
        offset++
    }

    private val currentCapture = StringBuilder()
    override var isCapturing = false

    final override fun startCapturing() {
        isCapturing = true
    }

    final override fun stopCapturing() {
        isCapturing = false
    }

    final override fun capture(char: Char) {
        currentCapture.append(char)
    }

    override fun getCaptured() = currentCapture.toString()

    override fun purgeCaptured() {
        currentCapture.setLength(0)
    }
}

private class StringTextParseState(private val string: String) : TextParseStateBase() {
    override val current get() = if (offset in string.indices) string[offset] else '\u0000'
    override val isEndOfInput get() = offset >= string.length

    override fun next() = advance()
}

private class ReaderTextParseState(private val reader: Reader) : TextParseStateBase() {
    override var current = '\u0000'
    override var isEndOfInput = false

    override fun next() {
        advance()
        val next = reader.read()
        if (next >= 0) {
            current = next.toChar()
        } else {
            current = '\u0000'
            isEndOfInput = true
        }
    }
}
