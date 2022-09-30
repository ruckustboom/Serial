package serial

import java.io.ByteArrayOutputStream
import java.io.InputStream

public interface ByteCursor : DataCursor {
    public val current: Byte
}

// Exceptions

public class ByteCursorException(
    public val offset: Int,
    public val byte: Byte,
    public val description: String,
    cause: Throwable? = null,
) : Exception("$description (found $byte at $offset)", cause)

public fun ByteCursor.crash(message: String, cause: Throwable? = null): Nothing =
    throw ByteCursorException(offset, current, message, cause)

public inline fun ByteCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

// Initialize

public fun InputStream.toCursor(): ByteCursor = InputStreamCursor(this).apply { next() }
public fun ByteArray.toCursor(): ByteCursor = ByteArrayCursor(this).apply { next() }

public inline fun <T> ByteCursor.parse(consumeAll: Boolean = true, parse: ByteCursor.() -> T): T {
    val result = parse()
    if (consumeAll && !isEndOfInput) crash("Expected EOI @ $offset, found $current")
    return result
}

public inline fun <T> InputStream.parse(
    consumeAll: Boolean = true,
    closeWhenDone: Boolean = true,
    parse: ByteCursor.() -> T,
): T = try {
    toCursor().parse(consumeAll, parse)
} finally {
    if (closeWhenDone) close()
}

public inline fun <T> ByteArray.parse(consumeAll: Boolean = true, parse: ByteCursor.() -> T): T =
    toCursor().parse(consumeAll, parse)

public fun <S : DataCursor> S.tokenizeToByte(parseToken: S.() -> Byte): ByteCursor = ByteTokenizer(this, parseToken)

// Some common helpers

public fun ByteCursor.read(): Byte = current.also { next() }

public inline fun ByteCursor.readIf(predicate: (Byte) -> Boolean): Boolean = if (!isEndOfInput && predicate(current)) {
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
    private val data = ByteArrayOutputStream()

    override fun next() {
        capture(current)
        base.next()
    }

    public fun capture(byte: Byte) {
        data.writeByte(byte)
    }

    public fun getCaptured(): ByteArray = data.toByteArray()
}

public fun <S : ByteCursor> CapturingByteCursor<S>.capture(literal: ByteArray): Unit = literal.forEach(::capture)

public inline fun <S : ByteCursor> S.capturing(action: CapturingByteCursor<S>.() -> Unit): ByteArray =
    CapturingByteCursor(this).apply(action).getCaptured()

public inline fun <S : ByteCursor> CapturingByteCursor<S>.notCapturing(action: S.() -> Unit): Unit = base.action()

public inline fun ByteCursor.captureWhile(predicate: (Byte) -> Boolean): ByteArray = capturing { readWhile(predicate) }

public fun ByteCursor.captureCount(count: Int): ByteArray = capturing { repeat(count) { next() } }

// Primitives

public fun ByteCursor.toInputStream(): InputStream = ByteCursorInputStream(this)
public inline fun <R> ByteCursor.asInputStream(action: InputStream.() -> R): R = toInputStream().use(action)

public fun ByteCursor.readBoolean(): Boolean = readBoolean(::read)
public fun ByteCursor.readUByte(): UByte = readUByte(::read)
public fun ByteCursor.readShort(): Short = readShort(::read)
public fun ByteCursor.readUShort(): UShort = readUShort(::read)
public fun ByteCursor.readInt(): Int = readInt(::read)
public fun ByteCursor.readUInt(): UInt = readUInt(::read)
public fun ByteCursor.readLong(): Long = readLong(::read)
public fun ByteCursor.readULong(): ULong = readULong(::read)
public fun ByteCursor.readFloat(): Float = readFloat(::read)
public fun ByteCursor.readDouble(): Double = readDouble(::read)
public fun ByteCursor.readString(): String = readString(::read)
public inline fun <reified T : Enum<T>> ByteCursor.readEnumByName(): T = readEnumByName(::read)
public inline fun <reified T : Enum<T>> ByteCursor.readEnumByOrdinalAuto(): T = readEnumByOrdinalAuto(::read)
public inline fun <reified T : Enum<T>> ByteCursor.readEnumByOrdinalByte(): T = readEnumByOrdinalByte(::read)
public inline fun <reified T : Enum<T>> ByteCursor.readEnumByOrdinalShort(): T = readEnumByOrdinalShort(::read)
public inline fun <reified T : Enum<T>> ByteCursor.readEnumByOrdinal(): T = readEnumByOrdinal(::read)

// Extensions

public inline fun <T : Any> ByteCursor.readNullable(readValue: ByteCursor.() -> T): T? =
    if (readBoolean()) readValue() else null

public inline fun ByteCursor.repeatRead(action: ByteCursor.(index: Int) -> Unit) {
    repeat(readInt()) { action(it) }
}

public inline fun <T> ByteCursor.readValues(readValue: ByteCursor.() -> T, action: (value: T) -> Unit) {
    repeatRead { action(readValue()) }
}

public inline fun <T, C : MutableCollection<T>> ByteCursor.readValuesTo(values: C, readValue: ByteCursor.() -> T): C {
    readValues(readValue) { values += it }
    return values
}

public inline fun <T> ByteCursor.readValues(readValue: ByteCursor.() -> T): List<T> = List(readInt()) { readValue() }

public inline fun <K, V> ByteCursor.readMap(
    readKey: ByteCursor.() -> K,
    readValue: ByteCursor.(K) -> V,
    action: (key: K, value: V) -> Unit,
): Unit = repeatRead {
    val key = readKey()
    val value = readValue(key)
    action(key, value)
}

public inline fun <K, V> ByteCursor.readMap(
    readKey: ByteCursor.() -> K,
    readValue: ByteCursor.(K) -> V,
    into: MutableMap<K, V> = mutableMapOf(),
): Map<K, V> {
    readMap(readKey, readValue) { key, value -> into[key] = value }
    return into
}

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

private class ByteTokenizer<S : DataCursor>(private val base: S, private val parse: S.() -> Byte) : ByteCursorBase() {
    override var current: Byte = 0
    override val isEndOfInput get() = base.isEndOfInput

    override fun next() {
        advance()
        current = base.parse()
    }
}

private class ByteCursorInputStream(private val cursor: ByteCursor) : InputStream() {
    override fun read() = if (cursor.isEndOfInput) -1 else cursor.read().toInt() and 0xFF
}
