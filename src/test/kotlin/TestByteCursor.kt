package serial

import java.io.OutputStream
import kotlin.math.PI
import kotlin.test.*

class TestByteCursor {
    @Test
    fun testRead() {
        try {
            ByteArray(100) { it.toByte() }.parse {
                assertEquals(0, read())
                assertEquals(1, read())
                assertEquals(2, read())
                assertTrue(readIf { it < 10 })
                assertFalse(readIf { it > 10 })
                assertTrue(readOptionalByte(4))
                assertFalse(readOptionalByte(4))
                readRequiredByte(5)
                assertEquals(4, readWhile { it < 10 })
                readLiteral(byteArrayOf(10, 11, 12, 13))
                readLiteral(byteArrayOf(14, 15, 16, 15, 14))
            }
            fail("Should have crashed")
        } catch (e: ByteCursorException) {
            assertEquals(17, e.offset)
            assertEquals(17, e.byte)
            assertEquals("Expected: 15", e.description)
            assertEquals("Expected: 15 (found 17 at 17)", e.message)
        }
    }

    @Test
    fun testCapture() {
        ByteArray(33) { it.toByte() }.parse {
            assertContentEquals(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), captureWhile { it < 10 })
            assertContentEquals(byteArrayOf(10, 11, 12, 13, 14), captureCount(5))
            assertContentEquals(
                byteArrayOf(15, 16, 17, 18, 19, -1, -2, -3, 30, 31, 32),
                capturing {
                    assertEquals(5, readWhile { it < 20 })
                    notCapturing {
                        assertEquals(10, readWhile { it < 30 })
                    }
                    capture(byteArrayOf(-1, -2, -3))
                    assertEquals(3, readWhile { it < 33 })
                },
            )
        }
    }

    enum class Bool { TRUE, FALSE, FILE_NOT_FOUND }

    @Test
    fun testPrimitives() {
        makeInputStream {
            writeBoolean(true)
            writeByte(-19)
            writeUByte(17u)
            writeShort(-320)
            writeUShort(497u)
            writeInt(-42)
            writeUInt(42u)
            writeLong(Long.MIN_VALUE)
            writeULong(ULong.MAX_VALUE)
            writeFloat(Float.POSITIVE_INFINITY)
            writeDouble(PI)
            writeString("this is a test")
            writeEnumByName(Bool.TRUE)
            writeEnumByOrdinal(Bool.FALSE)
            writeEnumByOrdinalAuto(Bool.FILE_NOT_FOUND)
        }.parse {
            assertEquals(true, readBoolean())
            assertEquals(-19, read())
            assertEquals(17u, readUByte())
            assertEquals(-320, readShort())
            assertEquals(497u, readUShort())
            assertEquals(-42, readInt())
            assertEquals(42u, readUInt())
            assertEquals(Long.MIN_VALUE, readLong())
            assertEquals(ULong.MAX_VALUE, readULong())
            assertEquals(Float.POSITIVE_INFINITY, readFloat())
            assertEquals(PI, readDouble())
            assertEquals("this is a test", readString())
            assertEquals(Bool.TRUE, readEnumByName())
            assertEquals(Bool.FALSE, readEnumByOrdinal())
            assertEquals(Bool.FILE_NOT_FOUND, readEnumByOrdinalAuto())
            assertTrue(isEndOfInput)
        }
    }

    @Test
    fun testExtensions() {
        makeInputStream {
            writeNullable("Fred", OutputStream::writeString)
            writeNullable(null, OutputStream::writeString)
            writeValues(listOf("this", "is", "a", "test"), OutputStream::writeString)
            writeMap(
                mapOf("a" to "first letter", "fred" to "good test name"),
                OutputStream::writeString,
                OutputStream::writeString,
            )
        }.parse {
            assertEquals("Fred", readNullable(ByteCursor::readString))
            assertEquals(null, readNullable(ByteCursor::readString))
            assertEquals(listOf("this", "is", "a", "test"), readValues(ByteCursor::readString))
            assertEquals(
                mapOf("a" to "first letter", "fred" to "good test name"),
                readMap({ readString() }, { readString() }),
            )
            assertTrue(isEndOfInput)
        }
    }

    @Test
    fun testTokenize() {
        listOf("this", "is", "a", "test").toCursor()
            .tokenizeToByte { read().length.toByte() }
            .parse {
                assertEquals(4, read())
                assertEquals(2, read())
                assertEquals(1, read())
                assertEquals(4, read())
                assertTrue(isEndOfInput)
            }
    }
}
