package serial

import java.io.InputStream
import java.io.OutputStream

public fun InputStream.readBoolean(): Boolean = readOrThrow() != 0
public fun OutputStream.writeBoolean(value: Boolean) {
    write(if (value) 1 else 0)
}

public fun InputStream.readByte(): Byte = readOrThrow().toByte()
public fun OutputStream.writeByte(value: Byte): Unit = write(value.toInt() and 0xFF)

public fun InputStream.readUByte(): UByte = readByte().toUByte()
public fun OutputStream.writeUByte(value: UByte): Unit = writeByte(value.toByte())

public fun InputStream.readShort(): Short = (((readOrThrow() and 0xFF) shl 8) or (readOrThrow() and 0xFF)).toShort()
public fun OutputStream.writeShort(value: Short) {
    val x = value.toInt()
    write(x ushr 8 and 0xFF)
    write(x and 0xFF)
}

public fun InputStream.readUShort(): UShort = readShort().toUShort()
public fun OutputStream.writeUShort(value: UShort): Unit = writeShort(value.toShort())

public fun InputStream.readInt(): Int {
    var value = 0
    repeat(4) {
        value = value shl 8 or (readOrThrow() and 0xFF)
    }
    return value
}

public fun OutputStream.writeInt(value: Int) {
    write(value ushr 24 and 0xFF)
    write(value ushr 16 and 0xFF)
    write(value ushr 8 and 0xFF)
    write(value and 0xFF)
}

public fun InputStream.readUInt(): UInt = readInt().toUInt()
public fun OutputStream.writeUInt(value: UInt): Unit = writeInt(value.toInt())

public fun InputStream.readLong(): Long = (readInt().toLong() shl 32) or (readInt().toLong() and 0xFF_FF_FF_FF)
public fun OutputStream.writeLong(value: Long) {
    writeInt(value.ushr(32).toInt())
    write((value and 0xFF_FF_FF_FF).toInt())
}

public fun InputStream.readULong(): ULong = readLong().toULong()
public fun OutputStream.writeULong(value: ULong): Unit = writeLong(value.toLong())

public fun InputStream.readFloat(): Float = Float.fromBits(readInt())
public fun OutputStream.writeFloat(value: Float): Unit = writeInt(value.toRawBits())

public fun InputStream.readDouble(): Double = Double.fromBits(readLong())
public fun OutputStream.writeDouble(value: Double): Unit = writeLong(value.toRawBits())

public fun InputStream.readString(): String {
    val count = readInt()
    val bytes = ByteArray(count)
    require(read(bytes) == count)
    return bytes.decodeToString()
}

public fun OutputStream.writeString(value: String) {
    val bytes = value.encodeToByteArray()
    writeInt(bytes.size)
    write(bytes)
}

public inline fun <reified T : Enum<T>> InputStream.readEnum(): T {
    val variants = enumValues<T>()
    return variants[when {
        variants.size <= UByte.MAX_VALUE.toInt() -> readUByte().toInt()
        variants.size <= UShort.MAX_VALUE.toInt() -> readUShort().toInt()
        else -> readInt()
    }]
}

public inline fun <reified T : Enum<T>> OutputStream.writeEnum(value: T) {
    val variants = enumValues<T>()
    when {
        variants.size <= UByte.MAX_VALUE.toInt() -> writeUByte(value.ordinal.toUByte())
        variants.size <= UShort.MAX_VALUE.toInt() -> writeUShort(value.ordinal.toUShort())
        else -> writeInt(value.ordinal)
    }
}

public inline fun <reified T : Enum<T>> InputStream.readEnumByte(): T = enumValues<T>()[readUByte().toInt()]
public inline fun <reified T : Enum<T>> OutputStream.writeEnumByte(value: T) {
    require(value.ordinal < UByte.MAX_VALUE.toInt())
    writeUByte(value.ordinal.toUByte())
}

public inline fun <reified T : Enum<T>> InputStream.readEnumShort(): T = enumValues<T>()[readUShort().toInt()]
public inline fun <reified T : Enum<T>> OutputStream.writeEnumShort(value: T) {
    require(value.ordinal < UShort.MAX_VALUE.toInt())
    writeUShort(value.ordinal.toUShort())
}

public inline fun <reified T : Enum<T>> InputStream.readEnumInt(): T = enumValues<T>()[readInt()]
public inline fun <reified T : Enum<T>> OutputStream.writeEnumInt(value: T): Unit = writeInt(value.ordinal)
