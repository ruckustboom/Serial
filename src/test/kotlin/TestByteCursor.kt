package serial

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
