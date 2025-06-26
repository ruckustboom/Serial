package serial

import kotlin.test.*

class TestObjectCursor {
    @Test
    fun testRead() {
        try {
            (0..100).parse {
                assertEquals(0, read())
                assertEquals(1, read())
                assertEquals(2, read())
                assertTrue(readIf { it < 10 })
                assertFalse(readIf { it > 10 })
                assertTrue(readOptionalValue(4))
                assertFalse(readOptionalValue(4))
                readRequiredValue(5)
                assertEquals(4, readWhile { it < 10 })
                readLiteral(listOf(10, 11, 12, 13))
                readLiteral(listOf(14, 15, 16, 15, 14))
            }
            fail("Should have crashed")
        } catch (e: ObjectCursorException) {
            assertEquals(17, e.offset)
            assertEquals(17, e.value)
            assertEquals("Expected: 15", e.description)
            assertEquals("Expected: 15 (found <17> at 17)", e.message)
        }
    }

    @Test
    fun testCapture() {
        (0..32).parse {
            assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), captureWhile { it < 10 })
            assertEquals(listOf(10, 11, 12, 13, 14), captureCount(5))
            assertEquals(
                listOf(15, 16, 17, 18, 19, -1, -2, -3, 30, 31, 32),
                capturing {
                    assertEquals(5, readWhile { it < 20 })
                    notCapturing {
                        assertEquals(10, readWhile { it < 30 })
                    }
                    capture(listOf(-1, -2, -3))
                    assertEquals(3, readWhile { it < 33 })
                },
            )
        }
    }

    @Test
    fun testTokenize() {
        "this is a test".toCursor()
            .tokenize { captureWhile { it.isLetterOrDigit() }.also { readWhileWhitespace() } }
            .parse {
                assertEquals("this", read())
                assertEquals("is", read())
                assertEquals("a", read())
                assertEquals("test", read())
                assertTrue(isEndOfInput)
            }
    }
}
