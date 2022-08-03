package serial

import java.io.InputStream

@DslMarker
public annotation class BinaryCursorMarker

@BinaryCursorMarker
public interface BinaryCursor {
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

public fun InputStream.initParse(): BinaryCursor = InputStreamBinaryCursor(this).apply { next() }
public fun ByteArray.initParse(): BinaryCursor = ByteArrayBinaryCursor(this).apply { next() }

public inline fun <T> BinaryCursor.parse(
    consumeAll: Boolean = true,
    parse: BinaryCursor.() -> T,
): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> InputStream.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: BinaryCursor.() -> T,
): T = with(initParse()) { parse(consumeAll, parse).also { if (closeWhenDone) close() } }

public inline fun <T> ByteArray.parse(
    consumeAll: Boolean = true,
    parse: BinaryCursor.() -> T,
): T = with(initParse()) { parse(consumeAll, parse) }

public inline fun <T> BinaryCursor.parseMultiple(
    parse: BinaryCursor.() -> T,
): List<T> = buildList { while (!isEndOfInput) add(parse()) }

public inline fun <T> InputStream.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: BinaryCursor.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse).also { if (closeWhenDone) close() } }

public inline fun <T> ByteArray.parseMultiple(
    parse: BinaryCursor.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse) }

public fun BinaryCursor.crash(message: String, cause: Throwable? = null): Nothing =
    throw BinaryParseException(offset, current, message, cause)

public inline fun BinaryCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun BinaryCursor.readIf(predicate: (Byte) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun BinaryCursor.readWhile(predicate: (Byte) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public fun BinaryCursor.readOptionalByte(byte: Byte): Boolean = readIf { it == byte }

public fun BinaryCursor.readRequiredByte(byte: Byte): Unit = ensure(readOptionalByte(byte)) { "Expected: $byte" }

public fun BinaryCursor.readLiteral(bytes: ByteArray): Unit = bytes.forEach(::readRequiredByte)

// Capturing

public class CapturingBinaryCursor<out S : BinaryCursor>(public val base: S) : BinaryCursor by base {
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

public fun <S : BinaryCursor> CapturingBinaryCursor<S>.capture(literal: ByteArray): Unit = literal.forEach(::capture)

public inline fun <S : BinaryCursor> S.capturing(action: CapturingBinaryCursor<S>.() -> Unit): ByteArray =
    CapturingBinaryCursor(this).apply(action).getCaptured()

public inline fun <S : BinaryCursor> CapturingBinaryCursor<S>.notCapturing(action: S.() -> Unit): Unit = base.action()

public inline fun BinaryCursor.captureWhile(predicate: (Byte) -> Boolean): ByteArray =
    capturing { readWhile(predicate) }

public fun BinaryCursor.captureCount(count: Int): ByteArray = capturing { repeat(count) { next() } }

// Implementation

private abstract class BinaryCursorBase : BinaryCursor {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        offset++
    }
}

private class InputStreamBinaryCursor(private val stream: InputStream) : BinaryCursorBase() {
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

private class ByteArrayBinaryCursor(private val bytes: ByteArray) : BinaryCursorBase() {
    override val current get() = if (offset in bytes.indices) bytes[offset] else 0
    override val isEndOfInput get() = offset >= bytes.size

    override fun next() = advance()
}
