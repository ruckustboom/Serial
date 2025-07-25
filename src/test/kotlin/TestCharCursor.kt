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
                readWhileWhitespace()
                readLiteral("Vroom!", true)
            }
            fail("Should have crashed")
        } catch (e: CharCursorException) {
            assertEquals(21, e.offset)
            assertEquals(1, e.line)
            assertEquals(5, e.column)
            assertEquals('o', e.character)
            assertEquals("Expected: m", e.message)
        }
    }

    @Test
    fun testCapture() {
        "123456?#$&! 123---789".parse {
            assertEquals("123456", captureWhile { it.isDigit() })
            assertEquals("?#$&!", captureCount(5))
            readWhileWhitespace()
            assertEquals(
                "123456789",
                capturing {
                    assertEquals(3, readWhile { it.isDigit() })
                    assertEquals(3, notCapturing { readWhile { it == '-' } })
                    capture("456")
                    assertEquals(3, readWhile { it.isDigit() })
                },
            )
        }
        "'this is a string'".parse {
            assertEquals("this is a string", captureStringLiteral('\''))
        }
        "[this is a#nstring]".parse {
            assertEquals("[this is a\nstring]", captureStringLiteral('[', ']', true, '#'))
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

    @Test
    fun testErrors() {
        assertEquals(
            "Ambiguous termination vs escape",
            assertFailsWith<CharCursorException> {
                "fred".parse { captureStringLiteral(open = '?', escape = '?') }
            }.message
        )
    }

    @Test
    fun testWhitespace() {
        assertEquals(
            "starfish",
            "star \n fish".parse {
                capturing {
                    readWhile { it.isLetter() }
                    notCapturing {
                        readWhileWhitespace()
                    }
                    readWhile { it.isLetter() }
                }
            }
        )
        assertEquals(
            "starfish",
            "star \n fish".parse {
                capturing {
                    readWhile { it.isLetter() }
                    notCapturing {
                        readWhileWhitespace(false)
                        readRequiredChar('\n')
                        readWhileWhitespace(false)
                    }
                    readWhile { it.isLetter() }
                }
            }
        )
    }

    @Test
    fun testEoi() {
        "".parse { eoi() }  // Should run without failing
        assertEquals(
            "Expected end of input",
            assertFailsWith<CharCursorException> { "?".parse { eoi() } }.message,
        )
    }
}
