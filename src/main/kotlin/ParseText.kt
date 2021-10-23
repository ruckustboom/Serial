package serial

import java.io.Reader
import java.io.StringReader

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

public fun Reader.initParse(): TextParseState = TextParseStateImpl(this).apply { next() }

public inline fun <T> Reader.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: TextParseState.() -> T,
): T = with(initParse()) {
    val result = parse()
    if (closeWhenDone) close()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    result
}

public inline fun <T> String.parse(
    consumeAll: Boolean = true,
    parse: TextParseState.() -> T,
): T = StringReader(this).use { it.parse(consumeAll, true, parse) }

public inline fun <T> Reader.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: TextParseState.() -> T,
): List<T> = with(initParse()) {
    val results = mutableListOf<T>()
    while (!isEndOfInput) results += parse()
    if (closeWhenDone) close()
    results
}

public inline fun <T> String.parseMultiple(
    parse: TextParseState.() -> T,
): List<T> = StringReader(this).use { it.parseMultiple(true, parse) }

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

public fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

// Implementation

private class TextParseStateImpl(private val stream: Reader) : TextParseState {
    override var offset = -1
    override var line = 1
    override var lineStart = 0
    override var current = '\u0000'
    override var isEndOfInput = false

    // Read

    override fun next() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) addToCapture(current)
        // Check for newline
        if (current == '\n') {
            line++
            lineStart = offset
        }
        val next = stream.read()
        offset++
        if (next >= 0) {
            current = next.toChar()
        } else {
            current = '\u0000'
            isEndOfInput = true
        }
    }

    // Capture

    private val capture = StringBuilder()
    private var isCapturing = false

    override fun startCapture() {
        isCapturing = true
    }

    override fun pauseCapture() {
        isCapturing = false
    }

    override fun addToCapture(char: Char) {
        capture.append(char)
    }

    override fun finishCapture(): String {
        isCapturing = false
        return capture.truncate()
    }
}

private fun StringBuilder.truncate(): String {
    val text = toString()
    setLength(0)
    return text
}