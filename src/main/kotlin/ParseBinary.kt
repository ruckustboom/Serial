package serial

import java.io.ByteArrayInputStream
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

public fun InputStream.initParse(): BinaryParseState = BinaryParseStateImpl(this).apply { next() }

public inline fun <T> InputStream.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: BinaryParseState.() -> T
): T = with(initParse()) {
    val result = parse()
    if (closeWhenDone) close()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    result
}

public inline fun <T> ByteArray.parse(
    consumeAll: Boolean = true,
    parse: BinaryParseState.() -> T,
): T = ByteArrayInputStream(this).use { it.parse(consumeAll, true, parse) }

public inline fun <T> InputStream.parseMultiple(
    closeWhenDone: Boolean = true,
    parse: BinaryParseState.() -> T,
): List<T> = with(initParse()) {
    val results = mutableListOf<T>()
    while (!isEndOfInput) results += parse()
    if (closeWhenDone) close()
    results
}

public inline fun <T> ByteArray.parseMultiple(
    parse: BinaryParseState.() -> T,
): List<T> = ByteArrayInputStream(this).use { it.parseMultiple(true, parse) }

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

public inline fun BinaryParseState.captureWhile(predicate: (Byte) -> Boolean): ByteArray {
    startCapture()
    readWhile(predicate)
    return finishCapture()
}

public fun BinaryParseState.readOptionalByte(byte: Byte): Boolean = readIf { it == byte }

public fun BinaryParseState.readRequiredByte(byte: Byte): Unit =
    ensure(readOptionalByte(byte)) { "Expected: $byte" }

public fun BinaryParseState.readLiteral(bytes: ByteArray): Unit = bytes.forEach(::readRequiredByte)

// Implementation

private class BinaryParseStateImpl(private val stream: InputStream) : BinaryParseState {
    override var offset = -1
    override var current: Byte = 0
    override var isEndOfInput = false

    // Read

    override fun next() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) addToCapture(current)
        val next = stream.read()
        offset++
        if (next >= 0) {
            current = next.toByte()
        } else {
            current = 0
            isEndOfInput = true
        }
    }

    // Capture

    private val capture = ByteArrayBuilder()
    private var isCapturing = false

    override fun startCapture() {
        isCapturing = true
    }

    override fun pauseCapture() {
        isCapturing = false
    }

    override fun addToCapture(byte: Byte) {
        capture.append(byte)
    }

    override fun finishCapture(): ByteArray {
        isCapturing = false
        return capture.truncate()
    }
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
