package serial

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

// Primitives

public fun InputStream.readByte(): Byte {
    val x = read()
    check(x in 0x00..0xFF) { "Reached end of stream" }
    return x.toByte()
}

public fun OutputStream.writeByte(value: Byte): Unit = write(value.toInt() and 0xFF)

public fun InputStream.readBoolean(): Boolean = readBoolean(::readByte)
public fun OutputStream.writeBoolean(value: Boolean): Unit = writeBoolean(value, ::writeByte)

public fun InputStream.readUByte(): UByte = readUByte(::readByte)
public fun OutputStream.writeUByte(value: UByte): Unit = writeUByte(value, ::writeByte)

public fun InputStream.readShort(): Short = readShort(::readByte)
public fun OutputStream.writeShort(value: Short): Unit = writeShort(value, ::writeByte)

public fun InputStream.readUShort(): UShort = readUShort(::readByte)
public fun OutputStream.writeUShort(value: UShort): Unit = writeUShort(value, ::writeByte)

public fun InputStream.readInt(): Int = readInt(::readByte)
public fun OutputStream.writeInt(value: Int): Unit = writeInt(value, ::writeByte)

public fun InputStream.readUInt(): UInt = readUInt(::readByte)
public fun OutputStream.writeUInt(value: UInt): Unit = writeUInt(value, ::writeByte)

public fun InputStream.readLong(): Long = readLong(::readByte)
public fun OutputStream.writeLong(value: Long): Unit = writeLong(value, ::writeByte)

public fun InputStream.readULong(): ULong = readULong(::readByte)
public fun OutputStream.writeULong(value: ULong): Unit = writeULong(value, ::writeByte)

public fun InputStream.readFloat(): Float = readFloat(::readByte)
public fun OutputStream.writeFloat(value: Float): Unit = writeFloat(value, ::writeByte)

public fun InputStream.readDouble(): Double = readDouble(::readByte)
public fun OutputStream.writeDouble(value: Double): Unit = writeDouble(value, ::writeByte)

public fun InputStream.readString(): String = readString(::readByte, ::read)
public fun OutputStream.writeString(value: String): Unit = writeString(value, ::writeByte, ::write)

public inline fun <reified T : Enum<T>> InputStream.readEnumByName(): T = readEnumByName(::readByte, ::read)
public fun <T : Enum<T>> OutputStream.writeEnumByName(value: T): Unit = writeEnumByName(value, ::writeByte, ::write)

public inline fun <reified T : Enum<T>> InputStream.readEnumByOrdinalAuto(): T = readEnumByOrdinalAuto(::readByte)

public inline fun <reified T : Enum<T>> OutputStream.writeEnumByOrdinalAuto(value: T): Unit =
    writeEnumByOrdinalAuto(value, ::writeByte)

public inline fun <reified T : Enum<T>> InputStream.readEnumByOrdinalByte(): T = readEnumByOrdinalByte(::readByte)
public fun <T : Enum<T>> OutputStream.writeEnumByOrdinalByte(value: T): Unit =
    writeEnumByOrdinalByte(value, ::writeByte)

public inline fun <reified T : Enum<T>> InputStream.readEnumByOrdinalShort(): T = readEnumByOrdinalShort(::readByte)
public fun <T : Enum<T>> OutputStream.writeEnumByOrdinalShort(value: T): Unit =
    writeEnumByOrdinalShort(value, ::writeByte)

public inline fun <reified T : Enum<T>> InputStream.readEnumByOrdinal(): T = readEnumByOrdinal(::readByte)
public fun <T : Enum<T>> OutputStream.writeEnumByOrdinal(value: T): Unit =
    writeEnumByOrdinal(value, ::writeByte)

// Extensions

public inline fun <T : Any> InputStream.readNullable(readValue: InputStream.() -> T): T? =
    readNullable(::readByte) { readValue() }

public inline fun <T : Any> OutputStream.writeNullable(value: T?, writeValue: OutputStream.(T) -> Unit): Unit =
    writeNullable(value, ::writeByte) { writeValue(it) }

public inline fun InputStream.repeatRead(action: InputStream.(index: Int) -> Unit): Unit =
    repeatRead(::readByte) { action(it) }

public inline fun OutputStream.repeatWrite(count: Int, action: OutputStream.(index: Int) -> Unit): Unit =
    repeatWrite(count, ::writeByte) { action(it) }

public inline fun <T> InputStream.readValues(readValue: InputStream.() -> T, action: (value: T) -> Unit): Unit =
    readValues(::readByte, { readValue() }, { action(it) })

public inline fun <T, C : MutableCollection<T>> InputStream.readValuesTo(values: C, readValue: InputStream.() -> T): C =
    readValuesTo(values, ::readByte) { readValue() }

public inline fun <T> InputStream.readValues(readValue: InputStream.() -> T): List<T> =
    readValues(::readByte) { readValue() }

public inline fun <T> OutputStream.writeValues(list: Collection<T>, writeValue: OutputStream.(T) -> Unit): Unit =
    writeValues(list, ::writeByte) { writeValue(it) }

public inline fun <K, V> InputStream.readMap(
    readKey: InputStream.() -> K,
    readValue: InputStream.(K) -> V,
    action: (key: K, value: V) -> Unit,
): Unit = readMap(
    ::readByte,
    { readKey() },
    { readValue(it) },
    action,
)

public inline fun <K, V> InputStream.readMap(
    readKey: InputStream.() -> K,
    readValue: InputStream.(K) -> V,
    into: MutableMap<K, V> = mutableMapOf(),
): Map<K, V> = readMap(
    ::readByte,
    { readKey() },
    { readValue(it) },
    into,
)

public inline fun <K, V> OutputStream.writeMap(
    map: Map<K, V>,
    writeEntry: OutputStream.(key: K, value: V) -> Unit,
): Unit = writeMap(map, ::writeByte) { key, value -> writeEntry(key, value) }

public inline fun <K, V> OutputStream.writeMap(
    map: Map<K, V>,
    writeKey: OutputStream.(key: K) -> Unit,
    writeValue: OutputStream.(value: V) -> Unit,
): Unit = writeMap(
    map,
    ::writeByte,
    { writeKey(it) },
    { writeValue(it) },
)

// Testing

public inline fun makeByteArray(action: OutputStream.() -> Unit): ByteArray = with(ByteArrayOutputStream()) {
    use(action)
    toByteArray()
}

public inline fun <T> ByteArray.asInputStream(action: InputStream.() -> T): T =
    ByteArrayInputStream(this).use(action)

public inline fun makeInputStream(action: OutputStream.() -> Unit): InputStream =
    ByteArrayInputStream(makeByteArray(action))

public inline fun <T> fullWriteAndRead(write: OutputStream.() -> Unit, read: InputStream.() -> T): T =
    makeByteArray(write).asInputStream(read)

public fun ByteArray.toOutputStream(): FixedSizeOutputStream = FixedSizeOutputStream(this)

public fun ByteArray.asOutputStream(action: FixedSizeOutputStream.() -> Unit): Unit =
    toOutputStream().use(action)

public class FixedSizeOutputStream(public val array: ByteArray) : OutputStream() {
    public constructor(size: Int) : this(ByteArray(size))

    public var offset: Int = 0
        private set

    public fun seek(offset: Int) {
        check(offset)
        this.offset = offset
    }

    public fun seekBy(delta: Int) {
        val target = offset + delta
        check(target)
        this.offset = target
    }

    override fun write(byte: Int) {
        check(offset)
        array[offset++] = byte.toByte()
    }

    private fun check(offset: Int) {
        if (offset !in array.indices) throw IndexOutOfBoundsException(offset)
    }
}
