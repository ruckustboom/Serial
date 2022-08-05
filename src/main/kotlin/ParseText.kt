package serial

import java.io.Reader

public interface CharCursor : DataCursor {
    public val line: Int
    public val lineStart: Int
    public val current: Char
}

public class CharCursorException(
    public val offset: Int,
    public val line: Int,
    public val column: Int,
    public val character: Char,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found <$character>/${character.code} at $offset ($line:$column))", cause)

// Some common helpers

public fun Reader.toCursor(): CharCursor = ReaderCursor(this).apply { next() }
public fun String.toCursor(): CharCursor = StringCursor(this).apply { next() }

public inline fun <T> CharCursor.parse(consumeAll: Boolean = true, parse: CharCursor.() -> T): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> Reader.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: CharCursor.() -> T,
): T = try {
    toCursor().parse(consumeAll, parse)
} finally {
    if (closeWhenDone) close()
}

public inline fun <T> String.parse(consumeAll: Boolean = true, parse: CharCursor.() -> T): T =
    toCursor().parse(consumeAll, parse)

public fun <S : DataCursor> S.tokenizeToChar(parseToken: S.() -> Char): CharCursor = CharTokenizer(this, parseToken)

public fun CharCursor.crash(message: String, cause: Throwable? = null): Nothing =
    throw CharCursorException(offset, line, offset - lineStart + 1, current, message, cause)

public inline fun CharCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun CharCursor.readIf(predicate: (Char) -> Boolean): Boolean = if (!isEndOfInput && predicate(current)) {
    next()
    true
} else false

public inline fun CharCursor.readWhile(predicate: (Char) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun CharCursor.skipWhitespace(): Int = readWhile(Char::isWhitespace)

public fun CharCursor.readOptionalChar(char: Char, ignoreCase: Boolean = false): Boolean =
    readIf { it.equals(char, ignoreCase) }

public fun CharCursor.readRequiredChar(char: Char, ignoreCase: Boolean = false): Unit =
    ensure(readOptionalChar(char, ignoreCase)) { "Expected: $char" }

public fun CharCursor.readLiteral(literal: String, ignoreCase: Boolean = false): Unit =
    literal.forEach { readRequiredChar(it, ignoreCase) }

// Capturing

public class CapturingCharCursor<out S : CharCursor>(public val base: S) : CharCursor by base {
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

public fun <S : CharCursor> CapturingCharCursor<S>.capture(literal: String): Unit = literal.forEach(::capture)

public inline fun <S : CharCursor> S.capturing(action: CapturingCharCursor<S>.() -> Unit): String =
    CapturingCharCursor(this).apply(action).getCaptured()

public inline fun <S : CharCursor> CapturingCharCursor<S>.notCapturing(action: S.() -> Unit): Unit = base.action()

public inline fun CharCursor.captureWhile(predicate: (Char) -> Boolean): String = capturing { readWhile(predicate) }

public fun CharCursor.captureCount(count: Int): String = capturing { repeat(count) { next() } }

// Implementation

private abstract class CharCursorBase : CharCursor {
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

private class StringCursor(private val string: String) : CharCursorBase() {
    override val current get() = if (offset in string.indices) string[offset] else '\u0000'
    override val isEndOfInput get() = offset >= string.length

    override fun next() = advance()
}

private class ReaderCursor(private val reader: Reader) : CharCursorBase() {
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

private class CharTokenizer<S : DataCursor>(private val base: S, private val parse: S.() -> Char) : CharCursorBase() {
    override var current = '\u0000'
    override val isEndOfInput get() = base.isEndOfInput

    override fun next() {
        advance()
        current = base.parse()
    }
}
