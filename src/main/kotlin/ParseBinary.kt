package serial

import java.io.InputStream

@DslMarker
public annotation class ByteCursorMarker

@ByteCursorMarker
public interface ByteCursor {
    public val offset: Int
    public val current: Byte
    public val isEndOfInput: Boolean
    public fun next()
}

public class BinaryParseException(
    public val offset: Int,
    public val byte: Byte,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found $byte at $offset)", cause)

// Some common helpers

public fun InputStream.initParse(): ByteCursor = InputStreamCursor(this).apply { next() }
public fun ByteArray.initParse(): ByteCursor = ByteArrayCursor(this).apply { next() }

public inline fun <T> ByteCursor.parse(
    consumeAll: Boolean = true,
    parse: ByteCursor.() -> T,
): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> InputStream.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: ByteCursor.() -> T,
): T = with(initParse()) { parse(consumeAll, parse).also { if (closeWhenDone) close() } }

public inline fun <T> ByteArray.parse(
    consumeAll: Boolean = true,
    parse: ByteCursor.() -> T,
): T = with(initParse()) { parse(consumeAll, parse) }

public inline fun <T> ByteCursor.parseMultiple(
    parse: ByteCursor.() -> T,
): List<T> = buildList { while (!isEndOfInput) add(parse()) }

public inline fun <T> InputStream.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: ByteCursor.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse).also { if (closeWhenDone) close() } }

public inline fun <T> ByteArray.parseMultiple(
    parse: ByteCursor.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse) }

public fun ByteCursor.crash(message: String, cause: Throwable? = null): Nothing =
    throw BinaryParseException(offset, current, message, cause)

public inline fun ByteCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun ByteCursor.readIf(predicate: (Byte) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun ByteCursor.readWhile(predicate: (Byte) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun ByteCursor.readOptionalByte(byte: Byte): Boolean = readIf { it == byte }

public fun ByteCursor.readRequiredByte(byte: Byte): Unit = ensure(readOptionalByte(byte)) { "Expected: $byte" }

public fun ByteCursor.readLiteral(bytes: ByteArray): Unit = bytes.forEach(::readRequiredByte)

// Capturing

public class CapturingByteCursor<out S : ByteCursor>(public val base: S) : ByteCursor by base {
    private var data = ByteArray(8)
    private var count: Int = 0

    override fun next() {
        capture(current)
        base.next()
    }

    public fun capture(byte: Byte) {
        if (data.size < count + 1) {
            data = data.copyOf(data.size * 2)
        }
        data[count++] = byte
    }

    public fun getCaptured(): ByteArray = data.copyOf(count)
}

public fun <S : ByteCursor> CapturingByteCursor<S>.capture(literal: ByteArray): Unit = literal.forEach(::capture)

public inline fun <S : ByteCursor> S.capturing(action: CapturingByteCursor<S>.() -> Unit): ByteArray =
    CapturingByteCursor(this).apply(action).getCaptured()

public inline fun <S : ByteCursor> CapturingByteCursor<S>.notCapturing(action: S.() -> Unit): Unit = base.action()

public inline fun ByteCursor.captureWhile(predicate: (Byte) -> Boolean): ByteArray =
    capturing { readWhile(predicate) }

public fun ByteCursor.captureCount(count: Int): ByteArray = capturing { repeat(count) { next() } }

// Implementation

private abstract class ByteCursorBase : ByteCursor {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        offset++
    }
}

private class InputStreamCursor(private val stream: InputStream) : ByteCursorBase() {
    override var current: Byte = 0
    override var isEndOfInput = false

    override fun next() {
        advance()
        val next = stream.read()
        if (next >= 0) {
            current = next.toByte()
        } else {
            current = 0
            isEndOfInput = true
        }
    }
}

private class ByteArrayCursor(private val bytes: ByteArray) : ByteCursorBase() {
    override val current get() = if (offset in bytes.indices) bytes[offset] else 0
    override val isEndOfInput get() = offset >= bytes.size

    override fun next() = advance()
}
