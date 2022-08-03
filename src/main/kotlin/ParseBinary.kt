package serial

import java.io.InputStream

public interface BinaryParseState {
    public val offset: Int
    public val current: Byte
    public val isEndOfInput: Boolean
    public val isCapturing: Boolean
    public fun next()
    public fun startCapturing()
    public fun stopCapturing()
    public fun capture(byte: Byte)
    public fun getCaptured(): ByteArray
    public fun purgeCaptured()
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

public fun BinaryParseState.finishCapturing(): ByteArray {
    ensure(isCapturing) { "Not currently capturing" }
    stopCapturing()
    val result = getCaptured()
    purgeCaptured()
    return result
}

public inline fun BinaryParseState.capturing(action: BinaryParseState.() -> Unit): ByteArray {
    ensure(!isCapturing) { "Already capturing" }
    startCapturing()
    action()
    return finishCapturing()
}

public inline fun BinaryParseState.notCapturing(action: BinaryParseState.() -> Unit) {
    ensure(isCapturing) { "Not currently capturing" }
    stopCapturing()
    action()
    startCapturing()
}

public inline fun BinaryParseState.captureWhile(predicate: (Byte) -> Boolean): ByteArray =
    capturing { readWhile(predicate) }

public fun BinaryParseState.captureCount(count: Int): ByteArray =
    capturing { repeat(count) { next() } }

public fun BinaryParseState.capture(literal: ByteArray): Unit = literal.forEach(::capture)

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
        if (isCapturing) capture(current)
        offset++
    }

    private val currentCapture = ByteArrayBuilder()
    override var isCapturing = false

    final override fun startCapturing() {
        isCapturing = true
    }

    final override fun stopCapturing() {
        isCapturing = false
    }

    final override fun capture(byte: Byte) {
        currentCapture.append(byte)
    }

    override fun getCaptured() = currentCapture.toArray()

    override fun purgeCaptured() = currentCapture.clear()
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
    private var count: Int = 0

    fun append(byte: Byte) {
        if (array.size < count + 1) {
            array = array.copyOf(array.size * 2)
        }
        array[count++] = byte
    }

    fun toArray(): ByteArray = array.copyOf(count)

    fun clear() {
        count = 0
    }
}
