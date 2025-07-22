package serial

import java.io.ByteArrayOutputStream
import java.io.InputStream

public interface ByteCursor : DataCursor {
    public val current: Byte

    public interface Sink : DataCursor.Sink {
        public fun update(value: Byte)
    }
}

// Exceptions

public class ByteCursorException(
    public val offset: Long,
    public val byte: Byte,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

// Initialize

public fun ByteArray.toCursor(): ByteCursor = ByteArrayCursor(this)
public fun ByteIterator.toCursor(): ByteCursor = ByteIteratorSource(this).toCursor()
public fun InputStream.toCursor(): ByteCursor = InputStreamSource(this).toCursor()
public fun DataCursor.Source<ByteCursor.Sink>.toCursor(): ByteCursor = SourceByteCursor(this)

public inline fun <T> ByteCursor.parse(parse: ByteCursor.() -> T): T = parse()
public inline fun <T> InputStream.parse(parse: ByteCursor.() -> T): T = toCursor().parse(parse)
public inline fun <T> ByteArray.parse(parse: ByteCursor.() -> T): T = toCursor().parse(parse)
public fun <S : DataCursor> S.tokenizeToByte(parseToken: S.() -> Byte): ByteCursor =
    SourceByteCursor(ByteTokenizer(this, parseToken))

// Some common helpers

public fun ByteCursor.read(): Byte = current.also { advance() }

public inline fun ByteCursor.readIf(predicate: (Byte) -> Boolean): Boolean = if (!isEndOfInput && predicate(current)) {
    advance()
    true
} else false

public inline fun ByteCursor.readWhile(predicate: (Byte) -> Boolean): Int {
    var count = 0
    while (!isEndOfInput && predicate(current)) {
        advance()
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

    override fun advance() {
        capture(base.read())
    }

    public fun capture(byte: Byte) {
        data.writeByte(byte)
    }

    public fun getCaptured(): ByteArray = data.toByteArray()
}

public fun <S : ByteCursor> CapturingByteCursor<S>.capture(literal: ByteArray): Unit = literal.forEach(::capture)

public inline fun <S : ByteCursor> S.capturing(action: CapturingByteCursor<S>.() -> Unit): ByteArray =
    CapturingByteCursor(this).apply(action).getCaptured()

public inline fun <S : ByteCursor, T> CapturingByteCursor<S>.notCapturing(action: S.() -> T): T = base.action()

public inline fun ByteCursor.captureWhile(predicate: (Byte) -> Boolean): ByteArray = capturing { readWhile(predicate) }

public fun ByteCursor.captureCount(count: Int): ByteArray = capturing { advanceBy(count) }

// Conversion

public fun ByteCursor.toInputStream(): InputStream = ByteCursorInputStream(this)
public inline fun <R> ByteCursor.asInputStream(action: InputStream.() -> R): R = toInputStream().use(action)

// Primitives

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
    final override var offset = 0L
        private set

    override fun crash(message: String, cause: Throwable?): Nothing =
        throw ByteCursorException(offset, current, message, cause)

    override fun advance() {
        ensure(!isEndOfInput) { "Unexpected end of input" }
        offset++
    }
}

private class ByteArrayCursor(private val array: ByteArray) : ByteCursorBase() {
    override val current get() = if (offset in array.indices) array[offset.toInt()] else 0
    override val isEndOfInput get() = offset >= array.size
}

private class SourceByteCursor(
    val source: DataCursor.Source<ByteCursor.Sink>,
) : ByteCursorBase() {
    override var current: Byte = -1
        private set
    override var isEndOfInput = false
        private set

    private val sink = object : ByteCursor.Sink {
        override fun update(value: Byte) {
            current = value
        }

        override fun eoi() {
            isEndOfInput = true
            current = -1
        }

        override fun crash(message: String, cause: Throwable?) =
            this@SourceByteCursor.crash(message, cause)
    }

    init {
        source.fetch(sink)
    }

    override fun advance() {
        super.advance()
        source.fetch(sink)
    }
}

private class InputStreamSource(
    stream: InputStream,
) : DataCursor.Source<ByteCursor.Sink>, AutoCloseable {
    private var stream: InputStream? = stream

    override fun fetch(into: ByteCursor.Sink) {
        val stream = stream ?: into.crash("Stream already closed")
        val next = try {
            stream.read()
        } catch (e: Throwable) {
            into.crash("Failed to read from stream", e)
        }
        if (next < 0) into.eoi()
        else into.update(next.toByte())
    }

    override fun close() {
        stream?.close()
        stream = null
    }
}

private class ByteIteratorSource(
    private val iter: ByteIterator,
) : DataCursor.Source<ByteCursor.Sink> {
    override fun fetch(into: ByteCursor.Sink) {
        if (iter.hasNext()) into.update(iter.nextByte())
        else into.eoi()
    }
}

private class ByteTokenizer<S : DataCursor>(
    private val base: S,
    private val parse: S.() -> Byte,
) : DataCursor.Source<ByteCursor.Sink> {
    override fun fetch(into: ByteCursor.Sink) {
        if (base.isEndOfInput) into.eoi()
        else into.update(base.parse())
    }
}

private class ByteCursorInputStream(private val cursor: ByteCursor) : InputStream() {
    override fun read() = if (cursor.isEndOfInput) -1 else cursor.read().toInt() and 0xFF
}
