package serial

import java.io.InputStream
import java.io.OutputStream
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestStreams {
    // Primitives

    @Test
    fun testBytes() {
        val values = ByteArray(256, Int::toByte)
        val serialized = makeByteArray { values.forEach(::writeByte) }
        assertEquals(256, serialized.size)
        val deserialized = serialized.asInputStream { ByteArray(values.size) { readByte() } }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testUBytes() {
        val values = UByteArray(256, Int::toUByte)
        val serialized = makeByteArray { values.forEach(::writeUByte) }
        assertEquals(256, serialized.size)
        val deserialized = serialized.asInputStream { UByteArray(values.size) { readUByte() } }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testShorts() {
        val values = ShortArray(256) { Random.nextInt().toShort() }
        val serialized = makeByteArray { values.forEach(::writeShort) }
        assertEquals(512, serialized.size)
        val deserialized = serialized.asInputStream { ShortArray(values.size) { readShort() } }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testUShorts() {
        val values = UShortArray(256) { Random.nextInt().toUShort() }
        val serialized = makeByteArray { values.forEach(::writeUShort) }
        assertEquals(512, serialized.size)
        val deserialized = serialized.asInputStream { UShortArray(values.size) { readUShort() } }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testInts() {
        val values = IntArray(256) { Random.nextInt() }
        val serialized = makeByteArray { values.forEach(::writeInt) }
        assertEquals(1024, serialized.size)
        val deserialized = serialized.asInputStream { IntArray(values.size) { readInt() } }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testUInts() {
        val values = UIntArray(256) { Random.nextUInt() }
        val serialized = makeByteArray { values.forEach(::writeUInt) }
        assertEquals(1024, serialized.size)
        val deserialized = serialized.asInputStream { UIntArray(values.size) { readUInt() } }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testLongs() {
        val values = LongArray(256) { Random.nextLong() }
        val serialized = makeByteArray { values.forEach(::writeLong) }
        assertEquals(2048, serialized.size)
        val deserialized = serialized.asInputStream { LongArray(values.size) { readLong() } }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testULongs() {
        val values = ULongArray(256) { Random.nextULong() }
        val serialized = makeByteArray { values.forEach(::writeULong) }
        assertEquals(2048, serialized.size)
        val deserialized = serialized.asInputStream { ULongArray(values.size) { readULong() } }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testFloats() {
        val values = FloatArray(256) { Random.nextFloat() }
        val serialized = makeByteArray { values.forEach(::writeFloat) }
        assertEquals(1024, serialized.size)
        val deserialized = serialized.asInputStream { FloatArray(values.size) { readFloat() } }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testDoubles() {
        val values = DoubleArray(256) { Random.nextDouble() }
        val serialized = makeByteArray { values.forEach(::writeDouble) }
        assertEquals(2048, serialized.size)
        val deserialized = serialized.asInputStream { DoubleArray(values.size) { readDouble() } }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testStrings() {
        val values = listOf(
            "",
            "test",
            "Hello, World!",
            "This is a\ntest \"\u4321",
        )
        val serialized = makeByteArray { values.forEach(::writeString) }
        assertEquals(52, serialized.size)
        val deserialized = serialized.asInputStream { List(values.size) { readString() } }
        assertEquals(values, deserialized)
    }

    private enum class TestEnum { A, B, C, D }

    @Test
    fun testEnums() {
        val values = enumValues<TestEnum>().toList()
        val serialized = makeByteArray { values.forEach(::writeEnumByName) }
        assertEquals(20, serialized.size)
        val deserialized = serialized.asInputStream { List(values.size) { readEnumByName<TestEnum>() } }
        assertEquals(values, deserialized)

        val serializedBytes = makeByteArray { values.forEach(::writeEnumByOrdinalByte) }
        assertEquals(4, serializedBytes.size)
        val deserializedBytes = serializedBytes.asInputStream {
            List(values.size) { readEnumByOrdinalByte<TestEnum>() }
        }
        assertEquals(values, deserializedBytes)

        val serializedShorts = makeByteArray { values.forEach(::writeEnumByOrdinalShort) }
        assertEquals(8, serializedShorts.size)
        val deserializedShorts = serializedShorts.asInputStream {
            List(values.size) { readEnumByOrdinalShort<TestEnum>() }
        }
        assertEquals(values, deserializedShorts)

        val serializedInts = makeByteArray { values.forEach(::writeEnumByOrdinal) }
        assertEquals(16, serializedInts.size)
        val deserializedInts = serializedInts.asInputStream { List(values.size) { readEnumByOrdinal<TestEnum>() } }
        assertEquals(values, deserializedInts)

        val serializedAuto = makeByteArray { values.forEach(::writeEnumByOrdinalAuto) }
        assertEquals(4, serializedAuto.size)
        val deserializedAuto = serializedAuto.asInputStream { List(values.size) { readEnumByOrdinalAuto<TestEnum>() } }
        assertEquals(values, deserializedAuto)
    }

    // Wrappers

    @Test
    fun testNullable() {
        val five = makeByteArray { writeNullable(5, OutputStream::writeInt) }
        assertEquals(5, five.size)
        assertEquals(5, five.asInputStream { readNullable(InputStream::readInt) })

        val nil = makeByteArray { writeNullable(null, OutputStream::writeInt) }
        assertEquals(1, nil.size)
        assertEquals(null, nil.asInputStream { readNullable(InputStream::readInt) })
    }

    @Test
    fun testRepeat() {
        assertContentEquals(
            IntArray(20) { it },
            fullWriteAndRead(
                { repeatWrite(20, OutputStream::writeInt) },
                {
                    val ints = IntArray(20)
                    repeatRead { ints[it] = readInt() }
                    ints
                },
            ),
        )
    }

    @Test
    fun testLists() {
        val values = List(20) { it.toString(2) }
        val serialized = makeByteArray { writeValues(values, OutputStream::writeString) }
        assertEquals(154, serialized.size)
        assertEquals(values, serialized.asInputStream { readValues(InputStream::readString) })
        assertEquals(values, serialized.asInputStream {
            val x = mutableListOf<String>()
            readValuesTo(x, InputStream::readString)
            x
        })
        assertEquals(values, serialized.asInputStream {
            val x = mutableListOf<String>()
            readValues(InputStream::readString, x::add)
            x
        })
    }

    @Test
    fun testMaps() {
        val map = mapOf("one" to 1, "two" to 2, "three" to 3)
        val serialized = makeByteArray {
            writeMap(map, OutputStream::writeString, OutputStream::writeInt)
        }
        assertEquals(39, serialized.size)
        assertEquals(map, serialized.asInputStream {
            readMap({ readString() }, { readInt() })
        })
    }
}
