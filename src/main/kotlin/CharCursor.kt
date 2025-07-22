package serial

import java.io.Reader

public interface CharCursor : DataCursor {
    public val line: Long
    public val lineStart: Long
    public val current: Char

    public interface Sink : DataCursor.Sink {
        public fun update(value: Char)
    }
}

// Exceptions

public class CharCursorException(
    public val offset: Long,
    public val line: Long,
    public val column: Long,
    public val character: Char,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

// Initialize

public fun String.toCursor(): CharCursor = StringCursor(this)
public fun CharArray.toCursor(): CharCursor = CharArrayCursor(this)
public fun CharIterator.toCursor(): CharCursor = CharIteratorSource(this).toCursor()
public fun Reader.toCursor(): CharCursor = ReaderSource(this).toCursor()
public fun DataCursor.Source<CharCursor.Sink>.toCursor(): CharCursor = SourceCharCursor(this)

public inline fun <T> CharCursor.parse(parse: CharCursor.() -> T): T = parse()
public inline fun <T> Reader.parse(parse: CharCursor.() -> T): T = toCursor().parse(parse)
public inline fun <T> String.parse(parse: CharCursor.() -> T): T = toCursor().parse(parse)
public fun <S : DataCursor> S.tokenizeToChar(parseToken: S.() -> Char): CharCursor =
    SourceCharCursor(CharTokenizer(this, parseToken))

// Some common helpers

public fun CharCursor.read(): Char = current.also { advance() }

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
        capture(base.read())
    }

    public fun capture(char: Char) {
        data.append(char)
    }

    public fun getCaptured(): String = data.toString()
}

public fun <S : CharCursor> CapturingCharCursor<S>.capture(literal: String): Unit = literal.forEach(::capture)

public inline fun <S : CharCursor> S.capturing(action: CapturingCharCursor<S>.() -> Unit): String =
    CapturingCharCursor(this).apply(action).getCaptured()

public inline fun <S : CharCursor, T> CapturingCharCursor<S>.notCapturing(action: S.() -> T): T = base.action()

public inline fun CharCursor.captureWhile(predicate: (Char) -> Boolean): String = capturing { readWhile(predicate) }

public fun CharCursor.captureCount(count: Int): String = capturing { advanceBy(count) }

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
        while (!isEndOfInput && current != close) {
            ensure(current >= '\u0020') { "Invalid character" }
            if (current == escape) {
                capture(notCapturing {
                    advance()
                    when (val x = read()) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'b' -> '\b'
                        'f' -> '\u000c'

                        'u' -> String(CharArray(4) {
                            ensure(!isEndOfInput && current.isHexDigit()) { "Invalid hex digit" }
                            read()
                        }).toInt(16).toChar()

                        else -> x
                    }
                })
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

    override fun crash(message: String, cause: Throwable?): Nothing =
        throw CharCursorException(offset, line, lineOffset, current, message, cause)

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

private class SourceCharCursor(
    private val source: DataCursor.Source<CharCursor.Sink>,
) : CharCursorBase() {
    override var current: Char = '\u0000'
        private set
    override var isEndOfInput = false
        private set

    private val sink = object : CharCursor.Sink {
        override fun update(value: Char) {
            current = value
        }

        override fun eoi() {
            isEndOfInput = true
            current = '\u0000'
        }

        override fun crash(message: String, cause: Throwable?) =
            this@SourceCharCursor.crash(message, cause)
    }

    init {
        source.fetch(sink)
    }

    override fun advance() {
        super.advance()
        source.fetch(sink)
    }
}

private class ReaderSource(
    reader: Reader,
) : DataCursor.Source<CharCursor.Sink>, AutoCloseable {
    private var reader: Reader? = reader

    override fun fetch(into: CharCursor.Sink) {
        val reader = reader ?: into.crash("Reader already closed")
        val next = try {
            reader.read()
        } catch (e: Throwable) {
            into.crash("Failed to read from reader", e)
        }
        if (next < 0) into.eoi()
        else into.update(next.toChar())
    }

    override fun close() {
        reader?.close()
        reader = null
    }
}

private class CharIteratorSource(
    private val iter: CharIterator
) : DataCursor.Source<CharCursor.Sink> {
    override fun fetch(into: CharCursor.Sink) {
        if (iter.hasNext()) into.update(iter.nextChar())
        else into.eoi()
    }
}

private class CharTokenizer<S : DataCursor>(
    private val base: S,
    private val parse: S.() -> Char,
) : DataCursor.Source<CharCursor.Sink> {
    override fun fetch(into: CharCursor.Sink) {
        if (base.isEndOfInput) into.eoi()
        else into.update(base.parse())
    }
}

private class CharCursorReader(private val cursor: CharCursor) : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (cursor.isEndOfInput) return -1
        var i = 0
        while (i < len) {
            cbuf[off + i++] = cursor.read()
            if (cursor.isEndOfInput) break
        }
        return i
    }

    override fun close() = Unit
}
