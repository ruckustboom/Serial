package serial

import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

class TestWrappers {
    @Test
    fun testReadOrThrow() {
        val bytes = makeByteArray(false) {
            writeByte(0)
            writeByte(1)
            writeByte(-1)
            writeByte(127)
            writeByte(-128)
        }
        bytes.asInputStream(false).use {
            assertEquals(0, it.readOrThrow())
            assertEquals(1, it.readOrThrow())
            assertEquals(255, it.readOrThrow())
            assertEquals(127, it.readOrThrow())
            assertEquals(128, it.readOrThrow())
            try {
                val x = it.readOrThrow()
                fail("Expected EOI, found $x")
            } catch (e: IllegalStateException) {
                assertEquals("Reached end of stream", e.message)
            } catch (e: Throwable) {
                fail("Expected IllegalStateException, threw $e")
            }
        }
    }

    @Test
    fun testNullable() {
        val five = makeByteArray(false) {
            writeNullable(5, OutputStream::writeInt)
        }
        assertEquals(5, five.size)
        assertEquals(5, five.asInputStream(false) { readNullable(InputStream::readInt) })

        val nil = makeByteArray(false) {
            writeNullable(null, OutputStream::writeInt)
        }
        assertEquals(1, nil.size)
        assertEquals(null, nil.asInputStream(false) { readNullable(InputStream::readInt) })
    }

    @Test
    fun testRepeat() {
        assertContentEquals(
            IntArray(20) { it },
            fullWriteAndRead(
                false,
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
        val serialized = makeByteArray(false) { writeValues(values, OutputStream::writeString) }
        assertEquals(154, serialized.size)
        assertEquals(values, serialized.asInputStream(false) { readValues(InputStream::readString) })
        assertEquals(values, serialized.asInputStream(false) {
            val x = mutableListOf<String>()
            readValuesTo(x, InputStream::readString)
            x
        })
        assertEquals(values, serialized.asInputStream(false) {
            val x = mutableListOf<String>()
            readValues(InputStream::readString, x::add)
            x
        })
    }

    @Test
    fun testMaps() {
        val map = mapOf("one" to 1, "two" to 2, "three" to 3)
        val serialized = makeByteArray(false) {
            writeMap(map, OutputStream::writeString, OutputStream::writeInt)
        }
        assertEquals(39, serialized.size)
        assertEquals(map, serialized.asInputStream(false) {
            readMap({ readString() }, { readInt() })
        })
    }
}
