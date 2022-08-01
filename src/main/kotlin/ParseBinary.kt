package serial

import java.io.InputStream

public interface BinaryParseState {
    public val offset: Int
    public val current: Byte
    public val isEndOfInput: Boolean
    public fun next()
    public fun startCapture()
    public fun pauseCapture()
    public fun addToCapture(byte: Byte)
    public fun finishCapture(): ByteArray
}

public class BinaryParseException(
    public val offset: Int,
    public val byte: Byte,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found $byte at $offset)", cause)

// Some common helpers

public fun InputStream.initParse(): BinaryParseState = InputStreamBinaryParseState(this).apply { next() }
public fun ByteArray.initParse(): BinaryParseState = ByteArrayBinaryParseState(this).apply { next() }

public inline fun <T> BinaryParseState.parse(consumeAll: Boolean = true, parse: BinaryParseState.() -> T): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> InputStream.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: BinaryParseState.() -> T,
): T = with(initParse()) { parse(consumeAll, parse).also { if (closeWhenDone) close() } }

public inline fun <T> ByteArray.parse(
    consumeAll: Boolean = true,
    parse: BinaryParseState.() -> T,
): T = with(initParse()) { parse(consumeAll, parse) }

public inline fun <T> BinaryParseState.parseMultiple(parse: BinaryParseState.() -> T): List<T> =
    buildList { while (!isEndOfInput) add(parse()) }

public inline fun <T> InputStream.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: BinaryParseState.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse).also { if (closeWhenDone) close() } }

public inline fun <T> ByteArray.parseMultiple(
    parse: BinaryParseState.() -> T,
): List<T> = with(initParse()) { parseMultiple(parse) }

public fun BinaryParseState.crash(message: String, cause: Throwable? = null): Nothing =
    throw BinaryParseException(offset, current, message, cause)

public inline fun BinaryParseState.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun BinaryParseState.readIf(predicate: (Byte) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(current)) {
        next()
        true
    } else false

public inline fun BinaryParseState.readWhile(predicate: (Byte) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        next()
        count++
    }
    return count
}

public inline fun BinaryParseState.capture(action: BinaryParseState.() -> Unit): ByteArray {
    startCapture()
    action()
    return finishCapture()
}

public inline fun BinaryParseState.captureWhile(predicate: (Byte) -> Boolean): ByteArray =
    capture { readWhile(predicate) }

public fun BinaryParseState.capture(count: Int): ByteArray =
    capture { repeat(count) { next() } }

public fun BinaryParseState.addToCapture(literal: ByteArray): Unit = literal.forEach(::addToCapture)

public fun BinaryParseState.readOptionalByte(byte: Byte): Boolean = readIf { it == byte }

public fun BinaryParseState.readRequiredByte(byte: Byte): Unit =
    ensure(readOptionalByte(byte)) { "Expected: $byte" }

public fun BinaryParseState.readLiteral(bytes: ByteArray): Unit = bytes.forEach(::readRequiredByte)

// Implementation

private abstract class BinaryParseStateBase : BinaryParseState {
    final override var offset = -1
        private set

    fun advance() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) addToCapture(current)
        offset++
    }

    private val capture = ByteArrayBuilder()
    private var isCapturing = false

    final override fun startCapture() {
        isCapturing = true
    }

    final override fun pauseCapture() {
        isCapturing = false
    }

    final override fun addToCapture(byte: Byte) {
        capture.append(byte)
    }

    final override fun finishCapture(): ByteArray {
        isCapturing = false
        return capture.truncate()
    }
}

private class InputStreamBinaryParseState(private val stream: InputStream) : BinaryParseStateBase() {
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

private class ByteArrayBinaryParseState(private val bytes: ByteArray) : BinaryParseStateBase() {
    override val current get() = if (offset in bytes.indices) bytes[offset] else 0
    override val isEndOfInput get() = offset >= bytes.size

    override fun next() = advance()
}

private const val DEFAULT_BYTES_COUNT = 8

private class ByteArrayBuilder {
    private var array = ByteArray(DEFAULT_BYTES_COUNT)
    var count: Int = 0
        private set

    fun append(byte: Byte) {
        if (array.size < count + 1) {
            array = array.copyOf(array.size * 2)
        }
        array[count++] = byte
    }

    fun toArray(): ByteArray = array.copyOf(count)

    fun truncate(): ByteArray {
        val result = toArray()
        count = 0
        return result
    }
}
