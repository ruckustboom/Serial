package serial

import java.io.ByteArrayInputStream
import java.io.InputStream

internal const val DEFAULT_BYTES_COUNT = 8

public interface BinaryParseState {
    public val offset: Int
    public val byte: Byte
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

/**
 * This method does **NOT** close the input stream or check for input
 * exhaustion. It is up to the user to handle that.
 */
public inline fun <T> InputStream.parse(parse: BinaryParseState.() -> T): T = initParse().parse()

public inline fun <T> ByteArray.parse(
    consumeAll: Boolean = true,
    parse: BinaryParseState.() -> T,
): T = ByteArrayInputStream(this).use {
    val state = it.initParse()
    val value = state.parse()
    if (consumeAll && state.offset < size) state.crash("Unexpected: ${state.byte} (${state.offset} vs $size)")
    value
}

public fun BinaryParseState.crash(message: String, cause: Throwable? = null): Nothing =
    throw BinaryParseException(offset, byte, message, cause)

public inline fun BinaryParseState.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public inline fun BinaryParseState.readIf(predicate: (Byte) -> Boolean): Boolean =
    if (!isEndOfInput && predicate(byte)) {
        next()
        true
    } else false

public inline fun BinaryParseState.readWhile(predicate: (Byte) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(byte)) {
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
    override var byte: Byte = 0
    override var isEndOfInput = false

    // Read

    override fun next() {
        ensure(!isEndOfInput) { "Unexpected EOI" }
        if (isCapturing) addToCapture(byte)
        val next = stream.read()
        offset++
        if (next >= 0) {
            byte = next.toByte()
        } else {
            byte = 0
            isEndOfInput = true
        }
    }

    // Capture

    private val capture = Bytes()
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

private class Bytes {
    private var array = ByteArray(DEFAULT_BYTES_COUNT)
    var count: Int = 0
        private set

    fun append(byte: Byte) {
        ensure(count + 1)
        array[count++] = byte
    }

    fun append(bytes: ByteArray) {
        ensure(count + bytes.size)
        bytes.copyInto(array, count)
        count += bytes.size
    }

    private fun ensure(size: Int) {
        if (array.size < size) {
            var actual = array.size
            while (actual < size) actual *= 2
            val new = ByteArray(actual)
            array.copyInto(new)
            array = new
        }
    }

    fun toArray(): ByteArray = array.copyOf(count)

    fun truncate(): ByteArray {
        val result = toArray()
        array = ByteArray(DEFAULT_BYTES_COUNT)
        count = 0
        return result
    }
}
