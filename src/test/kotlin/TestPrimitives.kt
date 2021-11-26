package serial

import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestPrimitives {
    @Test
    fun testBytes() {
        val values = ByteArray(256, Int::toByte)
        val serialized = makeByteArray(false) {
            for (value in values) writeByte(value)
        }
        assertEquals(256, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            ByteArray(values.size) { readByte() }
        }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testUBytes() {
        val values = UByteArray(256, Int::toUByte)
        val serialized = makeByteArray(false) {
            for (value in values) writeUByte(value)
        }
        assertEquals(256, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            UByteArray(values.size) { readUByte() }
        }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testShorts() {
        val values = ShortArray(256) { Random.nextInt().toShort() }
        val serialized = makeByteArray(false) {
            for (value in values) writeShort(value)
        }
        assertEquals(512, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            ShortArray(values.size) { readShort() }
        }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testUShorts() {
        val values = UShortArray(256) { Random.nextInt().toUShort() }
        val serialized = makeByteArray(false) {
            for (value in values) writeUShort(value)
        }
        assertEquals(512, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            UShortArray(values.size) { readUShort() }
        }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testInts() {
        val values = IntArray(256) { Random.nextInt() }
        val serialized = makeByteArray(false) {
            for (value in values) writeInt(value)
        }
        assertEquals(1024, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            IntArray(values.size) { readInt() }
        }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testUInts() {
        val values = UIntArray(256) { Random.nextUInt() }
        val serialized = makeByteArray(false) {
            for (value in values) writeUInt(value)
        }
        assertEquals(1024, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            UIntArray(values.size) { readUInt() }
        }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testLongs() {
        val values = LongArray(256) { Random.nextLong() }
        val serialized = makeByteArray(false) {
            for (value in values) writeLong(value)
        }
        assertEquals(2048, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            LongArray(values.size) { readLong() }
        }
        assertContentEquals(values, deserialized)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun testULongs() {
        val values = ULongArray(256) { Random.nextULong() }
        val serialized = makeByteArray(false) {
            for (value in values) writeULong(value)
        }
        assertEquals(2048, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            ULongArray(values.size) { readULong() }
        }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testFloats() {
        val values = FloatArray(256) { Random.nextFloat() }
        val serialized = makeByteArray(false) {
            for (value in values) writeFloat(value)
        }
        assertEquals(1024, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            FloatArray(values.size) { readFloat() }
        }
        assertContentEquals(values, deserialized)
    }

    @Test
    fun testDoubles() {
        val values = DoubleArray(256) { Random.nextDouble() }
        val serialized = makeByteArray(false) {
            for (value in values) writeDouble(value)
        }
        assertEquals(2048, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            DoubleArray(values.size) { readDouble() }
        }
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
        val serialized = makeByteArray(false) {
            for (value in values) writeString(value)
        }
        assertEquals(52, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            List(values.size) { readString() }
        }
        assertEquals(values, deserialized)
    }

    @Test
    fun testEnums() {
        val values = enumValues<TestEnum>().toList()
        val serialized = makeByteArray(false) {
            for (value in values) writeEnum(value)
        }
        assertEquals(4, serialized.size)
        val deserialized = serialized.asInputStream(false) {
            List(values.size) { readEnum<TestEnum>() }
        }
        assertEquals(values, deserialized)

        val serializedBytes = makeByteArray(false) {
            for (value in values) writeEnumAsByte(value)
        }
        assertEquals(4, serializedBytes.size)
        val deserializedBytes = serializedBytes.asInputStream(false) {
            List(values.size) { readEnumAsByte<TestEnum>() }
        }
        assertEquals(values, deserializedBytes)

        val serializedShorts = makeByteArray(false) {
            for (value in values) writeEnumAsShort(value)
        }
        assertEquals(8, serializedShorts.size)
        val deserializedShorts = serializedShorts.asInputStream(false) {
            List(values.size) { readEnumAsShort<TestEnum>() }
        }
        assertEquals(values, deserializedShorts)

        val serializedInts = makeByteArray(false) {
            for (value in values) writeEnumAsInt(value)
        }
        assertEquals(16, serializedInts.size)
        val deserializedInts = serializedInts.asInputStream(false) {
            List(values.size) { readEnumAsInt<TestEnum>() }
        }
        assertEquals(values, deserializedInts)
    }

    private enum class TestEnum { A, B, C, D }
}
