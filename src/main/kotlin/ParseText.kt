package serial

import java.io.Reader

@DslMarker
public annotation class TextCursorMarker

@TextCursorMarker
public interface TextCursor {
    public val offset: Int
    public val line: Int
    public val lineStart: Int
    public val current: Char
    public val isEndOfInput: Boolean
    public fun next()
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

public fun Reader.initParse(): TextCursor = ReaderTextCursor(this).apply { next() }
public fun String.initParse(): TextCursor = StringTextCursor(this).apply { next() }

public inline fun <T> TextCursor.parse(consumeAll: Boolean = true, parse: TextCursor.() -> T): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> Reader.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: TextCursor.() -> T,
): T = with(initParse()) { parse(consumeAll, parse).also { if (closeWhenDone) close() } }

public inline fun <T> String.parse(
    consumeAll: Boolean = true,
    parse: TextCursor.() -> T,
): T = with(initParse()) { parse(consumeAll, parse) }

public inline fun <T> TextCursor.parseMultiple(
    parse: TextCursor.() -> T,
): List<T> = buildList { while (!isEndOfInput) add(parse()) }

public inline fun <T> Reader.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: TextCursor.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse).also { if (closeWhenDone) close() } }

public inline fun <T> String.parseMultiple(
    parse: TextCursor.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse) }

public fun TextCursor.crash(message: String, cause: Throwable? = null): Nothing =
    throw TextParseException(offset, line, offset - lineStart + 1, current, message, cause)

public inline fun TextCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun TextCursor.readIf(predicate: (Char) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun TextCursor.readWhile(predicate: (Char) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun TextCursor.skipWhitespace(): Int = readWhile(Char::isWhitespace)

public fun TextCursor.readOptionalChar(char: Char, ignoreCase: Boolean = false): Boolean =
    readIf { it.equals(char, ignoreCase) }

public fun TextCursor.readRequiredChar(char: Char, ignoreCase: Boolean = false): Unit =
    ensure(readOptionalChar(char, ignoreCase)) { "Expected: $char" }

public fun TextCursor.readLiteral(literal: String, ignoreCase: Boolean = false): Unit =
    literal.forEach { readRequiredChar(it, ignoreCase) }

// Capturing

public class CapturingTextCursor<out S : TextCursor>(public val base: S) : TextCursor by base {
    private val data = StringBuilder()

    override fun next() {
        capture(current)
        base.next()
    }

    public fun capture(char: Char) {
        data.append(char)
    }

    public fun getCaptured(): String = data.toString()
}

public fun <S : TextCursor> CapturingTextCursor<S>.capture(literal: String): Unit = literal.forEach(::capture)

public inline fun <S : TextCursor> S.capturing(action: CapturingTextCursor<S>.() -> Unit): String =
    CapturingTextCursor(this).apply(action).getCaptured()

public inline fun <S : TextCursor> CapturingTextCursor<S>.notCapturing(action: S.() -> Unit): Unit = base.action()

public inline fun TextCursor.captureWhile(predicate: (Char) -> Boolean): String = capturing { readWhile(predicate) }

public fun TextCursor.captureCount(count: Int): String = capturing { repeat(count) { next() } }

// Implementation

private abstract class TextCursorBase : TextCursor {
    final override var offset = -1
        private set
    final override var line = 1
        private set
    final override var lineStart = 0
        private set

    protected fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        // Check for newline
        if (current == '\n') {
            line++
            lineStart = offset
        }
        offset++
    }
}

private class StringTextCursor(private val string: String) : TextCursorBase() {
    override val current get() = if (offset in string.indices) string[offset] else '\u0000'
    override val isEndOfInput get() = offset >= string.length

    override fun next() = advance()
}

private class ReaderTextCursor(private val reader: Reader) : TextCursorBase() {
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
