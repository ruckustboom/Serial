package serial

import java.io.InputStream
import java.io.Reader

// Generic

public fun <T, R> Cursor<T>.tokenize(
    parseToken: Cursor<T>.() -> R,
): Cursor<R> = object : Iterator<R> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

public fun <T> TextCursor.tokenize(
    parseToken: TextCursor.() -> T,
): Cursor<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

public fun <T> BinaryCursor.tokenize(
    parseToken: BinaryCursor.() -> T,
): Cursor<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

// Text

public fun <T> Cursor<T>.textTokenize(
    parseToken: Cursor<T>.() -> Char,
): TextCursor = object : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            try {
                cbuf[off + i] = parseToken()
            } catch (e: Throwable) {
                break
            }
            i++
        }
        return i
    }

    override fun close() {}
}.initParse()

public fun TextCursor.textTokenize(
    parseToken: TextCursor.() -> Char,
): TextCursor = object : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            try {
                cbuf[off + i] = parseToken()
            } catch (e: Throwable) {
                break
            }
            i++
        }
        return i
    }

    override fun close() {}
}.initParse()

public fun BinaryCursor.textTokenize(
    parseToken: BinaryCursor.() -> Char,
): TextCursor = object : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            try {
                cbuf[off + i] = parseToken()
            } catch (e: Throwable) {
                break
            }
            i++
        }
        return i
    }

    override fun close() {}
}.initParse()

// Binary

public fun <T> Cursor<T>.binaryTokenize(
    parseToken: Cursor<T>.() -> Byte,
): BinaryCursor = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.initParse()

public fun TextCursor.binaryTokenize(
    parseToken: TextCursor.() -> Byte,
): BinaryCursor = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.initParse()

public fun BinaryCursor.binaryTokenize(
    parseToken: BinaryCursor.() -> Byte,
): BinaryCursor = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.initParse()
