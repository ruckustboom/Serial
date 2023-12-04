package serial

public fun ByteArray.readByte(index: Int): Byte = this[index]
public fun ByteArray.writeByte(index: Int, value: Byte) {
    this[index] = value
}

public fun ByteArray.readBoolean(index: Int): Boolean = readByte(index) != 0.toByte()
public fun ByteArray.writeBoolean(index: Int, value: Boolean) {
    writeByte(index, if (value) 1 else 0)
}

public fun ByteArray.readUByte(index: Int): UByte = readByte(index).toUByte()
public fun ByteArray.writeUByte(index: Int, value: UByte): Unit = writeByte(index, value.toByte())

public fun ByteArray.readShort(index: Int): Short {
    var value = 0
    repeat(Short.SIZE_BYTES) {
        value = value shl 8 or (readByte(index + it).toInt() and 0xFF)
    }
    return value.toShort()
}

public fun ByteArray.writeShort(index: Int, value: Short) {
    repeat(Short.SIZE_BYTES) {
        writeByte(index + it, (value.toInt() ushr (Short.SIZE_BYTES - 1 - it) * 8).toByte())
    }
}

public fun ByteArray.readUShort(index: Int): UShort = readShort(index).toUShort()
public fun ByteArray.writeUShort(index: Int, value: UShort): Unit = writeShort(index, value.toShort())

public fun ByteArray.readInt(index: Int): Int {
    var value = 0
    repeat(Int.SIZE_BYTES) {
        value = value shl 8 or (readByte(index + it).toInt() and 0xFF)
    }
    return value
}

public fun ByteArray.writeInt(index: Int, value: Int) {
    repeat(Int.SIZE_BYTES) {
        writeByte(index + it, (value ushr (Int.SIZE_BYTES - 1 - it) * 8).toByte())
    }
}

public fun ByteArray.readUInt(index: Int): UInt = readInt(index).toUInt()
public fun ByteArray.writeUInt(index: Int, value: UInt): Unit = writeInt(index, value.toInt())

public fun ByteArray.readLong(index: Int): Long {
    var value = 0L
    repeat(Long.SIZE_BYTES) {
        value = value shl 8 or (readByte(index + it).toLong() and 0xFF)
    }
    return value
}

public fun ByteArray.writeLong(index: Int, value: Long) {
    repeat(Long.SIZE_BYTES) {
        writeByte(index + it, (value ushr (Long.SIZE_BYTES - 1 - it) * 8).toByte())
    }
}

public fun ByteArray.readULong(index: Int): ULong = readLong(index).toULong()
public fun ByteArray.writeULong(index: Int, value: ULong): Unit = writeLong(index, value.toLong())

public fun ByteArray.readFloat(index: Int): Float = Float.fromBits(readInt(index))
public fun ByteArray.writeFloat(index: Int, value: Float): Unit = writeInt(index, value.toRawBits())

public fun ByteArray.readDouble(index: Int): Double = Double.fromBits(readLong(index))
public fun ByteArray.writeDouble(index: Int, value: Double): Unit = writeLong(index, value.toRawBits())
