package serial

import java.io.Reader

public interface CharCursor : DataCursor {
    public val line: Long
    public val lineStart: Long
    public val current: Char

    public interface Handler : DataCursor.Handler {
        public fun update(value: Char)
    }
}

// Exceptions

public class CharCursorException(
    public val offset: Long,
    public val line: Long,
    public val column: Long,
    public val character: Char,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found <$character>/${character.code} at $offset ($line:$column))", cause)

public fun CharCursor.crash(message: String, cause: Throwable? = null): Nothing =
    throw CharCursorException(offset, line, lineOffset, current, message, cause)

public inline fun CharCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

// Initialize

public fun String.toCursor(): CharCursor = StringCursor(this)
public fun CharArray.toCursor(): CharCursor = CharArrayCursor(this)
public fun CharIterator.toCursor(): CharCursor = CharIteratorSource(this).toCursor()
public fun Reader.toCursor(): CharCursor = ReaderSource(this).toCursor()
public fun DataCursor.Source<CharCursor.Handler>.toCursor(): CharCursor =
    StatefulCharCursor(this)

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

public fun <S : DataCursor> S.tokenizeToChar(parseToken: S.() -> Char): CharCursor =
    StatefulCharCursor(CharTokenizer(this, parseToken))

// Some common helpers

public fun CharCursor.read(): Char = current.also { advance() }

public fun CharCursor.readCount(count: Int): Unit = repeat(count) { read() }

public inline fun CharCursor.readIf(predicate: (Char) -> Boolean): Boolean = if (!isEndOfInput && predicate(current)) {
    advance()
    true
} else false

public inline fun CharCursor.readWhile(predicate: (Char) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        advance()
        count++
    }
    return count
}

public fun CharCursor.readOptionalChar(char: Char, ignoreCase: Boolean = false): Boolean =
    readIf { it.equals(char, ignoreCase) }

public fun CharCursor.readRequiredChar(char: Char, ignoreCase: Boolean = false): Unit =
    ensure(readOptionalChar(char, ignoreCase)) { "Expected: $char" }

public fun CharCursor.readLiteral(literal: String, ignoreCase: Boolean = false): Unit =
    literal.forEach { readRequiredChar(it, ignoreCase) }

public fun CharCursor.readWhileWhitespace(includeLineBreaks: Boolean = true): Int =
    readWhile { it.isWhitespace() && (includeLineBreaks || it != '\n') }

public val CharCursor.lineOffset: Long get() = offset - lineStart

// Capturing

public class CapturingCharCursor<out S : CharCursor>(public val base: S) : CharCursor by base {
    private val data = StringBuilder()

    override fun advance() {
        capture(current)
        base.advance()
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

public fun CharCursor.captureCount(count: Int): String = capturing { readCount(count) }

public fun CharCursor.captureStringLiteral(
    open: Char = '"',
    close: Char = open,
    includeDelimiter: Boolean = false,
    escape: Char = '\\',
): String {
    ensure(close != escape) { "Ambiguous termination vs escape" }
    readRequiredChar(open)
    val string = capturing {
        if (includeDelimiter) capture(open)
        while (current != close) {
            ensure(current >= '\u0020') { "Invalid character" }
            if (current == escape) {
                notCapturing {
                    advance()
                    this@capturing.capture(
                        when (current) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'b' -> '\b'
                            'f' -> '\u000c'
                            'u' -> String(CharArray(4) {
                                advance()
                                ensure(current.isHexDigit()) { "Invalid hex digit" }
                                current
                            }).toInt(16).toChar()

                            else -> current
                        }
                    )
                    advance()
                }
            } else {
                advance()
            }
        }
        if (includeDelimiter) capture(close)
    }
    readRequiredChar(close)
    return string
}

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

// Conversion

public fun CharCursor.toReader(): Reader = CharCursorReader(this)
public inline fun <R> CharCursor.asReader(action: Reader.() -> R): R = toReader().use(action)

// Implementation

private abstract class CharCursorBase : CharCursor {
    final override var offset = 0L
        private set
    final override var line = 0L
        private set
    final override var lineStart = 0L
        private set

    override fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        // Check for newline
        if (current == '\n') {
            line++
            lineStart = offset + 1
        }
        offset++
    }
}

private class StringCursor(private val string: String) : CharCursorBase() {
    override val current get() = if (offset in string.indices) string[offset.toInt()] else '\u0000'
    override val isEndOfInput get() = offset >= string.length
}

private class CharArrayCursor(private val array: CharArray) : CharCursorBase() {
    override val current get() = if (offset in array.indices) array[offset.toInt()] else '\u0000'
    override val isEndOfInput get() = offset >= array.size
}

private class StatefulCharCursor(
    private val source: DataCursor.Source<CharCursor.Handler>,
) : CharCursorBase() {
    override var current: Char = '\u0000'
        private set
    override var isEndOfInput = false
        private set

    private val handler = object : CharCursor.Handler {
        override fun update(value: Char) {
            current = value
        }

        override fun eoi() {
            isEndOfInput = true
            current = '\u0000'
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

private class ReaderSource(
    private val reader: Reader,
) : DataCursor.Source<CharCursor.Handler> {
    override fun fetch(handler: CharCursor.Handler) {
        val next = reader.read()
        if (next < 0) handler.eoi()
        else handler.update(next.toChar())
    }
}

private class CharIteratorSource(
    private val iter: CharIterator
) : DataCursor.Source<CharCursor.Handler> {
    override fun fetch(handler: CharCursor.Handler) {
        if (iter.hasNext()) handler.update(iter.nextChar())
        else handler.eoi()
    }
}

private class CharTokenizer<S : DataCursor>(
    private val base: S,
    private val parse: S.() -> Char,
) : DataCursor.Source<CharCursor.Handler> {
    override fun fetch(handler: CharCursor.Handler) {
        if (base.isEndOfInput) handler.eoi()
        else handler.update(base.parse())
    }
}

private class CharCursorReader(private val cursor: CharCursor) : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (cursor.isEndOfInput) return -1
        val start = cursor.offset.toInt()
        for (i in 0..<len) {
            cbuf[off + len] = cursor.read()
            if (cursor.isEndOfInput) break
        }
        return cursor.offset.toInt() - start
    }

    override fun close() = Unit
}
