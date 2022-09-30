package serial

import java.io.InputStream
import java.io.OutputStream

public fun InputStream.readBoolean(): Boolean = readBoolean(::readByte)
public fun OutputStream.writeBoolean(value: Boolean): Unit = writeBoolean(value, ::writeByte)

public fun InputStream.readByte(): Byte = readOrThrow().toByte()
public fun OutputStream.writeByte(value: Byte): Unit = write(value.toInt() and 0xFF)

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
