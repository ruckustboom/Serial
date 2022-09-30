package serial

import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestWrappers {
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
