package serial

public inline fun readBoolean(readByte: () -> Byte): Boolean = readByte() != 0.toByte()
public inline fun writeBoolean(value: Boolean, writeByte: (Byte) -> Unit): Unit = writeByte(if (value) 1 else 0)

public inline fun readUByte(readByte: () -> Byte): UByte = readByte().toUByte()
public inline fun writeUByte(value: UByte, writeByte: (Byte) -> Unit): Unit = writeByte(value.toByte())

public inline fun readShort(readByte: () -> Byte): Short {
    var value = 0
    repeat(Short.SIZE_BYTES) {
        value = value shl 8 or (readByte().toInt() and 0xFF)
    }
    return value.toShort()
}

public inline fun writeShort(value: Short, writeByte: (Byte) -> Unit) {
    repeat(Short.SIZE_BYTES) {
        writeByte((value.toInt() ushr (Short.SIZE_BYTES - 1 - it) * 8).toByte())
    }
}

public inline fun readUShort(readByte: () -> Byte): UShort = readShort(readByte).toUShort()
public inline fun writeUShort(value: UShort, writeByte: (Byte) -> Unit): Unit = writeShort(value.toShort(), writeByte)

public inline fun readInt(readByte: () -> Byte): Int {
    var value = 0
    repeat(Int.SIZE_BYTES) {
        value = value shl 8 or (readByte().toInt() and 0xFF)
    }
    return value
}

public inline fun writeInt(value: Int, writeByte: (Byte) -> Unit) {
    repeat(Int.SIZE_BYTES) {
        writeByte((value ushr (Int.SIZE_BYTES - 1 - it) * 8).toByte())
    }
}

public inline fun readUInt(readByte: () -> Byte): UInt = readInt(readByte).toUInt()
public inline fun writeUInt(value: UInt, writeByte: (Byte) -> Unit): Unit = writeInt(value.toInt(), writeByte)

public inline fun readLong(readByte: () -> Byte): Long {
    var value = 0L
    repeat(Long.SIZE_BYTES) {
        value = value shl 8 or (readByte().toLong() and 0xFF)
    }
    return value
}

public inline fun writeLong(value: Long, writeByte: (Byte) -> Unit) {
    repeat(Long.SIZE_BYTES) {
        writeByte((value ushr (Long.SIZE_BYTES - 1 - it) * 8).toByte())
    }
}

public inline fun readULong(readByte: () -> Byte): ULong = readLong(readByte).toULong()
public inline fun writeULong(value: ULong, writeByte: (Byte) -> Unit): Unit = writeLong(value.toLong(), writeByte)

public inline fun readFloat(readByte: () -> Byte): Float = Float.fromBits(readInt(readByte))
public inline fun writeFloat(value: Float, writeByte: (Byte) -> Unit): Unit = writeInt(value.toRawBits(), writeByte)

public inline fun readDouble(readByte: () -> Byte): Double = Double.fromBits(readLong(readByte))
public inline fun writeDouble(value: Double, writeByte: (Byte) -> Unit): Unit = writeLong(value.toRawBits(), writeByte)

public inline fun readString(readByte: () -> Byte): String =
    ByteArray(readInt(readByte)) { readByte() }.decodeToString()

public inline fun writeString(value: String, writeByte: (Byte) -> Unit) {
    val bytes = value.encodeToByteArray()
    writeInt(bytes.size, writeByte)
    bytes.forEach(writeByte)
}

public inline fun readString(readByte: () -> Byte, readBytes: (ByteArray) -> Int): String {
    val count = readInt(readByte)
    val bytes = ByteArray(count)
    check(readBytes(bytes) == count)
    return bytes.decodeToString()
}

public inline fun writeString(value: String, writeByte: (Byte) -> Unit, writeBytes: (ByteArray) -> Unit) {
    val bytes = value.encodeToByteArray()
    writeInt(bytes.size, writeByte)
    writeBytes(bytes)
}

public inline fun <reified T : Enum<T>> readEnumByName(readByte: () -> Byte): T = enumValueOf(readString(readByte))
public inline fun <T : Enum<T>> writeEnumByName(writeByte: (Byte) -> Unit, value: T): Unit =
    writeString(value.name, writeByte)

public inline fun <reified T : Enum<T>> readEnumByName(readByte: () -> Byte, readBytes: (ByteArray) -> Int): T =
    enumValueOf(readString(readByte, readBytes))

public inline fun <T : Enum<T>> writeEnumByName(
    value: T,
    writeByte: (Byte) -> Unit,
    writeBytes: (ByteArray) -> Unit,
): Unit = writeString(value.name, writeByte, writeBytes)

public inline fun <reified T : Enum<T>> readEnumByOrdinalAuto(readByte: () -> Byte): T {
    val variants = enumValues<T>()
    return variants[when {
        variants.size <= UByte.MAX_VALUE.toInt() -> readUByte(readByte).toInt()
        variants.size <= UShort.MAX_VALUE.toInt() -> readUShort(readByte).toInt()
        else -> readInt(readByte)
    }]
}

public inline fun <reified T : Enum<T>> writeEnumByOrdinalAuto(value: T, writeByte: (Byte) -> Unit) {
    val variants = enumValues<T>()
    when {
        variants.size <= UByte.MAX_VALUE.toInt() -> writeUByte(value.ordinal.toUByte(), writeByte)
        variants.size <= UShort.MAX_VALUE.toInt() -> writeUShort(value.ordinal.toUShort(), writeByte)
        else -> writeInt(value.ordinal, writeByte)
    }
}

public inline fun <reified T : Enum<T>> readEnumByOrdinalByte(readByte: () -> Byte): T =
    enumValues<T>()[readUByte(readByte).toInt()]

public inline fun <T : Enum<T>> writeEnumByOrdinalByte(value: T, writeByte: (Byte) -> Unit) {
    require(value.ordinal < UByte.MAX_VALUE.toInt())
    writeUByte(value.ordinal.toUByte(), writeByte)
}

public inline fun <reified T : Enum<T>> readEnumByOrdinalShort(readByte: () -> Byte): T =
    enumValues<T>()[readUShort(readByte).toInt()]

public inline fun <T : Enum<T>> writeEnumByOrdinalShort(value: T, writeByte: (Byte) -> Unit) {
    require(value.ordinal < UShort.MAX_VALUE.toInt())
    writeUShort(value.ordinal.toUShort(), writeByte)
}

public inline fun <reified T : Enum<T>> readEnumByOrdinal(readByte: () -> Byte): T =
    enumValues<T>()[readInt(readByte)]

public inline fun <T : Enum<T>> writeEnumByOrdinal(value: T, writeByte: (Byte) -> Unit): Unit =
    writeInt(value.ordinal, writeByte)
