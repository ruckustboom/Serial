package serial

import kotlin.test.*

class TestCharCursor {
    @Test
    fun testRead() {
        try {
            "#ab1c...:vroom \n vroooom".parse {
                assertEquals('#', read())
                assertEquals('a', read())
                assertEquals('b', read())
                assertTrue(readIf { it.isDigit() })
                assertFalse(readIf { it.isDigit() })
                assertTrue(readOptionalChar('c'))
                assertFalse(readOptionalChar('c'))
                assertEquals(3, readWhile { it == '.' })
                readRequiredChar(':')
                readLiteral("VROOM", true)
                consumeWhitespace()
                readLiteral("Vroom!", true)
            }
            fail("Should have crashed")
        } catch (e: CharCursorException) {
            assertEquals(21, e.offset)
            assertEquals(1, e.line)
            assertEquals(5, e.column)
            assertEquals('o', e.character)
            assertEquals("Expected: m", e.description)
            assertEquals("Expected: m (found <o>/111 at 21 (1:5))", e.message)
        }
    }

    @Test
    fun testCapture() {
        "123456?#$&! 123---789".parse {
            assertEquals("123456", captureWhile { it.isDigit() })
            assertEquals("?#$&!", captureCount(5))
            consumeWhitespace()
            assertEquals(
                "123456789",
                capturing {
                    assertEquals(3, readWhile { it.isDigit() })
                    notCapturing {
                        assertEquals(3, readWhile { it == '-' })
                    }
                    capture("456")
                    assertEquals(3, readWhile { it.isDigit() })
                },
            )
        }
    }

    @Test
    fun testTokenize() {
        listOf("this", "is", "a", "test").toCursor()
            .tokenizeToChar { read().first() }
            .parse {
                assertEquals('t', read())
                assertEquals('i', read())
                assertEquals('a', read())
                assertEquals('t', read())
                assertTrue(isEndOfInput)
            }
    }
}
