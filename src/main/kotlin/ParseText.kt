package serial

import java.io.Reader

public interface TextParseState {
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

public fun TextParseState.skipWhitespace(): Int = readWhile(Char::isWhitespace)

public fun TextParseState.readOptionalChar(char: Char, ignoreCase: Boolean = false): Boolean =
    readIf { it.equals(char, ignoreCase) }

public fun TextParseState.readRequiredChar(char: Char, ignoreCase: Boolean = false): Unit =
    ensure(readOptionalChar(char, ignoreCase)) { "Expected: $char" }

public fun TextParseState.readLiteral(literal: String, ignoreCase: Boolean = false): Unit =
    literal.forEach { readRequiredChar(it, ignoreCase) }

// Capturing

public class TextCapturingParseState<out S : TextParseState>(
    public val base: S,
) : TextParseState by base {
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

public fun <S : TextParseState> TextCapturingParseState<S>.capture(literal: String): Unit =
    literal.forEach(::capture)

public inline fun <S : TextParseState> S.capturing(
    action: TextCapturingParseState<S>.() -> Unit,
): String = TextCapturingParseState(this).apply(action).getCaptured()

public inline fun <S : TextParseState> TextCapturingParseState<S>.notCapturing(
    action: S.(TextCapturingParseState<S>) -> Unit,
): Unit = base.action(this)

public inline fun TextParseState.captureWhile(predicate: (Char) -> Boolean): String =
    capturing { readWhile(predicate) }

public fun TextParseState.captureCount(count: Int): String =
    capturing { repeat(count) { next() } }

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
        // Check for newline
        if (current == '\n') {
            line++
            lineStart = offset
        }
        offset++
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
