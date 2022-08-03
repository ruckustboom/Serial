package serial

import java.io.InputStream
import java.io.Reader

// Generic

public fun <T, R> ObjectCursor<T>.tokenize(
    parseToken: ObjectCursor<T>.() -> R,
): ObjectCursor<R> = object : Iterator<R> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.toCursor()

public fun <T> CharCursor.tokenize(
    parseToken: CharCursor.() -> T,
): ObjectCursor<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.toCursor()

public fun <T> ByteCursor.tokenize(
    parseToken: ByteCursor.() -> T,
): ObjectCursor<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.toCursor()

// Text

public fun <T> ObjectCursor<T>.textTokenize(
    parseToken: ObjectCursor<T>.() -> Char,
): CharCursor = object : Reader() {
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
}.toCursor()

public fun CharCursor.textTokenize(
    parseToken: CharCursor.() -> Char,
): CharCursor = object : Reader() {
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
}.toCursor()

public fun ByteCursor.textTokenize(
    parseToken: ByteCursor.() -> Char,
): CharCursor = object : Reader() {
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
}.toCursor()

// Binary

public fun <T> ObjectCursor<T>.binaryTokenize(
    parseToken: ObjectCursor<T>.() -> Byte,
): ByteCursor = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.toCursor()

public fun CharCursor.binaryTokenize(
    parseToken: CharCursor.() -> Byte,
): ByteCursor = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.toCursor()

public fun ByteCursor.binaryTokenize(
    parseToken: ByteCursor.() -> Byte,
): ByteCursor = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.toCursor()
