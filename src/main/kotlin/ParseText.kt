package serial

import java.io.Reader

public interface TextParseState {
    public val offset: Int
    public val line: Int
    public val lineStart: Int
    public val current: Char
    public val isEndOfInput: Boolean
    public fun next()
    public fun startCapture()
    public fun pauseCapture()
    public fun addToCapture(char: Char)
    public fun finishCapture(): String
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

public inline fun TextParseState.captureWhile(predicate: (Char) -> Boolean): String {
    startCapture()
    readWhile(predicate)
    return finishCapture()
}

public fun TextParseState.skipWhitespace(): Int = readWhile { it.isWhitespace() }

public fun TextParseState.readOptionalChar(char: Char, ignoreCase: Boolean = false): Boolean =
    readIf { it.equals(char, ignoreCase) }

public fun TextParseState.readRequiredChar(char: Char, ignoreCase: Boolean = false): Unit =
    ensure(readOptionalChar(char, ignoreCase)) { "Expected: $char" }

public fun TextParseState.readLiteral(literal: String, ignoreCase: Boolean = false) {
    for (char in literal) readRequiredChar(char, ignoreCase)
}

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
        if (isCapturing) addToCapture(current)
        // Check for newline
        if (current == '\n') {
            line++
            lineStart = offset
        }
        offset++
    }

    private val capture = StringBuilder()
    private var isCapturing = false

    final override fun startCapture() {
        isCapturing = true
    }

    final override fun pauseCapture() {
        isCapturing = false
    }

    final override fun addToCapture(char: Char) {
        capture.append(char)
    }

    final override fun finishCapture(): String {
        isCapturing = false
        val text = capture.toString()
        capture.setLength(0)
        return text
    }
}

private class StringTextParseState(private val string: String) : TextParseStateBase() {
    override val current = if (offset in string.indices) string[offset] else '\u0000'
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
